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
    void shouldHideNonMainRequiresAutoHideAndNonBlacklistedKind() {
        final Set<Long> black = new LinkedHashSet<>(Arrays.asList(1L, 4L));
        // autoHide off -> never hide
        assertFalse(SelectionRules.shouldHideNonMain(false, 2L, black));
        // blacklisted kind -> never hide
        assertFalse(SelectionRules.shouldHideNonMain(true, 1L, black));
        // autoHide on + kind not blacklisted -> hide
        assertTrue(SelectionRules.shouldHideNonMain(true, 2L, black));
    }

    @Test
    void hiddenIndicesEmptyOrNullListHidesNothing() {
        assertTrue(
            SelectionRules.hiddenIndices(null, true, 1L, new LinkedHashSet<>(), s -> false)
                .isEmpty());
        assertTrue(
            SelectionRules.hiddenIndices(Arrays.asList(), true, 1L, new LinkedHashSet<>(), s -> false)
                .isEmpty());
    }

    @Test
    void hiddenIndicesNothingWhenAutoHideOffOrKindBlacklisted() {
        final List<String> ordered = Arrays.asList("ThermalFoundation", "minecraft", "IC2");
        // autoHide off -> no hiding at all.
        assertTrue(
            SelectionRules.hiddenIndices(ordered, false, 2L, new LinkedHashSet<>(), s -> false)
                .isEmpty());
        // autoHide on but kind blacklisted -> no hiding for that kind.
        assertTrue(
            SelectionRules.hiddenIndices(ordered, true, 1L, new LinkedHashSet<>(Arrays.asList(1L)), s -> false)
                .isEmpty());
    }

    @Test
    void hiddenIndicesAutoHideHidesNonMainWhenKindNotBlacklisted() {
        final List<String> ordered = Arrays.asList("ThermalFoundation", "minecraft", "IC2", "TConstruct");
        final List<Integer> hidden = SelectionRules
            .hiddenIndices(ordered, true, 2L, new LinkedHashSet<>(Arrays.asList(1L)), s -> false);
        assertEquals(Arrays.asList(1, 2, 3), hidden);
    }

    @Test
    void hiddenIndicesModBlacklistExemptsSpecificEntries() {
        final List<String> ordered = Arrays.asList("ThermalFoundation", "minecraft", "IC2", "TConstruct");
        // The mod blacklist exempts minecraft (index 1) and TConstruct (index 3) from hiding; the main
        // entry (index 0) is always shown; IC2 (index 2) is collapsed.
        final List<Integer> hidden = SelectionRules.hiddenIndices(
            ordered,
            true,
            2L,
            new LinkedHashSet<>(Arrays.asList(1L)),
            s -> s.equals("minecraft") || s.equals("TConstruct"));
        assertEquals(Arrays.asList(2), hidden);
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
