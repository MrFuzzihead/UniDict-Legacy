package com.mrfuzzihead.unidict.pure.config;

import java.util.List;

/**
 * Pure resolution of the BB-2 owner model used by the entry comparators. Upstream had a global
 * {@code ownerOfEveryThing}, a per-kind {@code ownerOfEvery<Kind>} list, and an
 * {@code enableSpecificKindSort} boolean to pick between them. Here there is no boolean: a per-kind
 * override in {@link ConfigData#ownerOfKind} applies when present, otherwise
 * {@link ConfigData#ownerPriorities} is the fallback — deterministic and testable (T1).
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

    /** Compares two mod names by their effective owner index for a kind. */
    public static int compare(final ConfigData config, final String kindName, final String modNameA,
        final String modNameB) {
        final long a = indexOf(config, kindName, modNameA);
        final long b = indexOf(config, kindName, modNameB);
        return Long.compare(a, b);
    }

    private static long indexIn(final List<String> owners, final String modName) {
        if (owners == null) return NOT_LISTED;
        final int index = owners.indexOf(modName);
        return (index < 0) ? NOT_LISTED : index;
    }
}
