package tech.sethi.pebbles.crates.screenhandlers.admin

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import tech.sethi.pebbles.crates.lootcrates.CrateConfig

class CrateScreenHandlerFactory(private val crateConfig: CrateConfig) : NamedScreenHandlerFactory {
    override fun createMenu(syncId: Int, inv: PlayerInventory, player: PlayerEntity): ScreenHandler {
        // Create and return the screen handler for the crate interface
        // You will implement this in the next step
        return CrateScreenHandler(syncId, inv, crateConfig)
    }

    override fun getDisplayName(): Text {
        return Text.of(crateConfig.crateName)
    }
}

class CrateScreenHandler(
    syncId: Int,
    private val playerInventory: PlayerInventory,
    private val crateConfig: CrateConfig
) : ScreenHandler(null, syncId) {

    private val inventory: SimpleInventory

    init {
        val rows = 6
        val columns = 9
        inventory = SimpleInventory(columns * rows)

        // Populate the inventory with the prizes and their chances
        // You will implement this in the next step
        populateInventory()

        // Add the slots for the inventory
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                addSlot(Slot(inventory, column + row * columns, 8 + column * 18, 18 + row * 18))
            }
        }

        // Add the player's inventory slots
        val playerInventoryStartX = 8
        val playerInventoryStartY = 140
        val playerHotbarStartY = 198

        for (row in 0..2) {
            for (column in 0..8) {
                addSlot(
                    Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        playerInventoryStartX + column * 18,
                        playerInventoryStartY + row * 18
                    )
                )
            }
        }

        for (column in 0..8) {
            addSlot(Slot(playerInventory, column, playerInventoryStartX + column * 18, playerHotbarStartY))
        }
    }

    private fun populateInventory() {
        // Populate the inventory with the prizes and their chances
        // This is just a placeholder, replace this with your actual implementation
        val prizes = crateConfig.prize
        for ((index, prize) in prizes.withIndex()) {
            // Add the prize item to the inventory with its chance as lore
            val itemStack = ItemStack(Registry.ITEM.get(Identifier.tryParse(prize.material)))
            setLore(itemStack, listOf(Text.of("Chance: ${prize.chance}")))
            inventory.setStack(index, itemStack)
        }
    }

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    override fun transferSlot(player: PlayerEntity, index: Int): ItemStack {
        return ItemStack.EMPTY
    }

    private fun setLore(itemStack: ItemStack, lore: List<Text>) {
        val itemNbt = itemStack.getOrCreateSubNbt("display")
        val loreNbt = NbtList()

        for (line in lore) {
            loreNbt.add(NbtString.of(Text.Serializer.toJson(line)))
        }

        itemNbt.put("Lore", loreNbt)
    }
}

