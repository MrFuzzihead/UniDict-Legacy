package com.mrfuzzihead.unidict.pure.config;

import java.util.List;

/**
 * Pure resolution of the BB-2 owner model used by the entry comparators. Upstream had a global
 * {@code ownerOfEveryThing}, a per-kind {@code ownerOfEvery<Kind>} list, and an
 * {@code enableSpecificKindSort} boolean to pick between them. Here there is no boolean: a per-kind
 * override in {@link ConfigData#ownerOfKind} applies when present, otherwise
 * {@link ConfigData#ownerPriorities} is the fallback — deterministic and testable (T1).
 *
 * <p>
 * Mods absent from the effective owner order all share the {@link #NOT_LISTED} sentinel. To keep the
 * canonical (main) entry stable even when no listed mod holds a resource, equal ranks are broken
 * deterministically by a lexical comparison of the two mod names (see {@link #tiebreak}).
 */
public final class OwnerOrder {

    /** Sentinel for a mod that appears in no owner list — sorts last. */
    public static final long NOT_LISTED = Long.MAX_VALUE;

    private OwnerOrder() {}

    /**
     * @param kindName the kind (e.g. "INGOT"); lookup is per-kind, falling back to the global list.
     * @return the index of {@code modName} in the effective owner order, or {@link #NOT_LISTED}.
     */
    public static long indexOf(final ConfigData config, final String kindName, final String modName) {
        final List<String> owners = orderedOwners(config, kindName);
        return indexIn(owners, modName);
    }

    /** @return the index of {@code modName} in the global owner priority list. */
    public static long globalIndexOf(final ConfigData config, final String modName) {
        return indexIn(config.ownerPriorities, modName);
    }

    /** @return the effective owner order for a kind: its per-kind override, else the global list. */
    public static List<String> orderedOwners(final ConfigData config, final String kindName) {
        final List<String> perKind = config.ownerOfKind.get(kindName);
        return (perKind != null && !perKind.isEmpty()) ? perKind : config.ownerPriorities;
    }

    /**
     * Compares two mod names by their effective owner index for a kind (lowest index = highest
     * priority). When both are absent from the owner order ({@link #NOT_LISTED}), the tie is broken
     * deterministically by the lexical {@link #tiebreak} so the canonical entry does not vary
     * run-to-run (docs/PLAN.md §BB-1 "stable + diffable report").
     */
    public static int compare(final ConfigData config, final String kindName, final String modNameA,
        final String modNameB) {
        final long a = indexOf(config, kindName, modNameA);
        final long b = indexOf(config, kindName, modNameB);
        if (a != b) return Long.compare(a, b);
        return tiebreak(modNameA, modNameB);
    }

    /**
     * Compares two mod names by their <em>global</em> owner index (the non-per-kind path used by
     * {@code Util.itemStackComparatorByModName()}). Identical semantics to {@link #compare} — a
     * lexical {@link #tiebreak} resolves the equal-{@link #NOT_LISTED} case deterministically.
     */
    public static int compareGlobal(final ConfigData config, final String modNameA, final String modNameB) {
        final long a = globalIndexOf(config, modNameA);
        final long b = globalIndexOf(config, modNameB);
        if (a != b) return Long.compare(a, b);
        return tiebreak(modNameA, modNameB);
    }

    /**
     * Deterministic tiebreak for two mods with the same owner rank (both {@link #NOT_LISTED}): a
     * case-sensitive lexical comparison of the mod names, null-safe ({@code null} sorts first).
     */
    private static int tiebreak(final String modNameA, final String modNameB) {
        if (modNameA == null) return (modNameB == null) ? 0 : -1;
        if (modNameB == null) return 1;
        return modNameA.compareTo(modNameB);
    }

    private static long indexIn(final List<String> owners, final String modName) {
        if (owners == null) return NOT_LISTED;
        final int index = owners.indexOf(modName);
        return (index < 0) ? NOT_LISTED : index;
    }
}
