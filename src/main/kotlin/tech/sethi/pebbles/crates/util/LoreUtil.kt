package tech.sethi.pebbles.crates.util

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.text.MutableText
import net.minecraft.text.Text

fun setLore(itemStack: ItemStack, lore: List<Text>) {
    val loreComponent = LoreComponent(lore)
    itemStack.set(DataComponentTypes.LORE, loreComponent)
}