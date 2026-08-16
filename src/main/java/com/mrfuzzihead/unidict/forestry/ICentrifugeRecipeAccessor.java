package com.mrfuzzihead.unidict.forestry;

import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over {@code forestry.factory.recipes.CentrifugeRecipe#outputs} — the <b>private final</b>
 * {@code Map<ItemStack, Float>} of product → chance. {@code getProducts(Random)} (the machine's actual
 * output roller) reads this exact map, so rewriting its <em>contents</em> in place (clear + putAll the
 * canonical keys) is all a centrifuge output rewrite is — no recipe is ever removed (BB-3).
 *
 * <p>
 * NB: {@code getAllProducts()} returns {@code ImmutableMap.copyOf(outputs)} — a fresh immutable wrapper,
 * useless for mutation. It is precisely because the <em>underlying</em> map is still the live, mutable
 * field here that the in-place rewrite is possible at all.
 *
 * <p>
 * Live implementation is {@code CentrifugeRecipeMixin} ({@code mixins.late},
 * {@code @Accessor("outputs")}, {@code remap=false}: a Forestry mod field keeps its name in dev and SRG).
 * T2 tests drive the pure {@code ForestryIntegration#rewriteCentrifugeProducts} map seam directly, so no
 * Forestry types reach the JUnit classpath.
 */
public interface ICentrifugeRecipeAccessor {

    /** @return the live product map, mutable in place (null for a null-backed field, never expected) */
    Map<ItemStack, Float> unidict$getProducts();
}
