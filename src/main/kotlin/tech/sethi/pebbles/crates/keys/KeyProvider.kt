package tech.sethi.pebbles.crates.keys

import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.lootcrates.CrateConfig

/**
 * Where a crate's key comes from. Both implementations answer about the same crate identity - the
 * `crateName`, never the screen name - so a key granted by any route opens the crate it names.
 */
interface KeyProvider {
    fun hasKey(player: ServerPlayerEntity, crate: CrateConfig): Boolean

    /** Takes exactly one key. Returns false without changing anything when there was none to take. */
    fun consumeKey(player: ServerPlayerEntity, crate: CrateConfig): Boolean

    /** Shown when [hasKey] said no, so each provider can explain itself. */
    fun missingKeyMessage(player: ServerPlayerEntity, crate: CrateConfig): Text
}

/** The behaviour crates have always had: the right item, named for this crate, in the main hand. */
object PhysicalKeyProvider : KeyProvider {
    override fun hasKey(player: ServerPlayerEntity, crate: CrateConfig): Boolean = matches(player.mainHandStack, crate)

    override fun consumeKey(player: ServerPlayerEntity, crate: CrateConfig): Boolean {
        val held = player.mainHandStack
        if (!matches(held, crate)) return false
        held.decrement(1)
        return true
    }

    override fun missingKeyMessage(player: ServerPlayerEntity, crate: CrateConfig): Text =
        Messages.text("key.need-physical", "key_name" to crate.crateKey.name, "crate_name" to crate.crateName)

    /**
     * A key is the crate's configured item carrying this crate's name in custom data. Also used by
     * `/padmin convertkeys`, which needs the same answer for stacks outside the main hand.
     */
    fun matches(stack: ItemStack, crate: CrateConfig): Boolean {
        if (stack.isEmpty) return false

        val keyItem = Registries.ITEM.get(Identifier.tryParse(crate.crateKey.material))
        if (stack.item != keyItem) return false

        val customData = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: return false
        return customData.getString("CrateName") == crate.crateName
    }
}

/** Keys held in the player's wallet rather than their inventory. */
object VirtualKeyProvider : KeyProvider {
    override fun hasKey(player: ServerPlayerEntity, crate: CrateConfig): Boolean =
        KeyManager.isReady(player.uuid) && KeyManager.getBalance(player.uuid, crate.crateName) > 0

    override fun consumeKey(player: ServerPlayerEntity, crate: CrateConfig): Boolean =
        KeyManager.isReady(player.uuid) && KeyManager.consume(player.uuid, crate.crateName)

    override fun missingKeyMessage(player: ServerPlayerEntity, crate: CrateConfig): Text {
        if (!KeyManager.isReady(player.uuid)) return Messages.text("key.loading")
        return Messages.text("key.none-virtual", "crate_name" to crate.crateName)
    }
}

object KeyProviders {
    fun forCrate(crate: CrateConfig): KeyProvider =
        if (KeyManager.isVirtual(crate)) VirtualKeyProvider else PhysicalKeyProvider
}
