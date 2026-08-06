package tech.sethi.pebbles.crates.screenhandlers.admin.cratelist

import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType.GENERIC_9X6
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.PebblesCrate.server
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.lootcrates.BlacklistConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.util.WorldBlockPos

class ActiveCrateList(syncId: Int, val player: PlayerEntity, private var page: Int = 0) :
    GenericContainerScreenHandler(GENERIC_9X6, syncId, player.inventory, SimpleInventory(9 * 6), 6) {

    // Snapshot so the slot indices stay stable while the screen is open
    private val activeCrates: List<Map.Entry<WorldBlockPos, String>> = CrateDataManager.snapshot().entries.toList()
    private val pageCount = ((activeCrates.size - 1) / CRATES_PER_PAGE + 1).coerceAtLeast(1)

    init {
        page = page.coerceIn(0, pageCount - 1)
        initializeInventory()
    }


    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    private fun initializeInventory() {
        val blacklist = BlacklistConfigManager.getBlacklist()
        val firstIndex = page * CRATES_PER_PAGE
        val lastIndex = (firstIndex + CRATES_PER_PAGE).coerceAtMost(activeCrates.size)

        for (slot in 0 until CRATES_PER_PAGE) {
            val index = firstIndex + slot
            if (index >= lastIndex) {
                inventory.setStack(slot, ItemStack.EMPTY)
                continue
            }

            val (worldBlockPos, crateName) = activeCrates[index]

            // Try to get the block from the correct world. Asking for a block in a chunk that is not
            // in memory would load - and if need be generate - it right there on the tick thread, up
            // to a chunk per entry, again on every page turn.
            val world = server?.worlds?.find { PebblesCrate.getWorldId(it) == worldBlockPos.worldId }
            val crateItem = if (world != null && world.isChunkLoaded(
                    worldBlockPos.pos.x shr 4, worldBlockPos.pos.z shr 4
                )
            ) {
                val blockOnPos = world.getBlockState(worldBlockPos.pos).block
                blockOnPos.asItem().defaultStack
            } else {
                // World or chunk not loaded, show a placeholder
                Items.BARRIER.defaultStack
            }

            // Show world info in the name
            crateItem.set(
                DataComponentTypes.CUSTOM_NAME, Messages.text(
                    "gui.activecrates.entry",
                    "world" to worldBlockPos.worldId.substringAfter(":"),
                    "x" to "${worldBlockPos.pos.x}",
                    "y" to "${worldBlockPos.pos.y}",
                    "z" to "${worldBlockPos.pos.z}",
                    "crate_name" to crateName
                )
            )

            val enchantmentRegistry = world?.registryManager?.get(RegistryKeys.ENCHANTMENT)
            if (!blacklist.contains(worldBlockPos) && enchantmentRegistry != null) {
                val vanishingEnchant = enchantmentRegistry.get(Enchantments.VANISHING_CURSE)
                if (vanishingEnchant != null) {
                    crateItem.addEnchantment(RegistryEntry.of(vanishingEnchant), 1)
                }
            }
            inventory.setStack(slot, crateItem)
        }

        for (slot in CRATES_PER_PAGE until 54) {
            inventory.setStack(slot, ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                set(DataComponentTypes.CUSTOM_NAME, Text.of(""))
            })
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

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE || actionType == SlotActionType.SWAP || actionType == SlotActionType.PICKUP_ALL) {
            return
        }

        // Clicking outside the window reports -999
        if (slotIndex < 0 || player == null) return

        if (slotIndex == PREVIOUS_SLOT && page > 0) {
            page--
            initializeInventory()
            return
        }

        if (slotIndex == NEXT_SLOT && page < pageCount - 1) {
            page++
            initializeInventory()
            return
        }

        if (slotIndex >= CRATES_PER_PAGE) return

        val crateIndex = page * CRATES_PER_PAGE + slotIndex
        val worldBlockPos = activeCrates.getOrNull(crateIndex)?.key ?: return

        val blacklist = BlacklistConfigManager.getBlacklist()
        if (blacklist.contains(worldBlockPos)) {
            BlacklistConfigManager.removeFromBlacklist(worldBlockPos)
        } else {
            BlacklistConfigManager.addToBlacklist(worldBlockPos)
        }

        // Reopen on the same page; opening a screen closes the one already up.
        val reopenPage = page
        player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
            ActiveCrateList(syncId, p, reopenPage)
        }, Messages.text("gui.activecrates.title")))
    }

    companion object {
        private const val CRATES_PER_PAGE = 45
        private const val PREVIOUS_SLOT = 45
        private const val PAGE_INDICATOR_SLOT = 49
        private const val NEXT_SLOT = 53
    }
}
