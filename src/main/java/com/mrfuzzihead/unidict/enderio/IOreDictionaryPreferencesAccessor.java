package com.mrfuzzihead.unidict.enderio;

import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over Ender IO's {@code OreDictionaryPreferences.preferences} map (docs/PLAN.md §M7 #1 and
 * the mixin summary table). This is the pattern from docs/PLAN.md §0 rule 1: a plain interface whose
 * live implementation is {@code OreDictionaryPreferencesMixin} (mixins.late) and whose T2 fake
 * ({@code FakeOreDictionaryPreferencesAccessor}) lives in {@code src/test}.
 *
 * <p>
 * Only the map we actually consume is exposed. Ender IO uses these preferences to decide which ore
 * dictionary entry a machine yields; {@code EIOIntegration} reads the live map through this seam so
 * it can observe/clear it for unification. The {@code preferences} field is Ender IO-added, not
 * vanilla, so the mixin targets it with {@code remap = false}.
 */
public interface IOreDictionaryPreferencesAccessor {

    /** Ender IO's {@code OreDictionaryPreferences.preferences} map (may be {@code null} before load). */
    Map<String, ItemStack> getPreferences();
}
