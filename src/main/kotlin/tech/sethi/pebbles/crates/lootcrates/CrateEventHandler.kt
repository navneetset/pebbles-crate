package tech.sethi.pebbles.crates.lootcrates

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.util.ActionResult

class CrateEventHandler {
    init {
        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, hitResult ->
            val block = world.getBlockState(hitResult.blockPos).block

            // Check if the block is an Enderchest
            if (block == Blocks.ENDER_CHEST) {
                val heldItem = player.getStackInHand(hand)

                // Check if the player is holding a key item
                val crateConfigManager = CrateConfigManager()
                val crateConfig =
                    crateConfigManager.getCrateConfig("Silver") // Replace "Silver" with the actual crate name from config
                if (crateConfig != null) {
                    // Open the custom crate interface
                    // Implement your logic here

                    // Consume the key item
//                    if (!player.isCreative) {
//                        heldItem.decrement(1)
//                    }

                    // Cancel the default Enderchest behavior
                    ActionResult.SUCCESS
                } else {
                    ActionResult.PASS
                }
            } else {
                ActionResult.PASS
            }
        })
    }
}