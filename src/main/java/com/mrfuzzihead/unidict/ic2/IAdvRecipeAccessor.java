package com.mrfuzzihead.unidict.ic2;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

/**
 * Seam for {@code ic2.core.AdvRecipe}'s {@code public final output} field, realised on the class by
 * `AdvRecipeMixin` so {@code CraftingIntegration} can canonicalise IC2's shaped recipes (e.g.
 * 9× tiny-copper-dust → copper-dust, 9 ingots → block) in place. The field is {@code final} at the
 * Java level but the mutator writes it from inside the declaring class, which the JVM permits —
 * this is the only mechanism that reliably changes what NEI and {@code findMatchingRecipe} observe
 * (read-side {@code @Inject} return replacement has proven ineffective in the GTNH mixin runtime).
 */
public interface IAdvRecipeAccessor {

    @Nonnull
    ItemStack unidict$getOutput();

    void unidict$setOutput(@Nonnull ItemStack output);
}
