package tech.sethi.pebbles.crates.util

import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.text.MutableText
import net.minecraft.text.Text

 fun setLore(itemStack: ItemStack, lore: List<Text>) {
    val itemNbt = itemStack.getOrCreateSubNbt("display")
    val loreNbt = NbtList()

    for (line in lore) {
        loreNbt.add(NbtString.of(Text.Serializer.toJson(line)))
    }

    itemNbt.put("Lore", loreNbt)
}