package com.mrfuzzihead.unidict.enderio;

import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * Deterministic, in-memory {@link IOreDictionaryPreferencesAccessor} for T2 tests — no MC statics, so
 * tests can drive {@code EIOIntegration.fixOreDictPreferences} (M7) without a live Ender IO install or
 * the applied mixin.
 *
 * <p>
 * Test-only fake, kept out of the mixin packages (docs/TestPlan.md rule 6). Wraps a caller-supplied
 * map so {@link #getPreferences()} mirrors the live field; {@code null} is allowed to exercise the
 * null-safe path in the integration.
 */
public final class FakeOreDictionaryPreferencesAccessor implements IOreDictionaryPreferencesAccessor {

    private final Map<String, ItemStack> preferences;

    public FakeOreDictionaryPreferencesAccessor(final Map<String, ItemStack> preferences) {
        this.preferences = preferences;
    }

    @Override
    public Map<String, ItemStack> getPreferences() {
        return preferences;
    }
}
