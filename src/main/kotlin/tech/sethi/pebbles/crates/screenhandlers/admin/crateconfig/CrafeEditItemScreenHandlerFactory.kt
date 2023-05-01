package tech.sethi.pebbles.crates.screenhandlers.admin.crateconfig

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
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
import tech.sethi.pebbles.crates.util.ParseableName
import tech.sethi.pebbles.crates.util.setLore

class CrafeEditItemScreenHandlerFactory(private val title: Text, private val crateConfig: CrateConfig) :
    NamedScreenHandlerFactory {
    override fun createMenu(syncId: Int, inv: PlayerInventory, player: PlayerEntity): ScreenHandler {
        var currentPage = 0

        val crateItems = crateConfig.prize
        val handler = object : GenericContainerScreenHandler(
            ScreenHandlerType.GENERIC_9X6, syncId, inv, CrateInventory(crateItems, currentPage), 6
        ) {
            override fun onSlotClick(
                slotNumber: Int, button: Int, action: SlotActionType, playerEntity: PlayerEntity
            ) {
                if (slotNumber == 45) { // Previous page arrow
                    if (currentPage > 0) {
                        currentPage--
                        (this.inventory as CrateInventory).populateInventory(crateItems, currentPage)
                    }
                } else if (slotNumber == 53) { // Next page arrow
                    if (currentPage < (crateItems.size - 1) / 45) {
                        currentPage++
                        (this.inventory as CrateInventory).populateInventory(crateItems, currentPage)
                    }
                } else {
                    return
                }
            }
        }
        return handler

    }

    override fun getDisplayName(): Text {
        return title
    }
}


class CrateInventory(private val crateItems: List<Prize>, private var currentPage: Int) : SimpleInventory(54) {
    init {
        populateInventory(crateItems, currentPage)
    }

    fun populateInventory(crateItems: List<Prize>, currentPage: Int) {
        clear()
        val itemsPerPage = 45
        val startIndex = currentPage * itemsPerPage
        val endIndex = (startIndex + itemsPerPage).coerceAtMost(crateItems.size)

        val totalWeight = crateItems.sumOf { it.chance }
        for (index in startIndex until endIndex) {
            val prize = crateItems[index]
            val itemStack = ItemStack(Registry.ITEM.get(Identifier.tryParse(prize.material)))
            val parsedName = ParseableName(prize.name).returnMessageAsStyledText()

            val chance = prize.chance.toDouble() / totalWeight.toDouble() * 100
            val roundedChance = String.format("%.2f", chance)

            if (prize.nbt != null) {
                val nbt: NbtCompound = NbtHelper.fromNbtProviderString(prize.nbt)
                itemStack.nbt = nbt
            }

            setLore(itemStack, listOf(Text.of("Chance: ${roundedChance}%")))
            setStack(index - startIndex, itemStack.setCustomName(parsedName))
        }

        // Fill the bottom row with gray stained glass
        for (i in 45..53) {
            setStack(i, ItemStack(Items.GRAY_STAINED_GLASS_PANE))
        }

        val pageText = Text.of("Page ${currentPage + 1} of ${((crateItems.size - 1) / 45) + 1}")

        // Set the page text
        setStack(52, ItemStack(Items.PAPER).apply { setCustomName(pageText) })

        // Set the navigation arrows
        setStack(45, ItemStack(Items.ARROW).apply { setCustomName(Text.of("Previous")) })
        setStack(53, ItemStack(Items.ARROW).apply { setCustomName(Text.of("Next")) })
    }
}
