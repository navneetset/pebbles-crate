package tech.sethi.pebbles.crates.screenhandlers.admin.cratelist

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.screenhandlers.admin.crateconfig.CrateConfigScreenHandler
import tech.sethi.pebbles.crates.util.ParseableName

class CrateListScreenHandler(syncId: Int, player: PlayerEntity) :
    GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, player.inventory, SimpleInventory(9 * 6), 6) {

    private val crateConfigManager = CrateConfigManager()

    init {
        val existingCrates = crateConfigManager.loadCrateConfigs()

        for ((index, crateConfig) in existingCrates.withIndex()) {
            val crateItem = ItemStack(Items.ENDER_CHEST)
            val formattedName = ParseableName(crateConfig.crateName).returnMessageAsStyledText()
            crateItem.setCustomName(formattedName)
            inventory.setStack(index, crateItem)
        }

//        val createNewCrateItem = ItemStack(Items.PAPER)
//        createNewCrateItem.setCustomName(
//            Text.literal("Create New Crate").formatted(Formatting.GREEN)
//        )

        // fill last row with gray_stained_glass_pane
        for (i in 45 until 54) {
            val pane = ItemStack(Items.GRAY_STAINED_GLASS_PANE)
            pane.setCustomName(Text.of(""))
            inventory.setStack(i, pane)
        }

//        inventory.setStack(53, createNewCrateItem)
    }


    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    override fun onSlotClick(slotIndex: Int, clickData: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE || actionType == SlotActionType.SWAP || actionType == SlotActionType.PICKUP_ALL) {
            return
        }

        player.sendMessage(Text.of("Slot index: $slotIndex"), false)

        // Get material of clicked item
        player.sendMessage(Text.of("Item: ${inventory.getStack(slotIndex)}"), false)

//        if (slotIndex == 53) { // Check if the "Create New Crate" button is clicked
//            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
//                CrateNameScreenHandler(syncId, p)
//            }, Text.of("Crate Configuration")))
//        } else
        if (slotIndex in 0..44 && actionType == SlotActionType.PICKUP) {
            val existingCrates = crateConfigManager.loadCrateConfigs()
            if (slotIndex in existingCrates.indices) {
                val crateConfig = existingCrates[slotIndex]
                player.sendMessage(Text.of("Opening config for crate: ${crateConfig.crateName}"), false)
                // Open the configuration screen for the selected crate
                val crateName = crateConfig.crateName
                player.sendMessage(Text.of("Crate name: $crateName"), false)
                player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                    CrateConfigScreenHandler(syncId, p, crateName)
                }, Text.of("$crateName Configuration")))
            }
        }
    }
}