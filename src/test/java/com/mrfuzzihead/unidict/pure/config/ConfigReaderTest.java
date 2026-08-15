package com.mrfuzzihead.unidict.pure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for {@link ConfigReader} — the pure map→config parser. Zero MC imports. Covers: key
 * round-trip against a fixture, explicit-keys-override-preset (last-write-wins), ignored legacy /
 * unknown keys, owner-of-kind (and its legacy alias) parsing, and bracket/comma list handling.
 */
class ConfigReaderTest {

    private static Map<String, String> fixture() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("keepOneEntry", "true");
        raw.put("inputReplacement", "true");
        raw.put("unifyDrops", "false");
        raw.put("autoHideInNEI", "true");
        raw.put("kindDebugMode", "true");
        raw.put("keepOneEntryModBlackList", "SomeMod, OtherMod"); // legacy alias for autoHideInNEIModBlackList
        raw.put("autoHideInNEIBlackList", "ore, ingot");
        raw.put("protectedOreDictionaryNames", "raw, tiny");
        raw.put("metalsToUnify", "[Iron, Gold, Copper, Tin]");
        raw.put("childrenOfMetals", "ingot, nugget");
        raw.put("resourceBlackList", "Aluminium, Chrome");
        raw.put("ownerPriorities", "ThermalFoundation, minecraft");
        raw.put("ownerOfKind.INGOT", "minecraft, ThermalFoundation");
        raw.put("integration", "true");
        raw.put("furnaceIntegration", "true");
        raw.put("appliedEnergistics2", "false");
        raw.put("industrialCraft2", "false");
        raw.put("immersiveEngineering", "false");
        raw.put("chestIntegration", "true");
        raw.put("enderIO", "false");
        raw.put("forestry", "false");
        raw.put("railcraft", "false");
        raw.put("thermalExpansion", "false");
        raw.put("galacticraft", "false");
        return raw;
    }

    @Test
    void everyKeptKeyRoundTripsAgainstTheFixture() {
        final ConfigData c = ConfigReader.parse(ConfigPresets.standard(), fixture()).config;

        assertTrue(c.keepOneEntry);
        assertTrue(c.inputReplacement);
        assertFalse(c.unifyDrops);
        assertTrue(c.autoHideInNEI);
        assertTrue(c.kindDebugMode);
        assertEquals(setOf("SomeMod", "OtherMod"), c.autoHideInNEIModBlackSet);
        assertEquals(setOf("ore", "ingot"), c.hideInNEIBlackSet);
        assertEquals(setOf("raw", "tiny"), c.protectedOreDictionaryNames);
        assertEquals(setOf("Iron", "Gold", "Copper", "Tin"), c.metalsToUnify);
        assertEquals(setOf("ingot", "nugget"), c.childrenOfMetals);
        assertEquals(listOf("Aluminium", "Chrome"), c.resourceBlackList);
        assertEquals(listOf("ThermalFoundation", "minecraft"), c.ownerPriorities);

        assertTrue(c.integrationModuleEnabled);
        assertTrue(c.furnaceIntegration);
        assertFalse(c.ae2Integration);
        assertFalse(c.ic2Integration);
        assertFalse(c.ieIntegration);
        assertTrue(c.chestIntegration);
        assertFalse(c.enderIOIntegration);
        assertFalse(c.forestryIntegration);
        assertFalse(c.railcraftIntegration);
        assertFalse(c.thermalExpansionIntegration);
        assertFalse(c.galacticraftIntegration);
    }

    @Test
    void explicitKeysOverridePresetDefaults() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("furnaceIntegration", "false");
        raw.put("thermalExpansion", "false");
        raw.put("galacticraft", "false");
        raw.put("metalsToUnify", "Zinc");
        // Absent keys fall back to the standard preset (all integrations on).
        final ConfigData c = ConfigReader.parse(ConfigPresets.standard(), raw).config;

        assertFalse(c.furnaceIntegration);
        assertFalse(c.thermalExpansionIntegration);
        assertTrue(c.ae2Integration); // preset default preserved
        assertTrue(c.ic2Integration);
        assertEquals(setOf("Zinc"), c.metalsToUnify);
        assertFalse(c.inputReplacement); // preset default false
        assertTrue(c.unifyDrops); // preset default true (drops unification)
    }

    @Test
    void ownerOfKindParsesModernAndLegacyKeys() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("ownerOfKind.INGOT", "modA, modB");
        raw.put("ownerOfEveryDUST", "modX, modY"); // legacy alias for ownerOfKind.DUST
        final ConfigData c = ConfigReader.parse(ConfigPresets.standard(), raw).config;

        assertEquals(listOf("modA", "modB"), c.ownerOfKind.get("INGOT"));
        assertEquals(listOf("modX", "modY"), c.ownerOfKind.get("DUST"));
    }

    @Test
    void ignoredKeysAreCollectedForReporting() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("enableSpecificKindSort", "true"); // subsumed by the owner model
        raw.put("customUnifiedResources", "Obsidian:dust|dust"); // deferred
        raw.put("mekanism", "true"); // removed mod
        final ConfigReader.Result result = ConfigReader.parse(ConfigPresets.standard(), raw);

        assertEquals(3, result.ignored.size());
        assertTrue(result.ignored.contains("mekanism"));
        assertTrue(result.ignored.contains("customUnifiedResources"));
        assertTrue(result.ignored.contains("enableSpecificKindSort"));
    }

    @Test
    void ownerOfEveryThingAliasBacksTheGlobalPriorities() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("ownerOfEveryThing", "TE, vanilla"); // legacy alias for ownerPriorities
        final ConfigData c = ConfigReader.parse(ConfigPresets.standard(), raw).config;
        assertEquals(listOf("TE", "vanilla"), c.ownerPriorities);
    }

    @Test
    void modBlacklistAliasAcceptsTheLegacyKeepOneName() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("keepOneEntryModBlackList", "OldMod"); // legacy alias for autoHideInNEIModBlackList
        final ConfigData c = ConfigReader.parse(ConfigPresets.standard(), raw).config;
        assertEquals(setOf("OldMod"), c.autoHideInNEIModBlackSet);
    }

    @Test
    void presetKeyIsRecognizedAndNotReportedIgnored() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("preset", "max-compat"); // consumed by the loader (BB-2), not a ConfigData field
        final ConfigReader.Result result = ConfigReader.parse(ConfigPresets.standard(), raw);
        assertTrue(result.ignored.isEmpty());
    }

    @Test
    void nonStandardPresetSuppliesTheDefaultSurface() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("furnaceIntegration", "false"); // explicit key still wins
        // minimal base: only vanilla-safe integrations on, small metal set.
        final ConfigData c = ConfigReader.parse(ConfigPresets.minimal(), raw).config;
        assertFalse(c.furnaceIntegration); // explicit override
        assertTrue(c.chestIntegration); // minimal default preserved
        assertFalse(c.ae2Integration); // minimal default (off)
        assertFalse(c.keepOneEntry); // minimal default
        assertEquals(setOf("Iron", "Gold", "Copper", "Tin"), c.metalsToUnify); // minimal metal set
    }

    private static java.util.List<String> listOf(final String... values) {
        return java.util.Arrays.asList(values);
    }

    private static java.util.Set<String> setOf(final String... values) {
        return new java.util.LinkedHashSet<>(java.util.Arrays.asList(values));
    }
}
