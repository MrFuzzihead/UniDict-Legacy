package com.mrfuzzihead.unidict.pure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for {@link ConfigPresets} — the BB-2 presets must each resolve to a documented,
 * deterministic default surface (docs/PLAN.md §BB-2 gate).
 */
class ConfigPresetsTest {

    @Test
    void minimalEnablesOnlyVanillaSafeIntegrations() {
        final ConfigData c = ConfigPresets.minimal();
        assertTrue(c.integrationModuleEnabled);
        assertTrue(c.furnaceIntegration);
        assertTrue(c.chestIntegration);
        assertFalse(c.ae2Integration);
        assertFalse(c.ic2Integration);
        assertFalse(c.ieIntegration);
        assertFalse(c.enderIOIntegration);
        assertFalse(c.railcraftIntegration);
        assertFalse(c.thermalExpansionIntegration);
        assertFalse(c.forestryIntegration);
        assertFalse(c.keepOneEntry);
        assertFalse(c.inputReplacement);
        assertTrue(c.unifyDrops); // drops unification is default-on (safe, non-destructive)
    }

    @Test
    void standardEnablesEveryKeptIntegration() {
        final ConfigData c = ConfigPresets.standard();
        assertTrue(c.integrationModuleEnabled);
        assertTrue(c.furnaceIntegration);
        assertTrue(c.chestIntegration);
        assertTrue(c.ae2Integration);
        assertTrue(c.ic2Integration);
        assertTrue(c.ieIntegration);
        assertTrue(c.enderIOIntegration);
        assertTrue(c.railcraftIntegration);
        assertTrue(c.thermalExpansionIntegration);
        assertTrue(c.forestryIntegration);
        assertTrue(c.unifyDrops);
    }

    @Test
    void maxCompatTurnsOnAggressiveFeaturesOnTopOfStandard() {
        final ConfigData c = ConfigPresets.maxCompat();
        assertTrue(c.keepOneEntry);
        assertTrue(c.inputReplacement);
        assertTrue(c.unifyDrops);
        assertTrue(c.kindDebugMode);
        // Every standard integration stays enabled.
        final ConfigData standard = ConfigPresets.standard();
        assertTrue(c.furnaceIntegration == standard.furnaceIntegration);
        assertTrue(c.ae2Integration == standard.ae2Integration);
        assertTrue(c.ic2Integration == standard.ic2Integration);
        assertTrue(c.ieIntegration == standard.ieIntegration);
        assertTrue(c.chestIntegration == standard.chestIntegration);
        assertTrue(c.enderIOIntegration == standard.enderIOIntegration);
        assertTrue(c.forestryIntegration == standard.forestryIntegration);
        assertTrue(c.railcraftIntegration == standard.railcraftIntegration);
        assertTrue(c.thermalExpansionIntegration == standard.thermalExpansionIntegration);
    }

    @Test
    void standardMetalsAndChildrenAreFixedAndDeterministic() {
        final ConfigData c = ConfigPresets.standard();
        assertTrue(c.metalsToUnify.containsAll(Arrays.asList("Iron", "Gold", "Copper")));
        assertTrue(c.childrenOfMetals.containsAll(Arrays.asList("ore", "ingot", "dust")));
        // Deterministic across calls: same values, same order.
        final ConfigData again = ConfigPresets.standard();
        assertTrue(c.ownerPriorities.equals(again.ownerPriorities));
    }

    @Test
    void byNameResolvesEachPresetAndFallsBackToStandard() {
        // Exact names.
        assertFalse(ConfigPresets.byName("minimal").ae2Integration);
        assertTrue(ConfigPresets.byName("standard").ae2Integration);
        assertTrue(ConfigPresets.byName("max-compat").keepOneEntry);
        // Case-insensitive.
        assertTrue(ConfigPresets.byName("MAX-COMPAT").keepOneEntry);
        assertFalse(ConfigPresets.byName("MINIMAL").ae2Integration);
        // Unknown / blank / null fall back to the safe standard surface (never empty).
        assertFalse(ConfigPresets.byName("bogus").keepOneEntry);
        assertFalse(ConfigPresets.byName("").keepOneEntry);
        assertFalse(ConfigPresets.byName(null).keepOneEntry);
        // Default name matches the fallback.
        assertEquals(ConfigPresets.DEFAULT_NAME, "standard");
    }
}
