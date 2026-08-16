package com.mrfuzzihead.unidict.pure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure (Minecraft-free) selection decisions for the unification core — the place where the NEI-hide
 * decision, the hide blacklists and the sort-trigger conditions live so they are T1-testable with
 * zero {@code net.minecraft*} imports (docs/PLAN.md §M4, docs/TestPlan.md rule 2).
 *
 * <p>
 * These are <b>decisions only</b>. Applying a decision to a live list (e.g. collapsing forge's
 * global Ore Dictionary) is <b>deferred</b> by the 2026-08-12 scope rework (non-destructive
 * rewriting, BB-3 — never mutate a global registry). {@code UniResourceContainer} uses these to pick
 * and report the canonical entry against a private snapshot instead.
 *
 * <p>
 * The only live selection feature today is NEI variant hiding, driven by {@code autoHideInNEI} with
 * three exemptions: {@code hideInNEIBlackSet} (per kind), the mod blacklist
 * ({@code autoHideInNEIModBlackList}), and protected items ({@code protectedOreDictionaryNames}, e.g.
 * {@code "raw"} for raw metals). Upstream's separate {@code keepOneEntry} collapse is <b>deferred</b>
 * as a stretch goal (TODO.md P0 #2) — it is not wired in.
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
     * Whether non-main variants should be hidden in NEI for a given kind — the kind-level hide gate
     * fed by the NEI hide-set builder ({@link #hiddenIndices}). Hiding requires {@code autoHideInNEI}
     * and the kind not being in {@code kindBlackSet} ({@code hideInNEIBlackSet}).
     */
    public static boolean shouldHideNonMain(final boolean autoHideInNEI, final long kind,
        final Set<Long> kindBlackSet) {
        return autoHideInNEI && (kindBlackSet == null || !kindBlackSet.contains(kind));
    }

    /**
     * The NEI hide-set builder (TODO.md P0 #1): the indices of an already-ordered entry list that must
     * be hidden so the player sees only the canonical entry (plus exempted variants). Driven solely by
     * {@code autoHideInNEI}, with independent exemptions:
     *
     * <ul>
     * <li>{@code kindBlackSet} ({@code hideInNEIBlackSet}) — exempts a whole kind from hiding.</li>
     * <li>{@code modBlacklisted} (the {@code autoHideInNEIModBlackList} mod blacklist, applied per
     * entry) — exempts a specific owner mod's variant from hiding.</li>
     * <li>{@code alwaysVisible} (protected items — e.g. raw metals, via {@code protectedOreDictionaryNames})
     * — exempts specific variants from hiding so they coexist with the canonical entry.</li>
     * </ul>
     *
     * The canonical entry (index 0) is always shown. Returns empty when auto-hide is off, the kind is
     * blacklisted, or the list is empty.
     */
    public static <T> List<Integer> hiddenIndices(final List<T> ordered, final boolean autoHideInNEI, final long kind,
        final Set<Long> kindBlackSet, final Predicate<? super T> modBlacklisted,
        final Predicate<? super T> alwaysVisible) {
        final List<Integer> hidden = new ArrayList<>();
        if (ordered == null || ordered.isEmpty()) return hidden;
        if (!shouldHideNonMain(autoHideInNEI, kind, kindBlackSet)) return hidden;
        for (int i = 1; i < ordered.size(); i++) {
            final T entry = ordered.get(i);
            final boolean exempt = (modBlacklisted != null && modBlacklisted.test(entry))
                || (alwaysVisible != null && alwaysVisible.test(entry));
            if (!exempt) hidden.add(i);
        }
        return hidden;
    }

    /**
     * Whether an already-sorted entry list needs a re-sort because the underlying selection changed
     * size since it was first captured ({@code initialSize} != {@code currentSize}).
     */
    public static boolean shouldResort(final boolean sortRequested, final int initialSize, final int currentSize) {
        return sortRequested && initialSize != currentSize;
    }
}
