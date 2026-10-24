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
    void lookupKeyIsLocaleIndependentLowercase() {
        assertEquals("dusttiny", KindNames.lookUpKey("DustTiny"));
        assertEquals("ingot", KindNames.lookUpKey("INGOT"));
        assertEquals("ingot".toLowerCase(Locale.ROOT), KindNames.lookUpKey("ingot"));
    }
}
