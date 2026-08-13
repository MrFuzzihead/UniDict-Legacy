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
import com.mrfuzzihead.unidict.MetaItem;
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
    private int[] hashes;

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
        if (!entries.isEmpty()) {
            final ItemStack mainEntry = entries.get(0);
            mainEntryMeta = (mainEntryItem = mainEntry.getItem()).getDamage(mainEntry);
            hashes = MetaItem.getArray(entries);
        }
        return updated = true;
    }

    int[] getHashes() {
        return hashes;
    }

    @Override
    public void setSort(final boolean sort) {
        if (this.sort = sort) sort();
    }

    public Comparator<ItemStack> getComparator() {
        // Kind-specific owner override when configured (BB-2 model), else the global owner order.
        if (kindName != null && Config.get().ownerOfKind.containsKey(kindName))
            return SpecificKindItemStackComparator.getComparatorFor(kindName);
        return Util.itemStackComparatorByModName();
    }

    public void sort() {
        final Comparator<ItemStack> itemStackComparator = getComparator();
        if (itemStackComparator != null) Collections.sort(entries, itemStackComparator);
    }

    @Override
    public String toString() {
        return name;
    }
}
