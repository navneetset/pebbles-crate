package tech.sethi.pebbles.crates.particles

import net.minecraft.particle.ParticleEffect
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.config.ParticleStyle
import tech.sethi.pebbles.crates.config.ResolvedStyle
import tech.sethi.pebbles.crates.config.StyleResolver
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.util.WorldBlockPos
import kotlin.math.cos
import kotlin.math.sin

object CrateParticles {
    private var stepX = 1

    private const val particles = 2
    private const val crossParticlesPerRotation = 20
    private const val spiralParticlesPerRotation = 16
    private const val radius = 1

    /** How far a scattered particle lands from the middle of the crate, on each axis. */
    private const val SCATTER = 0.75

    /** How many ticks one turn of the helix takes, and how far its strands climb over it. */
    private const val HELIX_TICKS_PER_ROTATION = 18
    private const val HELIX_CLIMB_TICKS = 40
    private const val HELIX_HEIGHT = 2.0

    /** The ring: three points, a fixed height above the crate, one slow turn. */
    private const val ORBIT_POINTS = 3
    private const val ORBIT_TICKS_PER_ROTATION = 60
    private const val ORBIT_HEIGHT = 1.2

    /** The column: two flames a half-cycle apart, climbing out of the block's top face. */
    private const val COLUMN_STRANDS = 2
    private const val COLUMN_CLIMB_TICKS = 48
    private const val COLUMN_HEIGHT = 2.5
    private const val COLUMN_JITTER = 0.15

    /** Glyphs start out on this ring and this far up, and converge on the crate as they fade. */
    private const val ENCHANT_GLYPHS = 3
    private const val ENCHANT_RADIUS = 1.4
    private const val ENCHANT_RISE = 1.2
    private const val ENCHANT_HEIGHT = 1.0

    /** Rain falls from here, over a square this wide to a side. */
    private const val RAIN_DROPS = 2
    private const val RAIN_HEIGHT = 2.5
    private const val RAIN_SPREAD = 1.0

    /** A preview is thrown at the admin, not at a crate, so it is capped well under the real burst. */
    private const val PREVIEW_MAX_COUNT = 50

    /** What one crate draws this tick: resolved once per crate, then sent to everyone near it. */
    data class IdleParticles(val style: ParticleStyle, val effect: ParticleEffect)

    fun updateTimers() {
        stepX++
    }

    /**
     * The idle particles for a crate on this tick, or null when it draws none - because its style is
     * "none", because the pattern only emits every so many ticks, or because the particle it names
     * is not registered. The blacklist is a separate, harder switch and is checked before this.
     */
    fun idleParticlesFor(worldPos: WorldBlockPos): IdleParticles? {
        val placement = CrateDataManager.getPlacement(worldPos) ?: return null
        val crateStyle = CrateConfigManager.getCrateConfig(placement.name)?.style

        val style = StyleResolver.particleStyle(placement.style, crateStyle)
        if (style == ParticleStyle.NONE) return null
        if (stepX % style.periodTicks != 0) return null

        val id = StyleResolver.particleId(style, placement.style, crateStyle) ?: return null
        val effect = GlobalConfigManager.particleEffect(id) ?: return null
        return IdleParticles(style, effect)
    }

    fun spawnIdleParticles(player: ServerPlayerEntity, pos: BlockPos, world: ServerWorld, idle: IdleParticles) {
        when (idle.style) {
            ParticleStyle.CROSS_SPIRAL -> spawnSpiral(player, pos, idle.effect, crossParticlesPerRotation)
            ParticleStyle.SPIRAL -> spawnSpiral(player, pos, idle.effect, spiralParticlesPerRotation)
            ParticleStyle.SPARKLE -> spawnScatter(player, pos, world, idle.effect, 3, 0.75)
            ParticleStyle.HEART -> spawnScatter(player, pos, world, idle.effect, 2, 0.35)
            ParticleStyle.DOUBLE_HELIX -> spawnDoubleHelix(player, pos, idle.effect)
            ParticleStyle.ORBIT -> spawnOrbit(player, pos, idle.effect)
            ParticleStyle.COLUMN -> spawnColumn(player, pos, world, idle.effect)
            ParticleStyle.ENCHANT -> spawnEnchant(player, pos, world, idle.effect)
            ParticleStyle.RAIN -> spawnRain(player, pos, world, idle.effect)
            ParticleStyle.NONE -> {}
        }
    }

