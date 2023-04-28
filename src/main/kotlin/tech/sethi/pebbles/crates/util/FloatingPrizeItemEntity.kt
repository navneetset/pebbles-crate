package tech.sethi.pebbles.crates.util

import net.minecraft.entity.ItemEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class FloatingPrizeItemEntity(
    world: World,
    x: Double,
    y: Double,
    z: Double,
    stack: ItemStack,
    private val armorStandEntity: ArmorStandEntity
) : ItemEntity(world, x, y, z, stack) {
    private var ticksElapsed = 0

    init {
        setNoGravity(true)
        setPickupDelay(Int.MAX_VALUE)
        startRiding(armorStandEntity)
        isInvisible = false
        isInvulnerable = true
        velocity = Vec3d.ZERO
    }

    override fun tick() {
        super.tick()
        ticksElapsed++
        if (ticksElapsed >= 100) {
            armorStandEntity.kill()
            this.kill()
        }
    }
}
