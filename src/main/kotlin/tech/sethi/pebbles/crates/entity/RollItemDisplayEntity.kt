package tech.sethi.pebbles.crates.entity

import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.entity.EntityType
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ChunkTicketType
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.AffineTransformation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import org.joml.Vector3f
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.impl.ItemDisplayEntityImpl

/**
 * The item floating above a crate while it rolls.
 *
 * A display entity rather than an [net.minecraft.entity.ItemEntity]: the old floating item was a
 * real item stack, so a hopper under the crate could suck the prize out of the world and duplicate
 * it. Nothing can pick this up.
 *
 * The entity itself only faces the opener and keeps its chunk loaded; which item is shown, and
 * when, is driven from outside by [tech.sethi.pebbles.crates.util.TickHandler].
 */
class RollItemDisplayEntity(
    world: ServerWorld,
    private val viewer: ServerPlayerEntity,
    cratePos: BlockPos,
) : ItemDisplayEntity(EntityType.ITEM_DISPLAY, world) {

    /**
     * Radius 2 puts the entity's own chunk at the entity-ticking level, which is the minimum for
     * [tick] to keep running. The ticket is released again in [remove] - the mod this animation
     * came from re-added it every tick and never let go, permanently force-loading every chunk a
     * crate was ever opened in.
     */
    private val ticketPos = ChunkPos(cratePos)
    private var ticketHeld = false

    private var ticksAlive = 0

    private val display: ItemDisplayEntityImpl
        get() = this as ItemDisplayEntityImpl

    init {
        noClip = true
        isInvulnerable = true
        setPosition(cratePos.x + 0.5, cratePos.y + 1.25, cratePos.z + 0.5)
        faceViewer()
    }

    /** Spawns the entity and takes the chunk ticket. Returns false if the world refused the spawn. */
    fun spawn(): Boolean {
        val serverWorld = world as? ServerWorld ?: return false
        if (!serverWorld.spawnEntity(this)) return false

        display.`pebbles_crates$publicSetTransformationMode`(ModelTransformationMode.HEAD)
        holdTicket(serverWorld, ticketPos)
        ticketHeld = true
        return true
    }

    fun showPrize(stack: ItemStack) {
        display.`pebbles_crates$publicSetStack`(stack)
    }

    /** The prize the player actually won, shown at the configured scale rather than the rolling one. */
    fun showFinalPrize(stack: ItemStack) {
        val scale = GlobalConfigManager.animation.finalScale
        display.`pebbles_crates$publicSetStack`(stack)
        display.`pebbles_crates$publicSetTransformation`(
            AffineTransformation(null, null, Vector3f(scale, scale, scale), null)
        )
    }

    override fun tick() {
        super.tick()

        ticksAlive++
        // Nothing should keep this alive that long; if the task that discards it never ran, the
        // entity still cleans itself up instead of hovering over the crate forever.
        if (ticksAlive > GlobalConfigManager.animation.maxLifetimeTicks) {
            discard()
            return
        }

        faceViewer()
    }

    private fun faceViewer() {
        if (viewer.isRemoved) return
        yaw = viewer.yaw + 180.0f
        headYaw = viewer.headYaw + 180.0f
        pitch = 0.0f
    }

    /** Covers discard(), death and chunk unload alike, so the ticket can never outlive the entity. */
    override fun remove(reason: RemovalReason) {
        releaseTicket()
        super.remove(reason)
    }

    private fun releaseTicket() {
        if (!ticketHeld) return
        ticketHeld = false
        (world as? ServerWorld)?.let { dropTicket(it, ticketPos) }
    }

    override fun shouldSave() = false

    companion object {
        private const val TICKET_RADIUS = 2

        /**
         * A ticket is identified by its chunk, so two crates rolling in the same one ask for the
         * same ticket - and the first to finish would otherwise pull it out from under the second.
         * Counted instead, and only released by the last roller. Entities live on the game thread,
         * so this needs no locking.
         */
        private val ticketHolders = HashMap<Pair<RegistryKey<World>, ChunkPos>, Int>()

        private fun holdTicket(world: ServerWorld, chunk: ChunkPos) {
            val key = world.registryKey to chunk
            val held = (ticketHolders[key] ?: 0) + 1
            ticketHolders[key] = held
            if (held == 1) world.chunkManager.addTicket(ChunkTicketType.FORCED, chunk, TICKET_RADIUS, chunk)
        }

        private fun dropTicket(world: ServerWorld, chunk: ChunkPos) {
            val key = world.registryKey to chunk
            val held = ticketHolders[key] ?: return
            if (held > 1) {
                ticketHolders[key] = held - 1
                return
            }

            ticketHolders.remove(key)
            world.chunkManager.removeTicket(ChunkTicketType.FORCED, chunk, TICKET_RADIUS, chunk)
        }
    }
}
