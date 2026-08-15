package com.mrfuzzihead.unidict.common;

import static com.mrfuzzihead.unidict.common.Util.getModName;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.pure.config.ConfigData;
import com.mrfuzzihead.unidict.pure.config.OwnerOrder;

/**
 * Sorts a kind's entries by owner-mod priority from the current {@link Config} owner model.
 * Ported from {@code wanion.unidict.common.SpecificKindItemStackComparator} (WanionCane, MPL-2.0);
 * the ordering now flows through the pure {@link OwnerOrder} (T1-tested, docs/PLAN.md §M2 commit 2)
 * instead of upstream's {@code Config.getOwnerOfEveryKindMap} reflection into the forge config.
 *
 * <p>
 * The keep-one-entry black-set side effect upstream accumulated here is <b>not preserved</b>: the
 * live NEI-hide decision reads the two exemption blacklists directly via
 * {@link com.mrfuzzihead.unidict.resource.ResourceHandler#isKeepOneEntryBlacklisted} and the kind
 * blacklist, so this comparator only orders (TODO.md P0 #1).
 */
public final class SpecificKindItemStackComparator implements Comparator<ItemStack> {

    private static final Map<String, SpecificKindItemStackComparator> CACHE = new ConcurrentHashMap<>();

    private final String kindName;
    private final ConfigData config;

    private SpecificKindItemStackComparator(final String kindName, final ConfigData config) {
        this.kindName = kindName;
        this.config = config;
    }

    /** @return the (cached) comparator for a kind, reading the current {@link Config}. */
    public static SpecificKindItemStackComparator getComparatorFor(final String kindName) {
        return CACHE.computeIfAbsent(kindName, k -> new SpecificKindItemStackComparator(k, Config.get()));
    }

    /** Test seam: build a comparator directly from a given config (no {@link Config} static read). */
    static SpecificKindItemStackComparator forConfig(final String kindName, final ConfigData config) {
        return new SpecificKindItemStackComparator(kindName, config);
    }

    @Override
    public int compare(@Nonnull final ItemStack itemStack1, @Nonnull final ItemStack itemStack2) {
        return OwnerOrder.compare(config, kindName, getModName(itemStack1), getModName(itemStack2));
    }
}
