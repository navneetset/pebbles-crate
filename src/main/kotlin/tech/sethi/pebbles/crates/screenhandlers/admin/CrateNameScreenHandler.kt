package tech.sethi.pebbles.crates.screenhandlers.admin

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager

class CrateNameScreenHandler(syncId: Int, private val player: PlayerEntity) : GenericContainerScreenHandler(
    ScreenHandlerType.GENERIC_9X1, syncId, player.inventory, SimpleInventory(9), 1
) {

    init {
        val inventory = inventory
        val crateConfigManager = CrateConfigManager()

        // Fill the inventory with paper with modified name
        for (i in 1 until 9) {
            val paper = ItemStack(Items.PAPER)
            paper.setCustomName(Text.of("Add item to empty slot to name crate"))
            inventory.setStack(i, paper)
        }
        val existingCrates = crateConfigManager.loadCrateConfigs()

        // Add the crate name slot
        addSlot(Slot(inventory, 0, 8, 18))
    }


    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    override fun onSlotClick(slotIndex: Int, clickData: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (slotIndex == 0 && inventory.getStack(0).item == Items.NAME_TAG) {
            val crateName = inventory.getStack(0).name.string
            // Check if the crate name is already taken
            val existingCrates = CrateConfigManager().loadCrateConfigs()
            for (crateConfig in existingCrates) {
                if (crateConfig.crateName == crateName) {
                    player.sendMessage(Text.of("Crate name already taken"), false)
                    return
                }
            }
            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                PreviewIconScreenHandler(syncId, p, crateName)
            }, Text.of("Loot Icon Editor")))
        } else {
            super.onSlotClick(slotIndex, clickData, actionType, player)
        }
    }

}