package tech.sethi.pebbles.crates.screenhandlers.admin.cratelist

import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import tech.sethi.pebbles.crates.lootcrates.BlacklistConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager

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
            val cratePos = activeCrates.keys.elementAt(index)
            val blockOnPost = player.world.getBlockState(cratePos).block
            val crateItem = blockOnPost.asItem().defaultStack
            crateItem.setCustomName(crateItem.name.copy().append(" - $crateName"))
            if (!blacklist.contains(cratePos)) {
                crateItem.addEnchantment(Enchantments.VANISHING_CURSE, 1)
                crateItem.removeSubNbt("HideFlags")
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

        val cratePos = activeCrates.keys.elementAt(slotIndex)

        val blacklist = blacklistManager.getBlacklist()
        if (blacklist.contains(cratePos)) {
            blacklistManager.removeFromBlacklist(cratePos)
        } else {
            blacklistManager.addToBlacklist(cratePos)
        }

        // close and reopen screen
        player!!.currentScreenHandler.close(player)
        player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
            ActiveCrateList(syncId, p)
        }, Text.literal("Blacklist Particles")))


        return
    }

}