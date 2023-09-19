package tech.sethi.pebbles.crates.particles

import net.minecraft.network.packet.s2c.play.ParticleS2CPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
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

    fun spawnSpiralParticles(player: ServerPlayerEntity, pos: BlockPos, world: ServerWorld) {
        for (stepY in 0 until 60 step (120 / particles)) {
            val dx = -(cos(((stepX + stepY) / particlesPerRotation.toDouble()) * Math.PI * 2)) * radius
            val dy = stepY / particlesPerRotation.toDouble() / 2.0
            val dz = -(sin(((stepX + stepY) / particlesPerRotation.toDouble()) * Math.PI * 2)) * radius

            val x = pos.x + 0.5 + dx
            val y = pos.y + 0.5 + dy
            val z = pos.z + 0.5 + dz

            val particlePacket = ParticleS2CPacket(
                ParticleTypes.SOUL_FIRE_FLAME, false, x, y, z, 0.0f, 0.0f, 0.0f, 0.0f, 1
            )
            player.networkHandler.sendPacket(particlePacket)
        }
    }

    fun spawnCrossSpiralsParticles(player: ServerPlayerEntity, pos: BlockPos, world: ServerWorld) {
        for (stepY in 0 until 60 step (120 / particles)) {
            val dx = -(cos(((stepX + stepY) / particlesPerRotation.toDouble()) * Math.PI * 2)) * radius
            val dy = stepY / particlesPerRotation.toDouble() / 2.0
            val dz = -(sin(((stepX + stepY) / particlesPerRotation.toDouble()) * Math.PI * 2)) * radius

            val x = pos.x + 0.5 + dx
            val y = pos.y + 1.5 + dy
            val z = pos.z + 0.5 + dz

            val particlePacket = ParticleS2CPacket(
                ParticleTypes.FIREWORK, false, x, y, z, 0.0f, 0.0f, 0.0f, 0.0f, 1
            )
            player.networkHandler.sendPacket(particlePacket)
        }
    }

    fun rewardParticles(player: ServerPlayerEntity, pos: BlockPos) {
        val world = player.world

        for (i in 0 until 5) {
            world.playSound(
                null as ServerPlayerEntity?, pos, SoundEvents.ENTITY_ALLAY_DEATH, SoundCategory.BLOCKS, 0.5f, 0.5f
            )
            world.playSound(
                null as ServerPlayerEntity?, pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.5f, 1f
            )
        }

        val offsetX = 0.5
        val offsetY = 0.2
        val offsetZ = 0.5

        val particlePacket = ParticleS2CPacket(
            ParticleTypes.SCULK_SOUL,
            false,
            pos.x + offsetX,
            pos.y + 0.5 + offsetY,
            pos.z + offsetZ,
            0.0f,
            0.0f,
            0.0f,
            0.1f,
            50
        )
        player.networkHandler.sendPacket(particlePacket)

        player.networkHandler.sendPacket(particlePacket)

    }
}