    /**
     * Two strands winding up out of the crate. The wider rotation is the pattern the mod has always
     * drawn; the tighter one is the plain spiral.
     */
    private fun spawnSpiral(
        player: ServerPlayerEntity, pos: BlockPos, effect: ParticleEffect, particlesPerRotation: Int
    ) {
        for (stepY in 0 until 60 step (120 / particles)) {
            val dx = -(cos(((stepX + stepY) / particlesPerRotation.toDouble()) * Math.PI * 2)) * radius
            val dy = stepY / particlesPerRotation.toDouble() / 2.0
            val dz = -(sin(((stepX + stepY) / particlesPerRotation.toDouble()) * Math.PI * 2)) * radius

            send(player, effect, pos.x + 0.5 + dx, pos.y + 1.5 + dy, pos.z + 0.5 + dz)
        }
    }

    /**
     * Two strands half a turn apart, each climbing and starting over. Where the spirals draw their
     * whole length every tick, this draws one point per strand and lets the particles' own lifetime
     * leave the trail behind - which is what keeps a pattern this tall down to two particles a tick.
     */
    private fun spawnDoubleHelix(player: ServerPlayerEntity, pos: BlockPos, effect: ParticleEffect) {
        val climb = (stepX % HELIX_CLIMB_TICKS) / HELIX_CLIMB_TICKS.toDouble() * HELIX_HEIGHT

        for (strand in 0 until 2) {
            val angle = (stepX / HELIX_TICKS_PER_ROTATION.toDouble()) * Math.PI * 2 + strand * Math.PI
            send(
                player,
                effect,
                pos.x + 0.5 + cos(angle) * radius,
                pos.y + 1.0 + climb,
                pos.z + 0.5 + sin(angle) * radius
            )
        }
    }

    /** A flat ring of evenly spaced points, turning at a fixed height above the crate. */
    private fun spawnOrbit(player: ServerPlayerEntity, pos: BlockPos, effect: ParticleEffect) {
        val turn = (stepX / ORBIT_TICKS_PER_ROTATION.toDouble()) * Math.PI * 2

        for (point in 0 until ORBIT_POINTS) {
            val angle = turn + point * Math.PI * 2 / ORBIT_POINTS
            send(
                player,
                effect,
                pos.x + 0.5 + cos(angle) * radius,
                pos.y + ORBIT_HEIGHT,
                pos.z + 0.5 + sin(angle) * radius
            )
        }
    }

    /** Points rising out of the block's top face, spaced along the climb so the column reads as one. */
    private fun spawnColumn(player: ServerPlayerEntity, pos: BlockPos, world: ServerWorld, effect: ParticleEffect) {
        val random = world.random

        for (strand in 0 until COLUMN_STRANDS) {
            val offset = strand * COLUMN_CLIMB_TICKS / COLUMN_STRANDS
            val climb = ((stepX + offset) % COLUMN_CLIMB_TICKS) / COLUMN_CLIMB_TICKS.toDouble() * COLUMN_HEIGHT

            send(
                player,
                effect,
                pos.x + 0.5 + (random.nextDouble() - 0.5) * 2.0 * COLUMN_JITTER,
                pos.y + 1.0 + climb,
                pos.z + 0.5 + (random.nextDouble() - 0.5) * 2.0 * COLUMN_JITTER
            )
        }
    }

