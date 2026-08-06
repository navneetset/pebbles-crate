package tech.sethi.pebbles.crates.lootcrates

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.util.NbtItemUtil
import tech.sethi.pebbles.crates.util.ParseableMessage
import tech.sethi.pebbles.crates.util.setLore

class CrateTransformer(val crateName: String, val player: PlayerEntity) {

    val crateConfig = CrateConfigManager.getCrateConfig(crateName)

    private val crateItemStack = ItemStack(Items.PAPER)

    fun giveTransformer() {
        setLore(crateItemStack, Messages.list("crate.transformer.lore", "crate_name" to crateName))

        val nbt = NbtComponent.of(NbtCompound().apply {
            putString("CrateName", crateName)
        })
        crateItemStack.set(DataComponentTypes.CUSTOM_DATA, nbt)
        crateItemStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(crateName))

        player.giveItemStack(crateItemStack)
        Messages.send(
            player, "crate.transformer.given", "crate_name" to crateName, "player_name" to player.name.string
        )
    }

    /**
     * Returns false when no key could be minted - an unknown crate, or one whose `crateKey.material`
     * names an item the game does not have. Callers report that instead of claiming a key was given.
     */
    fun giveKey(amount: Int = 1, admin: PlayerEntity): Boolean {
        if (crateConfig == null) {
            Messages.send(admin, "command.crate-unknown", "crate_name" to crateName)
            return false
        }

        val materialIdentifier = Identifier.tryParse(crateConfig.crateKey.material) ?: return false
        val item = Registries.ITEM.get(materialIdentifier)
        if (item == Items.AIR) return false

        // Built once at count one and copied per stack below, so the count the config's nbt carries
        // never decides how many keys the player ends up with.
        val crateKeyItemStack = NbtItemUtil.applyNbt(
            ItemStack(item, 1), crateConfig.crateKey.nbt, 1, "crate key of crate '${crateConfig.crateName}'"
        )
        if (crateKeyItemStack.isEmpty) return false

        val nbtCompound = crateKeyItemStack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.apply {
            putString("CrateName", crateConfig.crateName)
        } ?: NbtCompound().apply {
            putString("CrateName", crateConfig.crateName)
        }

        crateKeyItemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound))

        crateKeyItemStack.set(
            DataComponentTypes.CUSTOM_NAME,
            ParseableMessage(crateConfig.crateKey.name, player as ServerPlayerEntity).returnMessageAsStyledText()
        )
        // Set the lore for the crate key item
        val crateKeyLore = crateConfig.crateKey.lore
        val parsedCrateKeyLore = crateKeyLore.map {
            ParseableMessage(it, player).returnMessageAsStyledText()
        }
        setLore(crateKeyItemStack, parsedCrateKeyLore)

        // A key item may well stack to 16, or to 1; a single oversized stack would be dropped.
        var remaining = amount
        while (remaining > 0) {
            val stackSize = remaining.coerceAtMost(crateKeyItemStack.maxCount)
            player.inventory.offerOrDrop(crateKeyItemStack.copyWithCount(stackSize))
            remaining -= stackSize
        }

        Messages.send(
            player,
            "key.received-physical",
            "amount" to "$amount",
            "key_name" to crateConfig.crateKey.name,
            "crate_name" to crateConfig.crateName
        )
        return true
    }
}