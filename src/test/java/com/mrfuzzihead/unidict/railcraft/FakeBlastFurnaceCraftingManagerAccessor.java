package com.mrfuzzihead.unidict.railcraft;

import java.util.List;

/**
 * Deterministic, in-memory {@link IBlastFurnaceCraftingManagerAccessor} for T2 tests — no MC statics, so
 * tests can drive the generic {@code RailcraftIntegration.rewriteRecipes} seam (M7) with a fabricated
 * recipe list without a live Railcraft install or the applied mixin.
 *
 * <p>
 * Test-only fake, kept out of the mixin packages (docs/TestPlan.md rule 6). The accessor is raw-typed
 * (Railcraft's {@code BlastFurnaceRecipe} is not on the JUnit test classpath), so this fake holds a raw
 * {@link List}; {@code null} is allowed to exercise the null-safe path.
 */
public final class FakeBlastFurnaceCraftingManagerAccessor implements IBlastFurnaceCraftingManagerAccessor {

    private final List recipes;

    @SuppressWarnings("rawtypes")
    public FakeBlastFurnaceCraftingManagerAccessor(final List recipes) {
        this.recipes = recipes;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getRecipes() {
        return recipes;
    }
}
