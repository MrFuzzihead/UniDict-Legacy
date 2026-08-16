package com.mrfuzzihead.unidict.resource;

import javax.annotation.Nonnull;

/**
 * Pure contract every child entry of a {@link Resource} must satisfy.
 *
 * <p>
 * The single live implementation is {@code UniResourceContainer}, which is Minecraft-bound
 * (it reads {@code UniOreDictionary} and stores {@code ItemStack}s) and is ported in milestone
 * M4. The interface lets {@code Resource}'s child mechanics — {@code addChild} merge/name-suffix
 * rules, {@code filteredClone} bit math, {@code updateEntries} pruning — be exercised by a pure
 * test fake with zero {@code net.minecraft*} imports (docs/TestPlan.md rules 2 and 6).
 */
public interface IResourceContainer {

    /** The resource name this entry belongs to (unified ore/variant name). */
    @Nonnull
    String getName();

    /** The kind bit(s) of this entry, drawn from the {@link Resource} kind taxonomy. */
    long getKind();

    /**
     * Reconciles this entry with the live Ore Dictionary.
     *
     * @return {@code true} to keep the entry as part of its resource, {@code false} to drop it.
     */
    boolean updateEntries();

    /** Requests that the entry sort its entries (no-op for entry kinds that are never sorted). */
    void setSort(boolean sort);
}
