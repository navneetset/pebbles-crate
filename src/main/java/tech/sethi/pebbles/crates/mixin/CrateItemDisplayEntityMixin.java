package tech.sethi.pebbles.crates.mixin;

import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import tech.sethi.pebbles.crates.impl.ItemDisplayEntityImpl;

@Mixin(DisplayEntity.ItemDisplayEntity.class)
public abstract class CrateItemDisplayEntityMixin implements ItemDisplayEntityImpl {

    @Shadow
    abstract void setItemStack(ItemStack stack);

    @Shadow
    protected abstract void setTransformationMode(ModelTransformationMode mode);

    @Override
    public void pebbles_crates$publicSetStack(ItemStack stack) {
        setItemStack(stack);
    }

    @Override
    public void pebbles_crates$publicSetTransformationMode(@NotNull ModelTransformationMode mode) {
        setTransformationMode(mode);
    }
}
