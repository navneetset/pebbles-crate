package tech.sethi.pebbles.crates.screenhandlers

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.Prize
import tech.sethi.pebbles.crates.util.setLore
import java.math.BigDecimal

class PrizeDisplayScreenHandlerFactory(private val title: Text, private val crateConfig: CrateConfig): NamedScreenHandlerFactory {


    override fun createMenu(syncId: Int, inv: PlayerInventory, player: PlayerEntity): ScreenHandler {
        val crateItems = crateConfig.prize
        val handler =
            object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, inv, CrateInventory(crateItems), 6) {
                override fun onSlotClick(slotNumber: Int, button: Int, action: SlotActionType, playerEntity: PlayerEntity) {
                    return super.onSlotClick(slotNumber, button, action, playerEntity)
                }
            }
        return handler

    }

    override fun getDisplayName(): Text {
        return title
    }
}


class CrateInventory(private val crateItems: List<Prize>) : SimpleInventory(54) {

    init {
        populateInventory()
    }

    private fun populateInventory() {
        val totalWeight = crateItems.sumOf { it.chance }
        for ((index, prize) in crateItems.withIndex()) {
            val itemStack = ItemStack(Registry.ITEM.get(Identifier.tryParse(prize.material)))
            val chance = BigDecimal.valueOf(prize.chance.toDouble()).divide(BigDecimal.valueOf(totalWeight.toDouble()), 2, BigDecimal.ROUND_HALF_UP) * BigDecimal.valueOf(100)
            setLore(itemStack, listOf(Text.of("Chance: ${chance}%")))
            setStack(index, itemStack)
        }
    }
}

/*
class PrizeDisplayScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val crateConfig: CrateConfig
) : ScreenHandler(null, syncId) {

    private val inventory: SimpleInventory

    init {
        val rows = 5
        val columns = 9
        inventory = SimpleInventory(columns * rows)

        // Populate the inventory with the prizes and their chances
        populateInventory()

        // Add the slots for the inventory
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                addSlot(Slot(inventory, column + row * columns, 8 + column * 18, 18 + row * 18))
            }
        }

        // Add the player's inventory slots
        val playerInventoryStartX = 8
        val playerInventoryStartY = 104
        val playerHotbarStartY = 162

        for (row in 0..2) {
            for (column in 0..8) {
                addSlot(Slot(playerInventory, column + row * 9 + 9, playerInventoryStartX + column * 18, playerInventoryStartY + row * 18))
            }
        }

        for (column in 0..8) {
            addSlot(Slot(playerInventory, column, playerInventoryStartX + column * 18, playerHotbarStartY))
        }
    }

    private fun populateInventory() {
        val prizes = crateConfig.prize

        for ((index, prize) in prizes.withIndex()) {
            val itemStack = ItemStack(Registry.ITEM.get(Identifier.tryParse(prize.material)))
            setLore(itemStack, listOf(Text.of("Chance: ${prize.chance}%")))
            inventory.setStack(index, itemStack)
        }
    }

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    override fun transferSlot(player: PlayerEntity, index: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun onSlotClick(slotIndex: Int, clickData: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (slotIndex >= 0 && slotIndex < inventory.size()) {
            // Prevent the player from taking the items
            return
        }

        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE || actionType == SlotActionType.SWAP || actionType == SlotActionType.PICKUP_ALL) {
            return
        }

        return super.onSlotClick(slotIndex, clickData, actionType, player)
    }

    override fun canInsertIntoSlot(slot: Slot?): Boolean {
        return false
    }

    override fun onButtonClick(player: PlayerEntity?, id: Int): Boolean {
        return false
    }

    override fun insertItem(stack: ItemStack, startIndex: Int, endIndex: Int, fromLast: Boolean): Boolean {
        return false
    }

}


class PrizeDisplayScreenHandlerFactory(private val crateConfig: CrateConfig) : NamedScreenHandlerFactory {
    override fun createMenu(syncId: Int, inv: PlayerInventory, player: PlayerEntity): ScreenHandler {
        return PrizeDisplayScreenHandler(syncId, inv, crateConfig)
    }

    override fun getDisplayName(): Text {
        return Text.of(crateConfig.crateName)
    }
}*/
