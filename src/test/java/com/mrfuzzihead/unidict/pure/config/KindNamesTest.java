package com.mrfuzzihead.unidict.pure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for {@link KindNames} — the pure, case-insensitive kind-name matcher behind
 * {@code childrenOfMetals}, {@code hideInNEIBlackSet} and per-kind owner overrides
 * (docs/CODE_REVIEW.md BUG-3). Kind names are OD-derived and not uniformly lowercase, so the helper
 * must never lose the original spelling.
 */
class KindNamesTest {

    private static final LinkedHashSet<String> KIND_SET = new LinkedHashSet<>(
        Arrays.asList("ingot", "ore", "dustTiny", "dust"));

    @Test
    void matchesKindNamesIgnoringCase() {
        assertTrue(KindNames.containsIgnoreCase(KIND_SET, "ingot"));
        assertTrue(KindNames.containsIgnoreCase(KIND_SET, "INGOT"));
        assertTrue(KindNames.containsIgnoreCase(KIND_SET, "Ingot"));
        assertTrue(KindNames.containsIgnoreCase(KIND_SET, "dustTiny"));
        assertTrue(KindNames.containsIgnoreCase(KIND_SET, "dusttiny"));
        assertFalse(KindNames.containsIgnoreCase(KIND_SET, "gem"));
    }

    @Test
    void findReturnsTheOriginalSpelling() {
        // The registered spelling must survive a case-insensitive request, because it is used for
        // Resource.getKindOfName() and for building container names.
        assertEquals("dustTiny", KindNames.findIgnoreCase(KIND_SET, "DUSTTINY"));
        assertEquals("ingot", KindNames.findIgnoreCase(KIND_SET, "ingot"));
        assertNull(KindNames.findIgnoreCase(KIND_SET, "gem"));
    }

    @Test
    void nullInputsAreSafe() {
        assertFalse(KindNames.containsIgnoreCase(null, "ingot"));
        assertFalse(KindNames.containsIgnoreCase(KIND_SET, null));
        assertNull(KindNames.findIgnoreCase(null, "ingot"));
        assertNull(KindNames.findIgnoreCase(KIND_SET, null));
        assertNull(KindNames.lookUpKey(null));
    }

    @Test
    void unknownKindNamesReportsOnlyUnmatchedEntriesSorted() {
        // BUG-18: a configured kind that resolves to nothing is the typo case that used to be silent.
        assertEquals(
            java.util.Arrays.asList("OreTypo", "gemm"),
            KindNames.unknownKindNames(java.util.Arrays.asList("ingot", "OreTypo", "gemm", "INGOT"), KIND_SET));
        assertTrue(
            KindNames.unknownKindNames(java.util.Arrays.asList("ingot", "ORE"), KIND_SET)
                .isEmpty());
        // Blanks and nulls are skipped; duplicates are collapsed.
        assertTrue(
            KindNames.unknownKindNames(java.util.Arrays.asList(null, "", "  ", "ingot"), KIND_SET)
                .isEmpty());
        assertEquals(
            java.util.Arrays.asList("ghost"),
            KindNames.unknownKindNames(java.util.Arrays.asList("ghost", "ghost", "Ghost"), KIND_SET));
        assertTrue(
            KindNames.unknownKindNames(null, KIND_SET)
                .isEmpty());
        assertTrue(
            KindNames.unknownKindNames(java.util.Arrays.asList("ingot"), null)
                .contains("ingot"));
    }

    @Test
    void configuredKindNamesGathersEveryKindBearingSurface() {
        final java.util.Map<String, java.util.List<String>> ownerOfKind = new java.util.LinkedHashMap<>();
        ownerOfKind.put("ingot", java.util.Arrays.asList("minecraft"));
        final ConfigData config = ConfigData.builder()
            .childrenOfMetals(new LinkedHashSet<>(java.util.Arrays.asList("ore", "dustTiny")))
            .hideInNEIBlackSet(new LinkedHashSet<>(java.util.Arrays.asList("gear")))
            .ownerOfKind(ownerOfKind)
            .build();

        final java.util.List<String> configured = KindNames.configuredKindNames(config);
        assertTrue(configured.contains("ore"));
        assertTrue(configured.contains("dustTiny"));
        assertTrue(configured.contains("gear"));
        assertTrue(configured.contains("ingot"));
        assertTrue(
            KindNames.configuredKindNames(null)
                .isEmpty());
    }

    @Test
    void sortedDropsBlanksAndOrdersAsciiCaseSensitively() {
        assertEquals(
            java.util.Arrays.asList("Ore", "block", "dust", "ingot"),
            KindNames.sorted(java.util.Arrays.asList("ingot", null, "dust", "", "  ", "Ore", "block")));
        assertTrue(
            KindNames.sorted(null)
                .isEmpty());
    }

    @Test
    void lookupKeyIsLocaleIndependentLowercase() {
        assertEquals("dusttiny", KindNames.lookUpKey("DustTiny"));
        assertEquals("ingot", KindNames.lookUpKey("INGOT"));
        assertEquals("ingot".toLowerCase(Locale.ROOT), KindNames.lookUpKey("ingot"));
    }
}
