package tech.sethi.pebbles.crates.lootcrates

import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import tech.sethi.pebbles.crates.util.ParseableMessage
import tech.sethi.pebbles.crates.util.setLore

class CrateTransformer(val crateName: String, player: PlayerEntity) {

    val crateConfig = CrateConfigManager().getCrateConfig(crateName)
    val player = player

    private val crateItemStack = ItemStack(Items.PAPER)

    fun giveTransformer() {
        // create MutableText list with instructions
        val instructions = mutableListOf<Text>()

        // add instructions to list
        instructions.add(Text.literal("Right click a chest/enderchest to").formatted(Formatting.GOLD))
        instructions.add(Text.literal("transform it into a $crateName").formatted(Formatting.GOLD))
        setLore(crateItemStack, instructions)

        val nbt = crateItemStack.orCreateNbt
        nbt.putString("CrateName", crateName)
        crateItemStack.setCustomName(Text.literal(crateName))

        player.sendMessage(Text.literal("Giving $crateName to ${player.name.string}"), false)

        player.giveItemStack(crateItemStack)
        val message = "Successfully gave $crateName to ${player.name.string}"
        ParseableMessage(message, player as ServerPlayerEntity, "placeholder").send()
    }

    fun giveKey(amount: Int = 1, admin: PlayerEntity) {
        val materialIdentifier = Identifier.tryParse(crateConfig!!.crateKey.material)
        if (materialIdentifier != null) {
            val item = Registry.ITEM.get(materialIdentifier)
            if (item != Items.AIR) {
                val crateKeyItemStack = ItemStack(item, amount)
                val parsedName = ParseableMessage(
                    crateConfig.crateKey.name, player as ServerPlayerEntity, "placeholder"
                ).returnMessageAsStyledText()

                if (crateConfig.crateKey.nbt != null) {
                    val nbt: NbtCompound = NbtHelper.fromNbtProviderString(crateConfig.crateKey.nbt)
                    crateKeyItemStack.nbt = nbt
                }

                val nbt = crateKeyItemStack.orCreateNbt
                nbt.putString("CrateName", crateConfig.crateName)

                crateKeyItemStack.setCustomName(parsedName)
                // Set the lore for the crate key item
                val crateKeyLore = crateConfig.crateKey.lore
                val parsedCrateKeyLore = crateKeyLore.map {
                    ParseableMessage(it, player, "placeholder").returnMessageAsStyledText()
                }
                setLore(crateKeyItemStack, parsedCrateKeyLore)

                player.giveItemStack(crateKeyItemStack)

                val message = "You received $amount ${crateConfig.crateKey.name} for ${crateConfig.crateName}!"
                val adminMessage = "${player.name.string} received $amount ${crateConfig.crateKey.name} for ${crateConfig.crateName}!"
                ParseableMessage(message, admin as ServerPlayerEntity, "placeholder").send()
                ParseableMessage(message, player, "placeholder").send()
            }
        }
    }
}