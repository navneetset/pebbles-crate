package tech.sethi.pebbles.crates.impl

import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.item.ItemStack
import net.minecraft.util.math.AffineTransformation

/**
 * Opens up the protected setters of vanilla's display entities so the roll animation can swap the
 * shown item. Implemented by mixins on `DisplayEntity` and `DisplayEntity.ItemDisplayEntity`.
 */
interface ItemDisplayEntityImpl {
    fun `pebbles_crates$publicSetStack`(stack: ItemStack?)
    fun `pebbles_crates$publicSetTransformation`(transformation: AffineTransformation)
    fun `pebbles_crates$publicSetTransformationMode`(mode: ModelTransformationMode)
}
