package com.mrfuzzihead.unidict.common;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.pure.config.ConfigData;
import com.mrfuzzihead.unidict.pure.config.OwnerOrder;

/**
 * Sorts a kind's entries by owner-mod priority from the <b>current</b> {@link Config} owner model.
 * Ported from {@code wanion.unidict.common.SpecificKindItemStackComparator} (WanionCane, MPL-2.0);
 * ordering flows through the pure {@link OwnerOrder} (T1-tested).
 *
 * <p>
 * <b>Live config (docs/CODE_REVIEW.md BUG-12).</b> The comparator holds a {@link Supplier} of the config,
 * never a captured snapshot, so a cached instance keeps following {@code Config.load(...)} — the previous
 * implementation captured the {@code ConfigData} at first use, which would silently order by the old owner
 * priorities/blacklists after a reload. {@code Config::get} and {@code Util::getModName} are injected in
 * production; {@link #forTesting} lets a test supply a mutable holder and a registry-free owner resolver.
 *
 * <p>
 * The keep-one-entry black-set side effect upstream accumulated here is <b>not preserved</b>: the live
 * NEI-hide decision reads the exemption blacklists directly via
 * {@link com.mrfuzzihead.unidict.resource.ResourceHandler#isModBlacklisted} and the kind blacklist, so this
 * comparator only orders.
 */
public final class SpecificKindItemStackComparator implements Comparator<ItemStack> {

    private static final Map<String, SpecificKindItemStackComparator> CACHE = new ConcurrentHashMap<>();

    private final String kindName;
    private final Supplier<ConfigData> config;
    private final Function<ItemStack, String> ownerOf;

    private SpecificKindItemStackComparator(final String kindName, final Supplier<ConfigData> config,
        final Function<ItemStack, String> ownerOf) {
        this.kindName = kindName;
        this.config = config;
        this.ownerOf = ownerOf;
    }

    /**
     * @param kindName the kind whose per-kind owner override (or the global order) applies
     * @return the cached comparator for a kind; it holds no config snapshot, so caching is reload-safe
     */
    public static SpecificKindItemStackComparator getComparatorFor(final String kindName) {
        return CACHE
            .computeIfAbsent(kindName, k -> new SpecificKindItemStackComparator(k, Config::get, Util::getModName));
    }

    /**
     * Test seam: build a comparator over a live config supplier and an owner resolver, with no
     * {@link Config} / registry statics. The supplier is read on every comparison, which is exactly what
     * the production path does.
     */
    static SpecificKindItemStackComparator forTesting(final String kindName, final Supplier<ConfigData> config,
        final Function<ItemStack, String> ownerOf) {
        return new SpecificKindItemStackComparator(kindName, config, ownerOf);
    }

    @Override
    public int compare(@Nonnull final ItemStack itemStack1, @Nonnull final ItemStack itemStack2) {
        return OwnerOrder.compare(config.get(), kindName, ownerOf.apply(itemStack1), ownerOf.apply(itemStack2));
    }
}
