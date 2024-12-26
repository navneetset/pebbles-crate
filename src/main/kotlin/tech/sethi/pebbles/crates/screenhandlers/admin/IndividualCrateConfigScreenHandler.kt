package tech.sethi.pebbles.crates.screenhandlers.admin

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.Registries
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager

class IndividualCrateConfigScreenHandler(syncId: Int, private val player: PlayerEntity, private val crateName: String) :
    GenericContainerScreenHandler(
        ScreenHandlerType.GENERIC_9X6, syncId, player.inventory, SimpleInventory(9 * 6), 6
    ) {

        private val crateConfigManager = CrateConfigManager()

    init {
        val existingCrates = crateConfigManager.loadCrateConfigs()
        val currentCrateConfig = existingCrates.first { it.crateName == crateName }

        val inventory = inventory

        val crateItems = currentCrateConfig.prize

        for (i in 0 until inventory.size()) {
            inventory.setStack(i, ItemStack.EMPTY)
        }

        for ((index, prize) in crateItems.withIndex()) {
            val materialIdentifier = Identifier.tryParse(prize.material)
            if (materialIdentifier != null) {
                val item = Registries.ITEM.get(materialIdentifier)
                if (item != Items.AIR) {
                    val itemStack = ItemStack(item, prize.amount)
                    val nbt = NbtCompound().apply {
                        putString("PebblesCrateNBT", prize.nbt ?: "")
                    }
                    itemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
                    setLore(itemStack, prize.commands.map { Text.of(it) })
                    inventory.setStack(index, itemStack)
                }
            }
        }
    }

    private fun setLore(itemStack: ItemStack, lore: List<Text>) {
        val loreComponent = LoreComponent(lore)
        itemStack.set(DataComponentTypes.LORE, loreComponent)
    }

}