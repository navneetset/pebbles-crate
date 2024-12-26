package tech.sethi.pebbles.crates.screenhandlers.admin

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.Prize
import java.math.BigDecimal

class PreviewIconScreenHandler(
    syncId: Int, player: PlayerEntity, private val crateName: String
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, player.inventory, SimpleInventory(9 * 3), 3) {

    private val oddsSumItem = ItemStack(Items.PAPER)
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

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    override fun onSlotClick(slotIndex: Int, clickData: Int, actionType: SlotActionType, player: PlayerEntity) {
        // Open the IndividualRewardEditingScreen for the clicked item
        // Save the preview items and their weights to a JSON file
        if (slotIndex == 9 * 3 - 1) {
            // Save the preview items
            player.sendMessage(Text.of("Saving..."), false)

            // Get the current crate configurations
            val currentCrateConfigs = crateConfigManager.loadCrateConfigs()

            // Update or add the crate configuration
            val updatedPrizes = ArrayList<Prize>()
            for (i in 0 until 18) {
                val stack = inventory.getStack(i)
                if (!stack.isEmpty) {
                    getWeightFromLore(stack).let { weight ->
                        val prize = currentCrateConfigs.first { it.crateName == crateName }.prize[i]
                        updatedPrizes.add(prize.copy(chance = weight.toInt()))
                    }
                }
            }
            val existingCrateConfig = currentCrateConfigs.find { it.crateName == crateName }
            if (existingCrateConfig != null) {
                existingCrateConfig.prize = updatedPrizes
            } else {
                val currentCrateConfig = currentCrateConfigs.first { it.crateName == crateName }
                val newCrateConfig = CrateConfig(
                    crateName = crateName, crateKey = currentCrateConfig.crateKey, prize = updatedPrizes
                )
                crateConfigManager.setCrateConfig(crateName, newCrateConfig)
            }

            // Save the updated crate configurations
            crateConfigManager.saveCrateConfigs(currentCrateConfigs)

            player.sendMessage(Text.of("Saved!"), false)
            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                IndividualCrateConfigScreenHandler(syncId, p, crateName)
            }, Text.of("$crateName Config")))

        } else {
            super.onSlotClick(slotIndex, clickData, actionType, player)
        }
    }


    private fun setLore(itemStack: ItemStack, lore: List<Text>) {
        val loreComponent = LoreComponent(lore)
        itemStack.set(DataComponentTypes.LORE, loreComponent)
    }


    private fun getWeightFromLore(itemStack: ItemStack): BigDecimal {
        val lore = itemStack.get(DataComponentTypes.LORE)?.lines
        val line = Text.Serialization.fromJson(lore?.get(0)?.string, DynamicRegistryManager.EMPTY)
        return if (lore != null && lore.size > 0) BigDecimal(line?.string?.split(": ")?.get(1) ?: "0")
        else BigDecimal.ZERO
    }
}