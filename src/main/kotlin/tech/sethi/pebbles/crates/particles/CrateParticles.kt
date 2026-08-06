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

            val x = pos.x + 0.5 + dx
            val y = pos.y + 1.5 + dy
            val z = pos.z + 0.5 + dz

            val particlePacket = ParticleS2CPacket(
                effect, false, x, y, z, 0.0f, 0.0f, 0.0f, 0.0f, 1
            )
            player.networkHandler.sendPacket(particlePacket)
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
            val x = pos.x + 0.5 + (random.nextDouble() - 0.5) * 2.0 * SCATTER
            val y = pos.y + height + (random.nextDouble() - 0.5) * 2.0 * SCATTER
            val z = pos.z + 0.5 + (random.nextDouble() - 0.5) * 2.0 * SCATTER

            val particlePacket = ParticleS2CPacket(
                effect, false, x, y, z, 0.0f, 0.0f, 0.0f, 0.0f, 1
            )
            player.networkHandler.sendPacket(particlePacket)
        }
    }

    /** The burst that marks a won prize: the crate's reward sounds for everyone nearby, particles for the opener. */
    fun rewardParticles(player: ServerPlayerEntity, pos: BlockPos, style: ResolvedStyle) {
        val world = player.world
        val settings = GlobalConfigManager.particles

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

        val effect = GlobalConfigManager.particleEffect(settings.reward) ?: return
        if (settings.rewardCount <= 0) return

        val particlePacket = ParticleS2CPacket(
            effect,
            false,
            pos.x + 0.5,
            pos.y + 0.7,
            pos.z + 0.5,
            0.0f,
            0.0f,
            0.0f,
            0.1f,
            settings.rewardCount
        )
        player.networkHandler.sendPacket(particlePacket)
    }
}
