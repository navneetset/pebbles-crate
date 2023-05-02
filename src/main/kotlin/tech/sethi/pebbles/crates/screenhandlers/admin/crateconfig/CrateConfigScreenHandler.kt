package tech.sethi.pebbles.crates.screenhandlers.admin.crateconfig

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateTransformer
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.CrateListScreenHandler

class CrateConfigScreenHandler(
    syncId: Int, player: PlayerEntity, crateName: String
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, player.inventory, SimpleInventory(9 * 3), 3) {

    init {
        val inventory = inventory
        for (i in 0 until inventory.size()) {
            inventory.setStack(i, ItemStack(Items.GRAY_STAINED_GLASS_PANE).setCustomName(Text.of("")))
        }

        inventory.setStack(
            12, ItemStack(Items.PAPER).setCustomName(Text.literal("Get Crate").formatted(Formatting.GOLD))
        )
        inventory.setStack(
            13, ItemStack(Items.TRIPWIRE_HOOK).setCustomName(Text.literal("Get Key").formatted(Formatting.GOLD))
        )
        inventory.setStack(
            14, ItemStack(Items.ITEM_FRAME).setCustomName(Text.literal("Configure Prize (To be implemented)").formatted(Formatting.GOLD))
        )

        inventory.setStack(18, ItemStack(Items.ARROW).setCustomName(Text.literal("Back").formatted(Formatting.RED)))
    }

    val crateConfigManager = CrateConfigManager()
    val crateConfig = crateConfigManager.getCrateConfig(crateName)
    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE || actionType == SlotActionType.SWAP || actionType == SlotActionType.PICKUP_ALL) {
            return
        }

        val crateTransformer = CrateTransformer(crateConfig!!.crateName, player!!)
        if (slotIndex == 18) {
            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                CrateListScreenHandler(syncId, p)
            }, Text.literal("Crate Management")))
        }

        if (slotIndex == 12) {
            crateTransformer.giveTransformer()
        }

        if (slotIndex == 13) {
            crateTransformer.giveKey(1, player)
        }
    }

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }


}