package tech.sethi.pebbles.crates.screenhandlers.admin.crateconfig

import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateTransformer
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.CrateListScreenHandler
import tech.sethi.pebbles.crates.screenhandlers.keys.KeyIcons

class CrateConfigScreenHandler(
    syncId: Int, player: PlayerEntity, private val crateName: String
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, player.inventory, SimpleInventory(9 * 3), 3) {

    init {
        val inventory = inventory
        for (i in 0 until inventory.size()) {
            inventory.setStack(i,
                ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { set(DataComponentTypes.CUSTOM_NAME, Text.of("")) })
        }

        inventory.setStack(12, ItemStack(Items.PAPER).apply {
            set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.crates.get-crate"))
        })

        inventory.setStack(13, ItemStack(KeyIcons.keyMaterial(CrateConfigManager.getCrateConfig(crateName))).apply {
            set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.crates.get-key"))
        })

        inventory.setStack(14, ItemStack(Items.ITEM_FRAME).apply {
            set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.crates.web-editor"))
        })

        inventory.setStack(18, ItemStack(Items.ARROW).apply {
            set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.crates.back"))
        })
    }

    val crateConfigManager = CrateConfigManager
    val crateConfig = crateConfigManager.getCrateConfig(crateName)
    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE || actionType == SlotActionType.SWAP || actionType == SlotActionType.PICKUP_ALL) {
            return
        }

        // Clicking outside the window reports -999; the crate can be gone after a /padmin reload
        if (slotIndex < 0 || player == null) return
        if (crateConfig == null) {
            Messages.send(player, "gui.crates.missing", "crate_name" to crateName)
            return
        }

        val crateTransformer = CrateTransformer(crateConfig.crateName, player)
        if (slotIndex == 18) {
            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                CrateListScreenHandler(syncId, p)
            }, Messages.text("gui.crates.title")))
        }

        if (slotIndex == 12) {
            crateTransformer.giveTransformer()
        }

        if (slotIndex == 13) {
            if (!crateTransformer.giveKey(1, player)) {
                Messages.send(player, "command.givekey.no-item", "crate_name" to crateConfig.crateName)
            }
        }

        if (slotIndex == 14) {
            val url = "https://pebblescrate.sethi.tech/"
            val clickableLink = Text.Serialization.fromJson(
                ("{\"text\":\"$url\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"$url\"}}"),
                DynamicRegistryManager.EMPTY
            ) ?: Text.literal(url)
            player.sendMessage(
                Messages.text("gui.crates.web-editor-hint").copy().append(clickableLink), false
            )
        }
    }

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

}