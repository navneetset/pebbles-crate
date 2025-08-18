package tech.sethi.pebbles.crates.lootcrates

import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.component.ComponentChanges
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.datafixer.TypeReferences
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.PebblesCrate.server
import tech.sethi.pebbles.crates.util.ParseableMessage
import tech.sethi.pebbles.crates.util.setLore

class CrateTransformer(val crateName: String, val player: PlayerEntity) {

    val crateConfig = CrateConfigManager.getCrateConfig(crateName)

    private val crateItemStack = ItemStack(Items.PAPER)

    fun giveTransformer() {
        // create MutableText list with instructions
        val instructions = mutableListOf<Text>()

        // add instructions to list
        instructions.add(Text.literal("Right click a chest/enderchest to").formatted(Formatting.GOLD))
        instructions.add(Text.literal("transform it into a $crateName").formatted(Formatting.GOLD))
        setLore(crateItemStack, instructions)

        val nbt = NbtComponent.of(NbtCompound().apply {
            putString("CrateName", crateName)
        })
        crateItemStack.set(DataComponentTypes.CUSTOM_DATA, nbt)
        crateItemStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(crateName))

        player.sendMessage(Text.literal("Giving $crateName to ${player.name.string}"), false)

        server?.execute { ->
            player.giveItemStack(crateItemStack)
        }

        val message = "Successfully gave $crateName to ${player.name.string}"
        ParseableMessage(message, player as ServerPlayerEntity, "placeholder").send()
    }

    fun giveKey(amount: Int = 1, admin: PlayerEntity) {
        val materialIdentifier = Identifier.tryParse(crateConfig!!.crateKey.material)
        if (materialIdentifier != null) {
            val item = Registries.ITEM.get(materialIdentifier)
            if (item != Items.AIR) {
                var crateKeyItemStack = ItemStack(item, amount)
                val parsedName = ParseableMessage(
                    crateConfig.crateKey.name, player as ServerPlayerEntity, "placeholder"
                ).returnMessageAsStyledText()

                if (!crateConfig.crateKey.nbt.isNullOrEmpty() && crateConfig.crateKey.nbt != "{}") {
                    val parsedNbt = StringNbtReader.parse(crateConfig.crateKey.nbt)

                    val namespacedKeyPattern = Regex("^[a-z0-9_.-]+:[a-z0-9_/.-]+$")

                    val isLegacy = parsedNbt.keys.any { !namespacedKeyPattern.matches(it) }
                    if (isLegacy) {
                        val legacyNbt = NbtCompound().apply {
                            putString("id", crateKeyItemStack.registryEntry.idAsString)
                            putInt("Count", amount)
                            put("tag", parsedNbt)
                        }

                        val updatedNbt = server?.dataFixer?.update(
                            TypeReferences.ITEM_STACK,
                            Dynamic(PebblesCrate.nbtOps, legacyNbt),
                            3700,
                            SharedConstants.getGameVersion().saveVersion.id
                        )?.value

                        crateKeyItemStack = ItemStack.CODEC.parse(PebblesCrate.nbtOps, updatedNbt).result().orElse(ItemStack.EMPTY)
                    } else {
                        val updatedNbt =
                            ComponentChanges.CODEC.parse(PebblesCrate.nbtOps, StringNbtReader.parse(crateConfig.crateKey.nbt)).result()
                                .orElse(null)
                        crateKeyItemStack.applyChanges(updatedNbt)
                        crateKeyItemStack.count = amount
                    }
                }

                val nbtCompound = crateKeyItemStack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.apply {
                    putString("CrateName", crateConfig.crateName)
                } ?: NbtCompound().apply {
                    putString("CrateName", crateConfig.crateName)
                }

                crateKeyItemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound))

                crateKeyItemStack.set(DataComponentTypes.CUSTOM_NAME, parsedName)
                // Set the lore for the crate key item
                val crateKeyLore = crateConfig.crateKey.lore
                val parsedCrateKeyLore = crateKeyLore.map {
                    ParseableMessage(it, player, "placeholder").returnMessageAsStyledText()
                }
                setLore(crateKeyItemStack, parsedCrateKeyLore)

                server?.execute { ->
                    player.inventory.offerOrDrop(crateKeyItemStack)
                }

                val message = "You received $amount ${crateConfig.crateKey.name} for ${crateConfig.crateName}!"
                ParseableMessage(message, player, "placeholder").send()
            }
        }
    }
}