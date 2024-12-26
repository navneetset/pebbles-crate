import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtHelper
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import com.google.gson.*
import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.component.ComponentChanges
import net.minecraft.component.DataComponentTypes
import net.minecraft.datafixer.TypeReferences
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.PebblesCrate.server
import java.lang.reflect.Type

data class ItemConfig(
    val itemId: String, val nbt: String?, val amount: Int, val displayName: String?
) {
    fun toItemStack(): ItemStack {
        val item = Registries.ITEM.get(Identifier.of(itemId))
        var itemStack = ItemStack(item, amount)

        // Apply NBT data if present
        if (nbt != null) {
            val parsedNbt = StringNbtReader.parse(nbt)

            val namespacedKeyPattern = Regex("^[a-z0-9_.-]+:[a-z0-9_/.-]+$")

            val isLegacy = parsedNbt.keys.any { !namespacedKeyPattern.matches(it) }
            if (isLegacy) {
                val legacyNbt = NbtCompound().apply {
                    putString("id", itemStack.registryEntry.idAsString)
                    putInt("Count", amount)
                    put("tag", parsedNbt)
                }

                val updatedNbt = server?.dataFixer?.update(
                    TypeReferences.ITEM_STACK,
                    Dynamic(PebblesCrate.nbtOps, legacyNbt),
                    3700,
                    SharedConstants.getGameVersion().saveVersion.id
                )?.value

                itemStack = ItemStack.CODEC.parse(PebblesCrate.nbtOps, updatedNbt).result().orElse(ItemStack.EMPTY)
            } else {
                val updatedNbt =
                    ComponentChanges.CODEC.parse(PebblesCrate.nbtOps, StringNbtReader.parse(nbt)).result()
                        .orElse(null)
                itemStack.applyChanges(updatedNbt)
                itemStack.count = amount
            }
        }

        if (displayName != null) {
            itemStack.set(DataComponentTypes.CUSTOM_NAME, Text.Serialization.fromJson(displayName, DynamicRegistryManager.EMPTY))
        }

        return itemStack
    }
}


class ItemStackTypeAdapter : JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {
    override fun serialize(
        itemStack: ItemStack, type: Type, jsonSerializationContext: JsonSerializationContext
    ): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("itemId", Registries.ITEM.getId(itemStack.item).toString())
        jsonObject.addProperty("amount", itemStack.count)
        if (itemStack.get(DataComponentTypes.CUSTOM_NAME) != null) {
            jsonObject.addProperty("displayName", itemStack.name.string)
        }
        // Save NBT data
        if (itemStack.componentChanges.size() > 0) {
            val nbtString = ComponentChanges.CODEC.encodeStart(PebblesCrate.nbtOps, itemStack.componentChanges).result().orElse(null)
            jsonObject.addProperty("nbt", nbtString.asString())
        }
        if (itemStack.componentChanges.size() > 0 && itemStack.get(DataComponentTypes.LORE)?.lines?.isNotEmpty() == true
        ) {
            val loreJsonArray = JsonArray()
            val loreNbtList = itemStack.get(DataComponentTypes.LORE)?.lines ?: return jsonObject
            for (i in 0 until loreNbtList.size) {
                loreJsonArray.add(loreNbtList[i].string)
            }
            jsonObject.add("lore", loreJsonArray)
        }
        return jsonObject
    }

    override fun deserialize(
        jsonElement: JsonElement, type: Type, jsonDeserializationContext: JsonDeserializationContext
    ): ItemStack {
        val jsonObject = jsonElement.asJsonObject
        val itemId = jsonObject.get("itemId").asString
        val amount = jsonObject.get("amount").asInt
        val displayName = if (jsonObject.has("displayName")) jsonObject.get("displayName").asString else null
        val nbt = if (jsonObject.has("nbt")) jsonObject.get("nbt").asString else null
        val itemConfig = ItemConfig(itemId, nbt, amount, displayName)
        return itemConfig.toItemStack()
    }
}
