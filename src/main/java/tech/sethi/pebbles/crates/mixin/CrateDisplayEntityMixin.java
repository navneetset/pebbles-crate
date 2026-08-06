package tech.sethi.pebbles.crates.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import tech.sethi.pebbles.crates.impl.ItemDisplayEntityImpl;

@Mixin(DisplayEntity.class)
public abstract class CrateDisplayEntityMixin implements ItemDisplayEntityImpl {

    @Shadow
    protected abstract void setTransformation(AffineTransformation transformation);

    @Override
    public void pebbles_crates$publicSetTransformation(@NotNull AffineTransformation transformation) {
        setTransformation(transformation);
    }
}
