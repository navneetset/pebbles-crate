package tech.sethi.pebbles.crates.screenhandlers.admin

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
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
                val item = Registry.ITEM.get(materialIdentifier)
                if (item != Items.AIR) {
                    val itemStack = ItemStack(item, prize.amount)
                    itemStack.nbt = NbtCompound().apply { this.putString("PebblesCrateNBT", prize.nbt ?: "") }
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
                    crateName = crateName,
                    crateKey = currentCrateConfig.crateKey,
                    prize = updatedPrizes
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
        val itemNbt = itemStack.getOrCreateSubNbt("display")
        val loreNbt = NbtList()

        for (line in lore) {
            loreNbt.add(NbtString.of(Text.Serializer.toJson(line)))
        }

        itemNbt.put("Lore", loreNbt)
    }

    private fun updateOddsSumItem() {
        var totalWeight = BigDecimal.ZERO

        for (i in 0 until 9) {
            val stack = inventory.getStack(i)
            if (!stack.isEmpty) {
                val lore = stack.getSubNbt("display")?.getList("Lore", NbtElement.STRING_TYPE.toInt())
                if (lore != null && lore.size > 0) {
                    val line = Text.Serializer.fromJson(lore.getString(0))
                    val weight = BigDecimal(line?.string?.split(": ")?.get(1) ?: "0")
                    totalWeight = totalWeight.add(weight)
                }
            }
        }

        oddsSumItem.setCustomName(Text.of("Total Weight: $totalWeight"))
    }

    private fun getWeightFromLore(itemStack: ItemStack): BigDecimal {
        val lore = itemStack.getSubNbt("display")?.getList("Lore", NbtElement.STRING_TYPE.toInt())
        val line = Text.Serializer.fromJson(lore?.getString(0))
        return if (lore != null && lore.size > 0) BigDecimal(line?.string?.split(": ")?.get(1) ?: "0")
        else BigDecimal.ZERO
    }
}