    /**
     * Enchanting-table glyphs: they are drawn *backwards*, from where they end up to where they
     * start, so this sends the crate as the position and the way out to the ring as the velocity -
     * the one place a count of zero is wanted, since that is how a packet carries a velocity at all.
     */
    private fun spawnEnchant(player: ServerPlayerEntity, pos: BlockPos, world: ServerWorld, effect: ParticleEffect) {
        val random = world.random

        for (glyph in 0 until ENCHANT_GLYPHS) {
            val angle = random.nextDouble() * Math.PI * 2
            val packet = ParticleS2CPacket(
                effect,
                false,
                pos.x + 0.5,
                pos.y + ENCHANT_HEIGHT,
                pos.z + 0.5,
                (cos(angle) * ENCHANT_RADIUS).toFloat(),
                (random.nextDouble() * ENCHANT_RISE).toFloat(),
                (sin(angle) * ENCHANT_RADIUS).toFloat(),
                1.0f,
                0
            )
            player.networkHandler.sendPacket(packet)
        }
    }

    /** Drops let go above the crate; the particle's own gravity is what makes it rain. */
    private fun spawnRain(player: ServerPlayerEntity, pos: BlockPos, world: ServerWorld, effect: ParticleEffect) {
        val random = world.random

        for (drop in 0 until RAIN_DROPS) {
            send(
                player,
                effect,
                pos.x + 0.5 + (random.nextDouble() - 0.5) * 2.0 * RAIN_SPREAD,
                pos.y + RAIN_HEIGHT,
                pos.z + 0.5 + (random.nextDouble() - 0.5) * 2.0 * RAIN_SPREAD
            )
        }
    }

    /** A handful of particles dropped at random around the crate, at the height the style wants. */
    private fun spawnScatter(
        player: ServerPlayerEntity,
        pos: BlockPos,
        world: ServerWorld,
        effect: ParticleEffect,
        count: Int,
        height: Double
    ) {
        val random = world.random

        for (i in 0 until count) {
            send(
                player,
                effect,
                pos.x + 0.5 + (random.nextDouble() - 0.5) * 2.0 * SCATTER,
                pos.y + height + (random.nextDouble() - 0.5) * 2.0 * SCATTER,
                pos.z + 0.5 + (random.nextDouble() - 0.5) * 2.0 * SCATTER
            )
        }
    }

    /** One particle, exactly where it is asked for and standing still. */
    private fun send(player: ServerPlayerEntity, effect: ParticleEffect, x: Double, y: Double, z: Double) {
        player.networkHandler.sendPacket(
            ParticleS2CPacket(effect, false, x, y, z, 0.0f, 0.0f, 0.0f, 0.0f, 1)
        )
    }

    /** The burst that marks a won prize: the crate's reward sounds for everyone nearby, particles for the opener. */
    fun rewardParticles(player: ServerPlayerEntity, pos: BlockPos, style: ResolvedStyle) {
        val world = player.world

        val sounds = style.rewardSounds.mapNotNull { sound ->
            GlobalConfigManager.soundEvent(sound.id)?.let { it to sound }
        }

        repeat(GlobalConfigManager.animation.rewardSoundRepeats) {
            for ((event, sound) in sounds) {
                world.playSound(
                    null as ServerPlayerEntity?, pos, event, SoundCategory.BLOCKS, sound.volume, sound.pitch
                )
            }
        }

        burst(player, pos.x + 0.5, pos.y + 0.7, pos.z + 0.5, style.rewardParticleId, style.rewardParticleCount)
    }

    /**
     * The same burst thrown at the admin's own feet, so a crate's reward particle can be judged from
     * the settings screen. Capped, because it is going off at arm's length rather than across a plaza.
     */
    fun previewReward(admin: ServerPlayerEntity, style: ResolvedStyle) {
        burst(
            admin,
            admin.x,
            admin.y + 1.0,
            admin.z,
            style.rewardParticleId,
            style.rewardParticleCount.coerceAtMost(PREVIEW_MAX_COUNT)
        )
    }

    /** A cloud of [count] particles around one point, sent to the one player who should see it. */
    private fun burst(player: ServerPlayerEntity, x: Double, y: Double, z: Double, id: String, count: Int) {
        if (count <= 0) return
        val effect = GlobalConfigManager.particleEffect(id) ?: return

        player.networkHandler.sendPacket(
            ParticleS2CPacket(effect, false, x, y, z, 0.0f, 0.0f, 0.0f, 0.1f, count)
        )
    }
}
