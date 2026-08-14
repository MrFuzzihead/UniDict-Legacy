package com.mrfuzzihead.unidict.railcraft;

import java.util.List;

/**
 * T2 seam over Railcraft's {@code BlastFurnaceCraftingManager.recipes} list (docs/PLAN.md §M7 #3 and
 * the mixin summary table). Pattern from docs/PLAN.md §0 rule 1: a plain interface whose live
 * implementation is {@code BlastFurnaceCraftingManagerMixin} (mixins.late) and whose T2 fake
 * ({@code FakeBlastFurnaceCraftingManagerAccessor}) lives in {@code src/test}.
 *
 * <p>
 * The element type is Railcraft's mod class {@code BlastFurnaceCraftingManager.BlastFurnaceRecipe},
 * which is not on the JUnit test classpath (Railcraft is a {@code compileOnly} dependency). The
 * accessor is therefore declared with a <em>raw</em> {@link List}{@code <BlastFurnaceRecipe>} — the
 * erased descriptor {@code java.util.List} matches the field exactly, so the mixin resolves it, while
 * a test fake can return any {@code List} without referencing Railcraft. {@code RailcraftIntegration}
 * casts to the typed list when it iterates.
 *
 * <p>
 * {@code recipes} is a Railcraft-added member, so the mixin targets it with {@code remap = false}.
 */
public interface IBlastFurnaceCraftingManagerAccessor {

    /** Railcraft's live blast-furnace recipe list (may be {@code null} before load). */
    @SuppressWarnings("rawtypes")
    List getRecipes();
}
