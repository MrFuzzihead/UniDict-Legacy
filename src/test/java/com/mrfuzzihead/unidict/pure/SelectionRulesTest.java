package com.mrfuzzihead.unidict.pure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for the pure selection decisions of {@link SelectionRules} (docs/PLAN.md §M4). Zero
 * {@code net.minecraft*} imports; operates on plain strings/indices so it runs on a bare JVM.
 */
class SelectionRulesTest {

    @Test
    void mainIndexIsFirstElementOfNonEmptyList() {
        assertEquals(-1, SelectionRules.mainIndex(Arrays.asList()));
        assertEquals(-1, SelectionRules.mainIndex(null));
        assertEquals(0, SelectionRules.mainIndex(Arrays.asList("a")));
        assertEquals(0, SelectionRules.mainIndex(Arrays.asList("a", "b", "c")));
    }

    @Test
    void keptIndicesKeepEverythingWhenNotKeepingOneEntry() {
        final List<String> ordered = Arrays.asList("ThermalFoundation", "minecraft", "IC2", "TConstruct");
        final List<Integer> kept = SelectionRules.keptIndices(ordered, false, s -> false);
        assertEquals(Arrays.asList(0, 1, 2, 3), kept);
        assertEquals(4, SelectionRules.keptCount(ordered, false, s -> false));
    }

    @Test
    void keptIndicesKeepOnlyMainWhenKeepingOneEntryAndNoBlacklist() {
        final List<String> ordered = Arrays.asList("ThermalFoundation", "minecraft", "IC2", "TConstruct");
        final List<Integer> kept = SelectionRules.keptIndices(ordered, true, s -> false);
        assertEquals(Arrays.asList(0), kept);
        assertEquals(1, SelectionRules.keptCount(ordered, true, s -> false));
    }

    @Test
    void keptIndicesRetainBlacklistedTailEntries() {
        final List<String> ordered = Arrays.asList("ThermalFoundation", "minecraft", "IC2", "TConstruct");
        final List<Integer> kept = SelectionRules
            .keptIndices(ordered, true, s -> s.equals("minecraft") || s.equals("TConstruct"));
        assertEquals(Arrays.asList(0, 1, 3), kept);
        assertEquals(3, SelectionRules.keptCount(ordered, true, s -> s.equals("minecraft") || s.equals("TConstruct")));
    }

    @Test
    void shouldHideNonMainAlwaysWhenKeepingOneEntry() {
        assertTrue(SelectionRules.shouldHideNonMain(true, false, 1L, new LinkedHashSet<>()));
        assertTrue(SelectionRules.shouldHideNonMain(true, true, 1L, new LinkedHashSet<>(Arrays.asList(1L))));
    }

    @Test
    void shouldHideNonMainRequiresAutoHideAndNonBlacklistedKind() {
        final Set<Long> black = new LinkedHashSet<>(Arrays.asList(1L, 4L));
        // autoHide off -> never hide
        assertFalse(SelectionRules.shouldHideNonMain(false, false, 2L, black));
        // blacklisted kind -> never hide
        assertFalse(SelectionRules.shouldHideNonMain(false, true, 1L, black));
        // autoHide on + kind not blacklisted -> hide
        assertTrue(SelectionRules.shouldHideNonMain(false, true, 2L, black));
    }

    @Test
    void shouldResortOnlyWhenSortRequestedAndSizeChanged() {
        assertFalse(SelectionRules.shouldResort(false, 4, 4));
        assertFalse(SelectionRules.shouldResort(false, 4, 6));
        assertFalse(SelectionRules.shouldResort(true, 4, 4));
        assertTrue(SelectionRules.shouldResort(true, 4, 6));
        assertTrue(SelectionRules.shouldResort(true, 6, 3));
    }
}
