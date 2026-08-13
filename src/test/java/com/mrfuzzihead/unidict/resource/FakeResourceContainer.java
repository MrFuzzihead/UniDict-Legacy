package com.mrfuzzihead.unidict.resource;

import javax.annotation.Nonnull;

/**
 * Pure test fake for {@link IResourceContainer}, so {@code Resource}'s child mechanics
 * ({@code addChild} / {@code filteredClone} / {@code updateEntries}) are T1-testable with zero
 * {@code net.minecraft*} imports. The production implementation is {@code UniResourceContainer} (M4).
 */
final class FakeResourceContainer implements IResourceContainer {

    private final String name;
    private final long kind;
    private boolean dropOnUpdate = false;

    FakeResourceContainer(@Nonnull final String name, final long kind) {
        this.name = name;
        this.kind = kind;
    }

    /** When {@code true}, {@link #updateEntries()} reports {@code false} so the resource prunes this entry. */
    void setDropOnUpdate(final boolean dropOnUpdate) {
        this.dropOnUpdate = dropOnUpdate;
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    public long getKind() {
        return kind;
    }

    @Override
    public boolean updateEntries() {
        return !dropOnUpdate;
    }

    @Override
    public void setSort(final boolean sort) {
        // no-op for the pure fake
    }
}
