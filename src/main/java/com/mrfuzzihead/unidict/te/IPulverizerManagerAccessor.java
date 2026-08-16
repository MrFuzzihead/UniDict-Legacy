package com.mrfuzzihead.unidict.te;

import java.util.Map;

/**
 * T2 seam over Thermal Expansion's {@code PulverizerManager.recipeMap} (docs/PLAN.md §M7 #4).
 * Pattern from docs/PLAN.md §0 rule 1: a plain interface whose live implementation is
 * {@code PulverizerManagerMixin} (mixins.late) and whose T2 fake
 * ({@code FakePulverizerManagerAccessor}) lives in {@code src/test}.
 *
 * <p>
 * Keys/values are TE mod classes not on the JUnit test classpath, so the accessor is declared with a
 * <em>raw</em> {@link Map} (matching the field's exact erased descriptor {@code java.util.Map});
 * {@code TEIntegration} casts to the typed map when it iterates.
 */
public interface IPulverizerManagerAccessor {

    /** TE's live pulverizer recipe map (may be {@code null} before load). */
    @SuppressWarnings("rawtypes")
    Map getRecipeMap();
}
