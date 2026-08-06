package tech.sethi.pebbles.crates.util

import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.component.ComponentChanges
import net.minecraft.datafixer.TypeReferences
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.StringNbtReader
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.PebblesCrate

/**
 * Single home for the legacy-vs-modern `nbt` string handling used by crate configs.
 *
 * Configs written before 1.20.5 store a bare `tag` compound (`{display:{Name:...}}`), configs
 * written after it store component changes (`{"minecraft:custom_name":...}`). Both must keep
 * working, so the compound is sniffed: any key that is not a namespaced identifier means the
 * string is legacy and gets run through the DataFixer from 1.20.4 (data version 3700).
 */
object NbtItemUtil {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    private val NAMESPACED_KEY_PATTERN = Regex("^[a-z0-9_.-]+:[a-z0-9_/.-]+$")

    /**
     * Applies a config `nbt` string to [itemStack] and returns the resulting stack. The legacy path
     * rebuilds the stack from scratch, so always use the returned value.
     *
     * [source] only appears in warning messages, to point users at the offending config.
     */
    fun applyNbt(itemStack: ItemStack, nbt: String?, amount: Int, source: String): ItemStack {
        if (nbt.isNullOrBlank() || nbt == "{}" || nbt == "null") return itemStack

        val parsedNbt = try {
            StringNbtReader.parse(nbt)
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] Malformed nbt for $source, ignoring it: ${e.message}")
            return itemStack
        }

        val isLegacy = parsedNbt.keys.any { !NAMESPACED_KEY_PATTERN.matches(it) }
        if (isLegacy) {
            val legacyNbt = NbtCompound().apply {
                putString("id", itemStack.registryEntry.idAsString)
                putInt("Count", amount)
                put("tag", parsedNbt)
            }

            val updatedNbt = PebblesCrate.server?.dataFixer?.update(
                TypeReferences.ITEM_STACK,
                Dynamic(PebblesCrate.nbtOps, legacyNbt),
                3700,
                SharedConstants.getGameVersion().saveVersion.id
            )?.value

            if (updatedNbt == null) {
                logger.warn("[Pebbles-Crates] Could not upgrade legacy nbt for $source, ignoring it")
                return itemStack
            }

            // Falling back to ItemStack.EMPTY here would hand the game-wide empty singleton to
            // callers that go on to set a name and lore on it.
            val upgraded = ItemStack.CODEC.parse(PebblesCrate.nbtOps, updatedNbt).result().orElse(null)
            if (upgraded == null || upgraded.isEmpty) {
                logger.warn("[Pebbles-Crates] Legacy nbt could not be applied to $source - item may be unresolvable, ignoring it")
                return itemStack
            }

            return upgraded
        }

        val updatedNbt = ComponentChanges.CODEC.parse(PebblesCrate.nbtOps, parsedNbt).result().orElse(null)
        if (updatedNbt == null) {
            logger.warn("[Pebbles-Crates] Could not read component nbt for $source, ignoring it")
            return itemStack
        }
        itemStack.applyChanges(updatedNbt)
        itemStack.count = amount
        return itemStack
    }
}
