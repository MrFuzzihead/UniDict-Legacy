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
        assertFalse(c.galacticraftIntegration);
        assertFalse(c.storageDrawersIntegration);
        assertFalse(c.keepOneEntry);
        assertFalse(c.inputReplacement);
        assertTrue(c.unifyDrops); // drops unification is default-on (safe, non-destructive)
    }

    @Test
    void protectedRawOdNamesDefaultOnInEveryPreset() {
        // Raw metals (e.g. EtF raw copper tagged rawCopper) default to "protected" so they are never
        // canonicalized into a mod's ore block and stay visible in NEI.
        assertTrue(ConfigPresets.standard().protectedOreDictionaryNames.contains("raw"));
        assertTrue(ConfigPresets.minimal().protectedOreDictionaryNames.contains("raw"));
        assertTrue(ConfigPresets.maxCompat().protectedOreDictionaryNames.contains("raw"));
    }

    @Test
    void oreNoLongerExemptedFromNihDefaultAndEtfCopperBlockCanonicalByDefault() {
        final ConfigData standard = ConfigPresets.standard();
        // "ore" was the default kind exemption; the user wants ores auto-collapsed too, so it is gone.
        assertTrue(standard.hideInNEIBlackSet.isEmpty());
        // EtF's copper block is the default canonical copper block in standard/max-compat (resolves the
        // craft conflict); minimal is left clean. Item-name matching is against live registry names, so
        // these are no-ops when EtF is absent.
        assertTrue(standard.canonicalItemNames.contains("etfuturum:copper_block"));
        assertTrue(ConfigPresets.maxCompat().canonicalItemNames.contains("etfuturum:copper_block"));
        assertTrue(ConfigPresets.minimal().canonicalItemNames.isEmpty());
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
        assertTrue(c.galacticraftIntegration);
        assertTrue(c.storageDrawersIntegration);
        assertTrue(c.unifyDrops);
    }

    @Test
    void maxCompatTurnsOnAggressiveFeaturesOnTopOfStandard() {
        final ConfigData c = ConfigPresets.maxCompat();
        assertFalse(c.keepOneEntry); // keepOneEntry deferred (TODO.md P0 #2) — max-compat keeps autoHideInNEI
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
        assertTrue(c.galacticraftIntegration == standard.galacticraftIntegration);
        assertTrue(c.storageDrawersIntegration == standard.storageDrawersIntegration);
    }

    @Test
    void standardMetalsAndChildrenAreFixedAndDeterministic() {
        final ConfigData c = ConfigPresets.standard();
        assertTrue(c.metalsToUnify.containsAll(Arrays.asList("Iron", "Gold", "Copper")));
        assertTrue(c.metalsToUnify.contains("Titanium"), "Galacticraft's titanium must be in the metal set");
        // ExtraPlanets metals with cross-mod equivalents (same rationale as Galacticraft above) must be in
        // the default metal set so their ore/ingot/block variants unify with the canonical entry.
        assertTrue(c.metalsToUnify.contains("Tungsten"), "ExtraPlanets tungsten overlaps GT5 -> unify it");
        assertTrue(c.metalsToUnify.contains("Uranium"), "ExtraPlanets uranium overlaps IC2/GT5 -> unify it");
        assertTrue(c.metalsToUnify.contains("Magnesium"), "ExtraPlanets magnesium overlaps GT5 -> unify it");
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
        assertFalse(ConfigPresets.byName("max-compat").keepOneEntry); // keepOneEntry deferred
        // Case-insensitive.
        assertFalse(ConfigPresets.byName("MAX-COMPAT").keepOneEntry);
        assertFalse(ConfigPresets.byName("MINIMAL").ae2Integration);
        // Unknown / blank / null fall back to the safe standard surface (never empty).
        assertFalse(ConfigPresets.byName("bogus").keepOneEntry);
        assertFalse(ConfigPresets.byName("").keepOneEntry);
        assertFalse(ConfigPresets.byName(null).keepOneEntry);
        // Default name matches the fallback.
        assertEquals(ConfigPresets.DEFAULT_NAME, "standard");
    }
}
