package com.mrfuzzihead.unidict.pure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure (Minecraft-free) selection decisions for the unification core — the place where the
 * keep-one-entry semantics, the NEI-hide eligibility and the sort-trigger conditions live so they
 * are T1-testable with zero {@code net.minecraft*} imports (docs/PLAN.md §M4, docs/TestPlan.md
 * rule 2).
 *
 * <p>
 * These are <b>decisions only</b>. Applying a decision to a live list (e.g. collapsing forge's
 * global Ore Dictionary) is <b>deferred</b> by the 2026-08-12 scope rework (non-destructive
 * rewriting, BB-3 — never mutate a global registry). {@code UniResourceContainer} uses these to pick
 * and report the canonical entry against a private snapshot instead.
 */
public final class SelectionRules {

    private SelectionRules() {}

    /**
     * Index of the canonical ("main") entry within a list already ordered by selection priority.
     *
     * @return {@code 0} when the list is non-empty, {@code -1} when it is empty.
     */
    public static int mainIndex(final List<?> entries) {
        return (entries == null || entries.isEmpty()) ? -1 : 0;
    }

    /**
     * Indices that survive a keep-one-entry pass over an already-ordered list. The main entry
     * (index 0) is always kept; when {@code keepOneEntry} is on, the only other survivors are
     * entries whose owning mod is blacklisted (matching upstream's
     * {@code keepOneEntryModBlackSet} handling). When off, every entry survives.
     *
     * @param ordered         a list already ordered by selection priority
     * @param keepOneEntry    whether to collapse to the main entry (+ blacklisted tail)
     * @param keepBlacklisted predicate that is {@code true} for blacklisted (kept) entries
     * @return the surviving indices, in ascending order
     */
    public static <T> List<Integer> keptIndices(final List<T> ordered, final boolean keepOneEntry,
        final Predicate<? super T> keepBlacklisted) {
        final List<Integer> kept = new ArrayList<>();
        if (ordered == null) return kept;
        for (int i = 0; i < ordered.size(); i++) {
            if (i == 0 || !keepOneEntry || (keepBlacklisted != null && keepBlacklisted.test(ordered.get(i))))
                kept.add(i);
        }
        return kept;
    }

    /**
     * Number of entries that survive keep-one-entry, mirroring {@link #keptIndices}.
     */
    public static <T> int keptCount(final List<T> ordered, final boolean keepOneEntry,
        final Predicate<? super T> keepBlacklisted) {
        return keptIndices(ordered, keepOneEntry, keepBlacklisted).size();
    }

    /**
     * Whether non-main variants should be hidden in NEI. Decision only — actual hiding is deferred
     * (docs/PLAN.md scope rework, "No NEI hiding"). When {@code keepOneEntry} is on every non-main
     * variant is hidden; otherwise hiding requires {@code autoHideInNEI} and the kind not being
     * blacklisted.
     */
    public static boolean shouldHideNonMain(final boolean keepOneEntry, final boolean autoHideInNEI, final long kind,
        final Set<Long> kindBlackSet) {
        if (keepOneEntry) return true;
        return autoHideInNEI && (kindBlackSet == null || !kindBlackSet.contains(kind));
    }

    /**
     * Whether an already-sorted entry list needs a re-sort because the underlying selection changed
     * size since it was first captured ({@code initialSize} != {@code currentSize}).
     */
    public static boolean shouldResort(final boolean sortRequested, final int initialSize, final int currentSize) {
        return sortRequested && initialSize != currentSize;
    }
}
