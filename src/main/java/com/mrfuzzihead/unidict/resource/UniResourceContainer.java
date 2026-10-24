package com.mrfuzzihead.unidict.resource;

/*
 * Rebuilt from wanion.unidict.resource.UniResourceContainer (WanionCane, MPL-2.0).
 * M4 fixes (docs/PLAN.md §M4 + scope rework 2026-08-12):
 * 1. The live Ore Dictionary list is SNAPSHOTTED at construction; sort / main-entry selection run
 * on the private copy, never on forge's global list (BB-3 non-destructive rule).
 * 2. removeBadEntriesFromNEI / keepOneEntry on the live list are DELETED — NEI hiding and global
 * keep-one-entry collapse are deferred. Selection decisions live in pure SelectionRules and are
 * applied to the snapshot only.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.common.SpecificKindItemStackComparator;
import com.mrfuzzihead.unidict.common.Util;
import com.mrfuzzihead.unidict.oredict.UniOreDictionary;
import com.mrfuzzihead.unidict.pure.SelectionRules;

public final class UniResourceContainer implements IResourceContainer {

    public final String name;
    public final long kind;

    private final String kindName;
    private final int id;
    /** Private snapshot of the live Ore Dictionary list — never the live list itself (BB-3). */
    private final List<ItemStack> entries;
    private final int initialSize;
    private boolean sort = false;
    private boolean updated = false;
    private Item mainEntryItem;
    private int mainEntryMeta;

    public UniResourceContainer(@Nonnull final String name, final long kind) {
        this.kindName = Resource.getNameOfKind(kind);
        this.name = name;
        this.kind = kind;
        this.id = UniOreDictionary.instance()
            .getId(name);
        final List<ItemStack> live = UniOreDictionary.instance()
            .getUn(id);
        if (live == null) throw new RuntimeException("Something may have broken the Ore Dictionary!");
        this.entries = new ArrayList<>(live);
        this.initialSize = entries.size();
    }

    public UniResourceContainer(@Nonnull final String name, final long kind, final boolean sort) {
        this(name, kind);
        if (sort) setSort(true);
    }

    public ItemStack getMainEntry() {
        return new ItemStack(mainEntryItem, 1, mainEntryMeta);
    }

    public ItemStack getMainEntry(final int size) {
        return new ItemStack(mainEntryItem, size, mainEntryMeta);
    }

    /**
     * The canonical entry's {@code Item}, without allocating a stack.
     *
     * <p>
     * The resolver's no-op check reads this (and {@link #getMainEntryDamage()}) instead of calling
     * {@link #getMainEntry(int)}, so an already-canonical drop or a re-run over already-rewritten recipes
     * costs no allocation on the hot path (docs/CODE_REVIEW.md OPT-1). Package-private: both the container
     * and {@code ResourceHandler} live in this package, and the value is what {@link #getMainEntry()} uses.
     *
     * @return the main entry's item (set by {@link #updateEntries()}); {@code null} only before that runs
     */
    Item getMainEntryItem() {
        return mainEntryItem;
    }

    /**
     * The canonical entry's damage value, without allocating a stack.
     *
     * @return the main entry's item damage / metadata; see {@link #getMainEntryItem()}
     */
    int getMainEntryDamage() {
        return mainEntryMeta;
    }

    /** A defensive copy of the snapshot entries (never the live / internal list). */
    public List<ItemStack> getEntries() {
        return new ArrayList<>(entries);
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
        if (entries.isEmpty()) return false;
        if (updated) return true;
        if (sort && SelectionRules.shouldResort(sort, initialSize, entries.size())) sort();
        applyCanonicalOverride();
        if (!entries.isEmpty()) {
            final ItemStack mainEntry = entries.get(0);
            mainEntryMeta = (mainEntryItem = mainEntry.getItem()).getDamage(mainEntry);
        }
        return updated = true;
    }

    @Override
    public void setSort(final boolean sort) {
        if (this.sort = sort) sort();
    }

    public Comparator<ItemStack> getComparator() {
        // Kind-specific owner override when configured (BB-2 model; case-insensitive), else the global order.
        if (Config.get()
            .hasOwnerOverride(kindName)) return SpecificKindItemStackComparator.getComparatorFor(kindName);
        return Util.itemStackComparatorByModName();
    }

    public void sort() {
        final Comparator<ItemStack> itemStackComparator = getComparator();
        if (itemStackComparator != null) Collections.sort(entries, itemStackComparator);
    }

    /**
     * Promotes a configured canonical-override variant to the front of the ordered snapshot so it
     * becomes the container's main (canonical) entry regardless of owner priority. Resolves
     * shared-OD-tag craft conflicts — e.g. both EtF and TF register a 9-ingot → block recipe on the
     * same pattern, so making one block canonical means every copper-block recipe's output (incl. the
     * other mod's) is rewritten to it and only one block is craftable.
     */
    private void applyCanonicalOverride() {
        if (entries.size() < 2) return;
        try {
            for (int i = 0; i < entries.size(); i++) {
                if (ResourceHandler.isCanonicalPreferred(entries.get(i))) {
                    if (i != 0) entries.add(0, entries.remove(i));
                    return;
                }
            }
        } catch (final Exception ignored) {
            // Never let a canonical-override lookup break selection.
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
