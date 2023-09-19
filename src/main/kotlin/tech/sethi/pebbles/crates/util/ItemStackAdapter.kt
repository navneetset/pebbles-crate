import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtHelper
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import com.google.gson.*
import net.minecraft.registry.Registries
import java.lang.reflect.Type

data class ItemConfig(
    val itemId: String, val nbt: String?, val amount: Int, val displayName: String?
) {
    fun toItemStack(): ItemStack {
        val item = Registries.ITEM.get(Identifier(itemId))
        val itemStack = ItemStack(item, amount)

        // Apply NBT data if present
        if (nbt != null) {
            val nbtCompound = NbtHelper.fromNbtProviderString(nbt)
            itemStack.nbt = nbtCompound
        }

        if (displayName != null) {
            itemStack.setCustomName(Text.Serializer.fromJson(displayName))
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
        if (itemStack.hasCustomName()) {
            jsonObject.addProperty("displayName", itemStack.name.toString())
        }
        // Save NBT data
        if (itemStack.hasNbt()) {
            jsonObject.addProperty("nbt", itemStack.nbt.toString())
        }
        if (itemStack.hasNbt() && itemStack.nbt!!.contains("display") && itemStack.nbt!!.getCompound("display")
                .contains("Lore")
        ) {
            val loreJsonArray = JsonArray()
            val loreNbtList = itemStack.nbt!!.getCompound("display").getList("Lore", 8)
            for (i in 0 until loreNbtList.size) {
                loreJsonArray.add(loreNbtList.getString(i))
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
