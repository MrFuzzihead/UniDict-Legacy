package com.mrfuzzihead.unidict.pure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for the {@link ConfigData} behaviour added by the code-review fixes:
 * <ul>
 * <li><b>Case-insensitive kind matching</b> (docs/CODE_REVIEW.md BUG-3). Kind names come from Ore
 * Dictionary prefixes and are <em>not</em> all lowercase ({@code dustTiny} is in the default child
 * set), so a fix must not lower-case the stored values — configured spellings are preserved and only
 * look-ups ignore case.</li>
 * <li><b>Case-insensitive owner override lookup</b> ({@link ConfigData#ownerOfKindFor}).</li>
 * <li><b>Single crafting gate</b> ({@link ConfigData#craftingUnificationEnabled}) shared by
 * {@code CraftingIntegration} and the {@code CraftingManagerMixin} hook (BUG-5).</li>
 * <li><b>Non-kind values are untouched</b> — resource names (capitalised), mod ids and registry names
 * are case-sensitive and must not be normalised.</li>
 * </ul>
 * Zero {@code net.minecraft*} imports.
 */
class ConfigDataTest {

    @Test
    void ownerOverrideKeysKeepTheirSpellingButMatchCaseInsensitively() {
        final Map<String, List<String>> ownerOfKind = new LinkedHashMap<>();
        ownerOfKind.put("INGOT", Arrays.asList("minecraft", "IC2"));

        final ConfigData c = ConfigData.builder()
            .ownerOfKind(ownerOfKind)
            .build();

        // The public map keeps exactly what was configured (no silent rewriting of the user's file).
        assertEquals(Arrays.asList("minecraft", "IC2"), c.ownerOfKind.get("INGOT"));
        assertFalse(c.ownerOfKind.containsKey("ingot"));

        // Look-ups ignore case, so `INGOT`, `Ingot` and the registered `ingot` all resolve.
        for (final String spelling : Arrays.asList("INGOT", "ingot", "Ingot", "iNgOt")) {
            assertTrue(c.hasOwnerOverride(spelling), spelling + " should resolve the INGOT override");
            assertEquals(Arrays.asList("minecraft", "IC2"), c.ownerOfKindFor(spelling));
        }
        assertFalse(c.hasOwnerOverride("dust"));
        assertNull(c.ownerOfKindFor("dust"));
        assertNull(c.ownerOfKindFor(null));
        assertFalse(c.hasOwnerOverride(null));
    }

    @Test
    void legacyAliasKeyResolvesThroughOwnerOrder() {
        // ConfigReader turns ownerOfEveryINGOT into ownerOfKind.INGOT; ConfigData's look-up ignores case
        // and OwnerOrder lowercases nothing but asks for the runtime kind name ("ingot"). All three line up.
        final Map<String, List<String>> ownerOfKind = new LinkedHashMap<>();
        ownerOfKind.put("INGOT", Arrays.asList("minecraft", "IC2"));
        final ConfigData c = ConfigData.builder()
            .ownerPriorities(Arrays.asList("ThermalFoundation"))
            .ownerOfKind(ownerOfKind)
            .build();

        assertEquals(0, OwnerOrder.indexOf(c, "ingot", "minecraft"));
        assertEquals(1, OwnerOrder.indexOf(c, "ingot", "IC2"));
        // A caller still passing the documented uppercase kind name sees the same override.
        assertEquals(0, OwnerOrder.indexOf(c, "INGOT", "minecraft"));
        // A kind without an override keeps using the global list.
        assertEquals(0, OwnerOrder.indexOf(c, "dust", "ThermalFoundation"));
    }

    @Test
    void childAndHideKindListsAreMatchedCaseInsensitivelyButKeepTheirSpelling() {
        final ConfigData c = ConfigData.builder()
            .childrenOfMetals(new LinkedHashSet<>(Arrays.asList("Ingot", "ORE", "dustTiny")))
            .hideInNEIBlackSet(new LinkedHashSet<>(Arrays.asList("Ore")))
            .build();

        // Stored values keep the configured spelling (camelCase matter: `dustTiny` must survive).
        assertTrue(c.childrenOfMetals.contains("Ingot"));
        assertTrue(c.childrenOfMetals.contains("dustTiny"));
        assertTrue(c.hideInNEIBlackSet.contains("Ore"));

        // Matching ignores case, in both directions.
        assertTrue(KindNames.containsIgnoreCase(c.childrenOfMetals, "ingot"));
        assertTrue(KindNames.containsIgnoreCase(c.childrenOfMetals, "DUSTTINY"));
        assertTrue(KindNames.containsIgnoreCase(c.childrenOfMetals, "dustTiny"));
        assertFalse(KindNames.containsIgnoreCase(c.childrenOfMetals, "gem"));
        assertTrue(KindNames.containsIgnoreCase(c.hideInNEIBlackSet, "ore"));
    }

    @Test
    void ownerRanksArePrecomputedWithFirstOccurrenceWins() {
        // OPT-6: ranks are precomputed maps; duplicate entries must keep List.indexOf's first-occurrence
        // semantics, and absent/null mods report the NOT_LISTED sentinel.
        final ConfigData c = ConfigData.builder()
            .ownerPriorities(Arrays.asList("ThermalFoundation", "minecraft", "ThermalFoundation"))
            .build();

        assertEquals(0, c.globalOwnerRank("ThermalFoundation"));
        assertEquals(1, c.globalOwnerRank("minecraft"));
        assertEquals(OwnerOrder.NOT_LISTED, c.globalOwnerRank("IC2"));
        assertEquals(OwnerOrder.NOT_LISTED, c.globalOwnerRank(null));
    }

    @Test
    void perKindRanksOverrideGlobalAndIgnoreCase() {
        final Map<String, List<String>> ownerOfKind = new LinkedHashMap<>();
        ownerOfKind.put("INGOT", Arrays.asList("minecraft", "IC2"));
        final ConfigData c = ConfigData.builder()
            .ownerPriorities(Arrays.asList("ThermalFoundation"))
            .ownerOfKind(ownerOfKind)
            .build();

        // The stored spelling is INGOT; look-ups ignore case, like ownerOfKindFor/hasOwnerOverride.
        assertEquals(0, c.ownerRank("ingot", "minecraft"));
        assertEquals(1, c.ownerRank("INGOT", "IC2"));
        assertEquals(OwnerOrder.NOT_LISTED, c.ownerRank("ingot", "ThermalFoundation"));
        // A kind without an override falls back to the global order.
        assertEquals(0, c.ownerRank("dust", "ThermalFoundation"));
        // A null kind means "no per-kind context" -> global order.
        assertEquals(0, c.ownerRank(null, "ThermalFoundation"));
    }

    @Test
    void emptyPerKindOverrideFallsBackToGlobalRanks() {
        final Map<String, List<String>> ownerOfKind = new LinkedHashMap<>();
        ownerOfKind.put("ingot", Collections.emptyList());
        final ConfigData c = ConfigData.builder()
            .ownerPriorities(Arrays.asList("ThermalFoundation", "IC2"))
            .ownerOfKind(ownerOfKind)
            .build();

        assertEquals(0, c.ownerRank("ingot", "ThermalFoundation"));
        assertEquals(1, c.ownerRank("ingot", "IC2"));
    }

    @Test
    void craftingGateRequiresBothSwitches() {
        assertTrue(
            ConfigData.builder()
                .craftingIntegration(true)
                .integrationModuleEnabled(true)
                .build()
                .craftingUnificationEnabled());
        assertFalse(
            ConfigData.builder()
                .craftingIntegration(false)
                .integrationModuleEnabled(true)
                .build()
                .craftingUnificationEnabled());
        assertFalse(
            ConfigData.builder()
                .craftingIntegration(true)
                .integrationModuleEnabled(false)
                .build()
                .craftingUnificationEnabled());
        assertFalse(
            ConfigData.builder()
                .craftingIntegration(false)
                .integrationModuleEnabled(false)
                .build()
                .craftingUnificationEnabled());
    }

    @Test
    void caseSensitiveValuesAreNotNormalised() {
        final ConfigData c = ConfigData.builder()
            .metalsToUnify(new LinkedHashSet<>(Arrays.asList("Copper", "MeteoricIron")))
            .resourceBlackList(Arrays.asList("Aluminium"))
            .ownerPriorities(Arrays.asList("ThermalFoundation", "IC2"))
            .autoHideInNEIModBlackSet(new LinkedHashSet<>(Arrays.asList("ThermalFoundation")))
            .protectedItemNames(new LinkedHashSet<>(Arrays.asList("EtFuturum:block_copper")))
            .canonicalItemNames(new LinkedHashSet<>(Arrays.asList("etfuturum:copper_block")))
            .build();

        assertTrue(c.metalsToUnify.contains("Copper"));
        assertTrue(c.metalsToUnify.contains("MeteoricIron"));
        assertTrue(c.resourceBlackList.contains("Aluminium"));
        assertTrue(c.ownerPriorities.contains("ThermalFoundation"));
        assertTrue(c.autoHideInNEIModBlackSet.contains("ThermalFoundation"));
        assertTrue(c.protectedItemNames.contains("EtFuturum:block_copper"));
        assertTrue(c.canonicalItemNames.contains("etfuturum:copper_block"));
    }
}
