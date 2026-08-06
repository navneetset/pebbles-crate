package tech.sethi.pebbles.crates.screenhandlers.keys

import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.keys.KeyManager

/**
 * Read-only view of a key wallet. Also used by `/padmin keys <player>` to inspect someone else's,
 * so the balances are passed in rather than read from the viewer.
 */
class KeyBalanceScreenHandler(
    syncId: Int, viewer: PlayerEntity, balances: Map<String, Int>
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, viewer.inventory, SimpleInventory(9 * 6), 6) {

    // Sorted and snapshotted so slot indices stay put while the screen is open
    private val keys: List<Pair<String, Int>> = balances.filterValues { it > 0 }.toList().sortedBy { it.first }
    private val pageCount = ((keys.size - 1) / KEYS_PER_PAGE + 1).coerceAtLeast(1)
    private var page = 0

    init {
        populate()
    }

    private fun populate() {
        val firstIndex = page * KEYS_PER_PAGE
        val lastIndex = (firstIndex + KEYS_PER_PAGE).coerceAtMost(keys.size)

        for (slot in 0 until KEYS_PER_PAGE) {
            val index = firstIndex + slot
            if (index >= lastIndex) {
                inventory.setStack(slot, ItemStack.EMPTY)
                continue
            }

            val (crate, amount) = keys[index]
            inventory.setStack(slot, KeyIcons.keyIcon(crate, amount))
        }

        for (slot in KEYS_PER_PAGE until 54) {
            inventory.setStack(slot, KeyIcons.filler())
        }

        if (keys.isEmpty()) {
            inventory.setStack(
                22, KeyIcons.button(
                    Items.BARRIER, Messages.text("gui.keys.empty"), Messages.list("gui.keys.empty-lore")
                )
            )
        }

        if (pageCount > 1) {
            inventory.setStack(PAGE_INDICATOR_SLOT, ItemStack(Items.PAPER).apply {
                set(
                    DataComponentTypes.CUSTOM_NAME,
                    Messages.text("gui.page", "page" to "${page + 1}", "pages" to "$pageCount")
                )
            })

            if (page > 0) {
                inventory.setStack(PREVIOUS_SLOT, KeyIcons.button(Items.ARROW, Messages.text("gui.previous")))
            }
            if (page < pageCount - 1) {
                inventory.setStack(NEXT_SLOT, KeyIcons.button(Items.ARROW, Messages.text("gui.next")))
            }
        }
    }

    override fun canUse(player: PlayerEntity): Boolean = true

    /** Nothing here is takeable: every click either turns a page or does nothing at all. */
    override fun onSlotClick(slotIndex: Int, clickData: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (slotIndex < 0) return

        if (slotIndex == PREVIOUS_SLOT && page > 0) {
            page--
            populate()
        } else if (slotIndex == NEXT_SLOT && page < pageCount - 1) {
            page++
            populate()
        }
    }

    companion object {
        private const val KEYS_PER_PAGE = 45
        private const val PREVIOUS_SLOT = 45
        private const val PAGE_INDICATOR_SLOT = 49
        private const val NEXT_SLOT = 53

        fun open(viewer: ServerPlayerEntity, title: Text, balances: Map<String, Int>) {
            viewer.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, screenPlayer ->
                KeyBalanceScreenHandler(syncId, screenPlayer, balances)
            }, title))
        }

        /** The viewer's own wallet. */
        fun openOwn(player: ServerPlayerEntity) {
            open(player, Messages.text("gui.keys.own-title"), KeyManager.getBalances(player.uuid))
        }
    }
}
