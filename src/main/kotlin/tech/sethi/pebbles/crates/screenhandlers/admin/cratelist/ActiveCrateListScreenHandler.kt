package tech.sethi.pebbles.crates.screenhandlers.admin.cratelist

import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.PebblesCrate.server
import tech.sethi.pebbles.crates.lootcrates.BlacklistConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.util.WorldBlockPos

class ActiveCrateList(syncId: Int, val player: PlayerEntity) :
    GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, player.inventory, SimpleInventory(9 * 6), 6) {

    private val activeCrates = CrateDataManager().loadCrateData()

    private val blacklistManager = BlacklistConfigManager()

    init {
        initializeInventory()
    }


    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    private fun initializeInventory() {
        val blacklist = blacklistManager.getBlacklist()
        for ((index, crateName) in activeCrates.values.withIndex()) {
            val worldBlockPos = activeCrates.keys.elementAt(index)

            // Try to get the block from the correct world
            val world = server?.worlds?.find { PebblesCrate.getWorldId(it) == worldBlockPos.worldId }
            val crateItem = if (world != null) {
                val blockOnPos = world.getBlockState(worldBlockPos.pos).block
                blockOnPos.asItem().defaultStack
            } else {
                // World not loaded, show a placeholder
                Items.BARRIER.defaultStack
            }

            // Show world info in the name
            val worldName = worldBlockPos.worldId.substringAfter(":")
            crateItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("[$worldName] ${worldBlockPos.pos.x}, ${worldBlockPos.pos.y}, ${worldBlockPos.pos.z} - $crateName"))

            if (!blacklist.contains(worldBlockPos)) {
                val vanishingEnchant = server!!.worlds.first().registryManager.get(RegistryKeys.ENCHANTMENT)
                    .get(Enchantments.VANISHING_CURSE)
                crateItem.addEnchantment(RegistryEntry.of(vanishingEnchant), 1)
            }
            inventory.setStack(index, crateItem)
        }
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE || actionType == SlotActionType.SWAP || actionType == SlotActionType.PICKUP_ALL) {
            return
        }

        if (slotIndex >= activeCrates.size) {
            return
        }

        val worldBlockPos = activeCrates.keys.elementAt(slotIndex)

        val blacklist = blacklistManager.getBlacklist()
        if (blacklist.contains(worldBlockPos)) {
            blacklistManager.removeFromBlacklist(worldBlockPos)
        } else {
            blacklistManager.addToBlacklist(worldBlockPos)
        }

        // close and reopen screen
        player!!.currentScreenHandler.onClosed(player)
        player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
            ActiveCrateList(syncId, p)
        }, Text.literal("Blacklist Particles")))


        return
    }

}