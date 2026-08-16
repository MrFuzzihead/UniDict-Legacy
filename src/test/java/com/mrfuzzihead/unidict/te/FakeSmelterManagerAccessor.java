package com.mrfuzzihead.unidict.te;

import java.util.Map;

/**
 * Deterministic, in-memory {@link ISmelterManagerAccessor} for T2 tests — no MC statics, so tests can
 * drive the generic {@code TEIntegration.rewriteOutputs} seam (M7) with a fabricated recipe map without
 * a live Thermal Expansion install or the applied mixin.
 *
 * <p>
 * Test-only fake, kept out of the mixin packages (docs/TestPlan.md rule 6). The accessor is raw-typed
 * (TE's {@code ComparableItemStackSmelter}/{@code RecipeSmelter} are not on the JUnit test classpath), so
 * this fake holds a raw {@link Map}; {@code null} is allowed to exercise the null-safe path.
 */
public final class FakeSmelterManagerAccessor implements ISmelterManagerAccessor {

    private final Map recipeMap;

    @SuppressWarnings("rawtypes")
    public FakeSmelterManagerAccessor(final Map recipeMap) {
        this.recipeMap = recipeMap;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getRecipeMap() {
        return recipeMap;
    }
}
