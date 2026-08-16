package com.mrfuzzihead.unidict.ic2;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

/**
 * Seam for {link ic2.core.AdvShapelessRecipe}'s {@code public} (non-final) {@code output} field, realised
 * on the class by `AdvShapelessRecipeMixin` so `CraftingIntegration` can canonicalise IC2's shapeless
 * recipes (e.g. tiny-copper-dust → copper-dust) in place, non-destructively (BB-3).
 */
public interface IAdvShapelessRecipeAccessor {

    @Nonnull
    ItemStack unidict$getOutput();

    void unidict$setOutput(@Nonnull ItemStack output);
}
