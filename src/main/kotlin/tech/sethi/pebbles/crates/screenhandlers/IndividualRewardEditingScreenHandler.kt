package tech.sethi.pebbles.crates.screenhandlers

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import java.math.BigDecimal

class IndividualRewardEditingScreenHandler(
        syncId: Int,
        player: PlayerEntity,
        private val crateName: String,
        private val previewItem: ItemStack,
        private val weight: BigDecimal
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X1, syncId, player.inventory, SimpleInventory(9), 1) {

    init {
        // Add slots for the reward items or command rewards
        for (i in 0 until 9) {
            addSlot(Slot(inventory, i, 8 + i * 18, 18))
        }
    }

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    // You'll need to handle the button clicks for adjusting the odds here.
    // You can use onButtonClick method for handling button events or create custom widgets.
}