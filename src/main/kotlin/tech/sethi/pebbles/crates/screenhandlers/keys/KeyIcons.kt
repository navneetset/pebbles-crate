package tech.sethi.pebbles.crates.screenhandlers.keys

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.util.ParseableName
import tech.sethi.pebbles.crates.util.setLore
import java.util.UUID

/** The stacks the key screens are built from, so all three of them look and behave the same. */
object KeyIcons {
    /**
     * How a GUI icon remembers which crate it stands for. Deliberately *not* the `CrateName` a real
     * key carries: an icon that somehow escaped a screen would otherwise be a working key. Reading
     * the crate back out of here also means a crate whose display name is styled or duplicated still
     * resolves to exactly one config.
     */
    private const val CRATE_TAG = "PebblesKeyCrate"

    /** Marks a button so its slot index does not have to be trusted. */
    private const val ACTION_TAG = "PebblesKeyAction"

    /** Which player a head stands for, so selecting one never depends on page-offset arithmetic. */
    private const val PLAYER_TAG = "PebblesKeyPlayer"

    /**
     * The item a crate's key is made of, falling back to the configured default whenever the crate's
     * own material does not resolve - which includes a wallet that outlived the crate it was filled
     * for, since an unknown crate still has to be visible on the screen.
     */
    fun keyMaterial(crateConfig: CrateConfig?): Item {
        val configured = crateConfig?.let { Registries.ITEM.get(Identifier.tryParse(it.crateKey.material)) }
        if (configured != null && configured != Items.AIR) return configured

        val fallback = Registries.ITEM.get(Identifier.tryParse(GlobalConfigManager.crate.defaultKeyMaterial))
        return if (fallback != Items.AIR) fallback else Items.TRIPWIRE_HOOK
    }

    fun keyIcon(crateName: String, amount: Int, lore: List<Text> = emptyList()): ItemStack {
        val crateConfig = CrateConfigManager.getCrateConfig(crateName)

        val stack = ItemStack(keyMaterial(crateConfig), amount.coerceIn(1, 64))
        stack.set(
            DataComponentTypes.CUSTOM_NAME,
            ParseableName(crateConfig?.crateKey?.name ?: crateName).returnMessageAsStyledText()
        )

        val fullLore = mutableListOf(
            Messages.text("gui.keys.icon.crate", "crate_name" to crateName),
            Messages.text("gui.keys.icon.amount", "amount" to "$amount")
        )
        if (crateConfig == null) {
            fullLore.add(Messages.text("gui.keys.icon.unknown-crate"))
        }
        fullLore.addAll(lore)
        setLore(stack, fullLore)

        tag(stack, CRATE_TAG, crateName)
        return stack
    }

    /** The crate an icon stands for, or null if the stack is not one of ours. */
    fun crateOf(stack: ItemStack): String? {
        val nbt = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: return null
        return nbt.getString(CRATE_TAG).takeIf { it.isNotEmpty() }
    }

    fun button(item: Item, name: Text, lore: List<Text> = emptyList(), action: String? = null): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponentTypes.CUSTOM_NAME, name)
        if (lore.isNotEmpty()) setLore(stack, lore)
        if (action != null) tag(stack, ACTION_TAG, action)
        return stack
    }

    fun actionOf(stack: ItemStack): String? {
        val nbt = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: return null
        return nbt.getString(ACTION_TAG).takeIf { it.isNotEmpty() }
    }

    /**
     * A head that actually renders the player's skin: 1.21 reads the texture from the PROFILE
     * component, so the `SkullOwner` string the donor screen wrote showed as a blank steve head.
     */
    fun playerHead(player: ServerPlayerEntity, lore: List<Text> = emptyList()): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(DataComponentTypes.PROFILE, ProfileComponent(player.gameProfile))
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(player.name.string).formatted(Formatting.AQUA))
        if (lore.isNotEmpty()) setLore(stack, lore)
        tag(stack, PLAYER_TAG, player.uuidAsString)
        return stack
    }

    /** The player a head stands for, or null if the stack is not one of ours. */
    fun playerOf(stack: ItemStack): UUID? {
        val nbt = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: return null
        val uuid = nbt.getString(PLAYER_TAG).takeIf { it.isNotEmpty() } ?: return null
        return try {
            UUID.fromString(uuid)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun filler(): ItemStack = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
        set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "))
    }

    private fun tag(stack: ItemStack, key: String, value: String) {
        val nbt = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: NbtCompound()
        nbt.putString(key, value)
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
    }
}
