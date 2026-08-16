package com.mrfuzzihead.unidict.forestry;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over Forestry's <em>supported</em> carpenter-recipe-addition path — {@code RecipeManagers
 * .carpenterManager.addRecipe(...)} ({@code forestry.api.recipes.ICarpenterManager}). Upstream
 * UniDict instead reflected into {@code CarpenterRecipeManager.recipes} (a {@code Collections
 * .unmodifiableSet} wrapper in this ForestryMC version) and called {@code Set.add(...)} directly,
 * which is fragile and version-specific. Forestry's public {@code addRecipe} is the stable, additive,
 * non-destructive API (BB-3: a recipe is only ever <em>added</em>, never removed, replaced, or
 * registered globally).
 *
 * <p>
 * Two shapes are needed and both are expressed <em>neutrally</em> (no Forestry types on the test
 * classpath): a single-slot grid (broken-tool recycling and crate *uncrating*) and a full 3x3 grid
 * (crate *crating*). The production implementation is ME-backing {@link
 * com.mrfuzzihead.unidict.integration.ForestryIntegration#CARPENTER_ADDER}; T2 tests drive the pure
 * {@code ForestryIntegration#addBronzeRecycling} / {@code ForestryIntegration#addCrateRecipes} seams
 * through {@link com.mrfuzzihead.unidict.forestry.FakeCarpenterRecipeAdder}.
 */
public interface ICarpenterRecipeAdder {

    /**
     * Adds a one-slot carpenter grid recipe: {@code ingredient} -&gt; {@code product} (stack size is
     * baked into {@code product} by the caller).
     *
     * @return {@code true} if the recipe was added (drives the callers' decision counters; the live
     *         impl always returns {@code true} because {@code ICarpenterManager.addRecipe} is void).
     */
    boolean addSingleRecipe(ItemStack product, Object ingredient);

    /**
     * Adds a full 3x3 carpenter grid recipe: nine of {@code ingredient} (an Ore-Dictionary name or a
     * stack) -&gt; {@code product} (crating).
     *
     * @return {@code true} if the recipe was added.
     */
    boolean addGridRecipe(ItemStack product, Object ingredient);
}
