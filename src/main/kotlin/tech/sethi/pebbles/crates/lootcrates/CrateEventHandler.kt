package tech.sethi.pebbles.crates.lootcrates

import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.config.ResolvedStyle
import tech.sethi.pebbles.crates.entity.RollItemDisplayEntity
import tech.sethi.pebbles.crates.particles.CrateParticles
import tech.sethi.pebbles.crates.util.NbtItemUtil
import tech.sethi.pebbles.crates.util.ParseableMessage
import tech.sethi.pebbles.crates.util.ParseableName
import tech.sethi.pebbles.crates.util.TickHandler
import tech.sethi.pebbles.crates.util.WorldBlockPos
import java.util.*


class CrateEventHandler(
    private val world: World,
    private val worldBlockPos: WorldBlockPos,
    private val player: ServerPlayerEntity,
    private val prizes: List<Prize>,
    private val cratesInUse: MutableMap<WorldBlockPos, Long>,
    private val playerCooldowns: MutableMap<UUID, Long>,
    private val crateName: String,
    /** Sounds, timing and scale for this crate at this block, already inherited down from config.json. */
    private val style: ResolvedStyle
) {
    // Extract the BlockPos for convenience
    private val pos: BlockPos = worldBlockPos.pos

    companion object {
        private val logger = LoggerFactory.getLogger("pebbles-crates")

        /** Slack on the display entity's own backstop, so it never expires before its roll ends. */
        private const val LIFETIME_MARGIN_TICKS = 100L
    }

    private val random = Random()

    /**
     * Returns null when there is nothing to roll: an empty prize list, or one where every chance is
     * zero. Both used to reach `random.nextInt(0)` and throw out of the interaction handler.
     */
    fun weightedRandomSelection(prizes: List<Prize>): Prize? {
        if (prizes.isEmpty()) return null

        val totalWeight = prizes.sumOf { it.chance }
        if (totalWeight <= 0) return null

        val randomValue = random.nextInt(totalWeight)
        var cumulativeWeight = 0

        for (prize in prizes) {
            cumulativeWeight += prize.chance
            if (randomValue < cumulativeWeight) {
                return prize
            }
        }

        // Only reachable if a config mixes negative chances into a positive total.
        return prizes.last()
    }

    /**
     * By the time this runs the key has already been taken, so every path out of it has to leave the
     * player with their prize - a return that skips [awardPrize] is a key spent for nothing. The
     * cooldown is not re-checked here for the same reason: [canOpenCrate] owns that decision, and it
     * is asked before anything is charged.
     */
    fun showPrizesAnimation(finalPrize: Prize) {
        if (world !is ServerWorld) {
            awardPrize(finalPrize)
            return
        }

        val currentTime = System.currentTimeMillis()
        val animation = GlobalConfigManager.animation

        // Long enough for this crate's own animation, however far it was tuned past config.json's.
        val lifetimeTicks = maxOf(animation.maxLifetimeTicks.toLong(), style.rollTicks + LIFETIME_MARGIN_TICKS)
        val display = RollItemDisplayEntity(world, player, pos, style.finalScale, lifetimeTicks.toInt())
        if (!display.spawn()) {
            logger.warn("[Pebbles-Crates] Could not spawn the roll display for crate '$crateName' at $pos")
            awardPrize(finalPrize)
            return
        }

        cratesInUse[worldBlockPos] = currentTime + GlobalConfigManager.maxAnimationMillis(style)

        val shuffleSound = GlobalConfigManager.soundEvent(style.shuffleSound.id)

        for (i in 0 until style.steps) {
            val rollPrize = weightedRandomSelection(prizes) ?: continue
            TickHandler.schedule(style.ticksPerStep * i) {
                display.showPrize(prizeStack(rollPrize))
                if (shuffleSound != null) {
                    world.playSound(
                        null,
                        pos,
                        shuffleSound,
                        SoundCategory.BLOCKS,
                        style.shuffleSound.volume,
                        style.shuffleSound.pitch
                    )
                }
            }
        }

        // Delay the final prize reveal so that the last random prize is shown for a while
        val finalPrizeDelay = style.ticksPerStep * (style.steps + 1)

        TickHandler.schedule(finalPrizeDelay) {
            display.showFinalPrize(prizeStack(finalPrize))
            awardPrize(finalPrize)
        }

        TickHandler.schedule(finalPrizeDelay + animation.holdTicks) {
            display.discard()
            cratesInUse.remove(worldBlockPos)
        }
    }

    /** Hands out the prize: particles, the configured messages, and the reward commands. */
    private fun awardPrize(prize: Prize) {
        CrateParticles.rewardParticles(player, pos, style)

        if (!prize.messageToOpener.isNullOrEmpty()) {
            val message = prize.messageToOpener.replace("{prize_name}", prize.name)
            ParseableMessage(message, player, prize.name).send()
        }

        if (!prize.broadcast.isNullOrEmpty()) {
            var broadcast = prize.broadcast.replace("{prize_name}", prize.name)
            broadcast = broadcast.replace("{player_name}", player.name.string)
            broadcast = broadcast.replace("{crate_name}", crateName)
            ParseableMessage(broadcast, player, prize.name).sendToAll()
        }

        runPrizeCommands(prize)
    }

    /**
     * Prize commands used to run on the bare console source, which sits at world spawn - so
     * relative coordinates and `@p` in a reward command resolved there instead of at the player who
     * opened the crate. The source below keeps console permission but stands where the player does.
     */
    private fun runPrizeCommands(prize: Prize) {
        val server = player.server
        val source = server.commandSource.withWorld(player.serverWorld).withPosition(player.pos)
            .withRotation(player.rotationClient).withEntity(player)

        for (command in prize.commands) {
            val cmd = command.replace("{player_name}", player.name.string)
            try {
                server.commandManager.dispatcher.execute(server.commandManager.dispatcher.parse(cmd, source))
            } catch (e: Exception) {
                logger.warn("[Pebbles-Crates] Reward command '$cmd' of crate '$crateName' failed: ${e.message}")
                Messages.send(player, "crate.command-failed", "command" to command)
            }
        }
    }

    private fun prizeStack(prize: Prize): ItemStack {
        val parsedPrize = Registries.ITEM.get(Identifier.tryParse(prize.material))
        val itemStack = NbtItemUtil.applyNbt(
            ItemStack(parsedPrize), prize.nbt, prize.amount, "prize '${prize.name}' in crate '$crateName'"
        )

        // A prize whose material the game does not have; naming it would only decorate an empty stack.
        if (itemStack.isEmpty) return itemStack

        itemStack.set(DataComponentTypes.CUSTOM_NAME, ParseableName(prize.name).returnMessageAsStyledText())
        return itemStack
    }

    fun canOpenCrate(): Boolean {
        val currentTime = System.currentTimeMillis()
        val cooldown = GlobalConfigManager.crate.cooldownMillis
        val lastCrateOpenTime = playerCooldowns[player.uuid] ?: 0L

        if (currentTime - lastCrateOpenTime < cooldown) {
            val remainingCooldown = (cooldown - (currentTime - lastCrateOpenTime)) / 1000
            Messages.send(player, "crate.cooldown", "seconds" to remainingCooldown.toString())
            return false
        }
        return true
    }

    fun updatePlayerCooldown() {
        val currentTime = System.currentTimeMillis()
        playerCooldowns[player.uuid] = currentTime
    }
}
