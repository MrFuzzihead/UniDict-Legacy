package com.mrfuzzihead.unidict.te;

import java.util.Map;

/**
 * T2 seam over Thermal Expansion's {@code FurnaceManager.recipeMap} (docs/PLAN.md §M7 #4 and the
 * mixin summary table). Pattern from docs/PLAN.md §0 rule 1: a plain interface whose live
 * implementation is {@code FurnaceManagerMixin} (mixins.late) and whose T2 fake
 * ({@code FakeFurnaceManagerAccessor}) lives in {@code src/test}.
 *
 * <p>
 * Keys/values are Thermal Expansion's mod classes ({@code FurnaceManager.ComparableItemStackFurnace}
 * / {@code FurnaceManager.RecipeFurnace}), which are not on the JUnit test classpath (TE is a
 * {@code compileOnly} dependency). The accessor is therefore declared with a <em>raw</em>
 * {@link Map} — the erased descriptor {@code java.util.Map} matches the field exactly (its declared
 * type is {@code Map<..., ...>}), so the mixin resolves it while a test fake can return any map
 * without referencing TE. {@code TEIntegration} casts to the typed map when it iterates.
 *
 * <p>
 * {@code recipeMap} is a {@code private static} TE-added member, so the mixin targets it with
 * {@code remap = false}.
 */
public interface IFurnaceManagerAccessor {

    /** TE's live redstone-furnace recipe map (may be {@code null} before load). */
    @SuppressWarnings("rawtypes")
    Map getRecipeMap();
}
