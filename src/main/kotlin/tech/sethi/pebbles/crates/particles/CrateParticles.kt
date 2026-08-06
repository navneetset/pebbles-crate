package tech.sethi.pebbles.crates.particles

import net.minecraft.network.packet.s2c.play.ParticleS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import kotlin.math.cos
import kotlin.math.sin

object CrateParticles {
    private var stepX = 1

    private const val particles = 2
    private const val particlesPerRotation = 20
    private const val radius = 1

    fun updateTimers() {
        stepX++
    }

    fun spawnCrossSpiralsParticles(player: ServerPlayerEntity, pos: BlockPos, world: ServerWorld) {
        val effect = GlobalConfigManager.particleEffect(GlobalConfigManager.particles.idle) ?: return

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

    /** The burst that marks a won prize: the configured sounds for everyone nearby, particles for the opener. */
    fun rewardParticles(player: ServerPlayerEntity, pos: BlockPos) {
        val world = player.world
        val settings = GlobalConfigManager.particles
        val animation = GlobalConfigManager.animation

        val sounds = animation.rewardSounds.mapNotNull { sound ->
            GlobalConfigManager.soundEvent(sound.id)?.let { it to sound }
        }

        repeat(animation.rewardSoundRepeats) {
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
