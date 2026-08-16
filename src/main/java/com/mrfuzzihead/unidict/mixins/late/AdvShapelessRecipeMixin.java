package com.mrfuzzihead.unidict.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.ic2.IAdvShapelessRecipeAccessor;

/**
 * Accessor mixin for {@code ic2.core.AdvShapelessRecipe}, exposing its {@code public} (non-final)
 * {@code output} field so {@code CraftingIntegration} can canonicalise IC2's shapeless recipes (e.g.
 * tiny-copper-dust → copper-dust) in place — non-destructive (BB-3). Because the field is mutable,
 * this is the reliable path (vs. {@link AdvRecipeMixin}, whose {@code output} is {@code final} and so
 * is handled best-effort via a {@code getRecipeOutput} read-inject). Mod-gated (IC2).
 */
@Mixin(value = ic2.core.AdvShapelessRecipe.class)
public abstract class AdvShapelessRecipeMixin implements IAdvShapelessRecipeAccessor {

    @Accessor(value = "output", remap = false)
    protected abstract ItemStack accessor$getOutput();

    @Accessor(value = "output", remap = false)
    protected abstract void accessor$setOutput(ItemStack output);

    @Override
    public ItemStack unidict$getOutput() {
        return accessor$getOutput();
    }

    @Override
    public void unidict$setOutput(final ItemStack output) {
        accessor$setOutput(output);
    }
}
