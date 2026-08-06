package tech.sethi.pebbles.crates.screenhandlers.admin.cratelist

import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.screenhandlers.admin.crateconfig.CrateConfigScreenHandler
import tech.sethi.pebbles.crates.util.ParseableName

class CrateListScreenHandler(syncId: Int, player: PlayerEntity) :
    GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, player.inventory, SimpleInventory(9 * 6), 6) {

    // Snapshot so the slot indices stay stable while the screen is open
    private val crates: List<CrateConfig> = CrateConfigManager.getCrateConfigs()
    private val pageCount = ((crates.size - 1) / CRATES_PER_PAGE + 1).coerceAtLeast(1)
    private var page = 0

    init {
        populate()
    }

    private fun populate() {
        val firstIndex = page * CRATES_PER_PAGE
        val lastIndex = (firstIndex + CRATES_PER_PAGE).coerceAtMost(crates.size)

        for (slot in 0 until CRATES_PER_PAGE) {
            val index = firstIndex + slot
            if (index >= lastIndex) {
                inventory.setStack(slot, ItemStack.EMPTY)
                continue
            }

            val crateItem = ItemStack(Items.ENDER_CHEST)
            crateItem.set(
                DataComponentTypes.CUSTOM_NAME, ParseableName(crates[index].crateName).returnMessageAsStyledText()
            )
            inventory.setStack(slot, crateItem)
        }

        // fill last row with gray_stained_glass_pane
        for (slot in CRATES_PER_PAGE until 54) {
            val pane = ItemStack(Items.GRAY_STAINED_GLASS_PANE)
            pane.set(DataComponentTypes.CUSTOM_NAME, Text.of(""))
            inventory.setStack(slot, pane)
        }

        if (pageCount > 1) {
            inventory.setStack(PAGE_INDICATOR_SLOT, ItemStack(Items.PAPER).apply {
                set(
                    DataComponentTypes.CUSTOM_NAME,
                    Messages.text("gui.page", "page" to "${page + 1}", "pages" to "$pageCount")
                )
            })

            if (page > 0) {
                inventory.setStack(PREVIOUS_SLOT, ItemStack(Items.ARROW).apply {
                    set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.previous"))
                })
            }
            if (page < pageCount - 1) {
                inventory.setStack(NEXT_SLOT, ItemStack(Items.ARROW).apply {
                    set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.next"))
                })
            }
        }
    }

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    override fun onSlotClick(slotIndex: Int, clickData: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE || actionType == SlotActionType.SWAP || actionType == SlotActionType.PICKUP_ALL) {
            return
        }

        // Clicking outside the window reports -999
        if (slotIndex < 0) return

        if (slotIndex == PREVIOUS_SLOT && page > 0) {
            page--
            populate()
            return
        }

        if (slotIndex == NEXT_SLOT && page < pageCount - 1) {
            page++
            populate()
            return
        }

        if (slotIndex < CRATES_PER_PAGE && actionType == SlotActionType.PICKUP) {
            val crateIndex = page * CRATES_PER_PAGE + slotIndex
            val crateConfig = crates.getOrNull(crateIndex) ?: return

            // Open the configuration screen for the selected crate
            val crateName = crateConfig.crateName
            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                CrateConfigScreenHandler(syncId, p, crateName)
            }, Messages.text("gui.crates.config-title", "crate_name" to crateName)))
        }
    }

    companion object {
        private const val CRATES_PER_PAGE = 45
        private const val PREVIOUS_SLOT = 45
        private const val PAGE_INDICATOR_SLOT = 49
        private const val NEXT_SLOT = 53
    }
}
