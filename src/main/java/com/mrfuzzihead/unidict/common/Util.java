package com.mrfuzzihead.unidict.common;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.pure.config.OwnerOrder;

import cpw.mods.fml.common.registry.GameData;

/**
 * Small MC-touching helpers shared across integrations. Ported from
 * {@code wanion.unidict.common.Util} (WanionCane, MPL-2.0), with ONLY the reflective helpers removed:
 * {@code getField}/{@code setField} (the last direct reflection in core; the Ore Dictionary bridge
 * and accessor mixins replaced it — see docs/PLAN.md §M3).
 *
 * <p>
 * The config-coupled {@code itemStackComparatorByModName} (and {@link SpecificKindItemStackComparator})
 * were ported with the M2 config rework (commit 2): they order by the new {@code Config} owner model
 * through the pure {@link OwnerOrder}, whose ordering is T1-tested independently of MC.
 */
public final class Util {

    /** Owner reported for a stack whose registry entry has no namespaced {@code modid:name}. */
    public static final String UNKNOWN_MOD = "unknown";

    /** Item owner namespaces are stable across registry-id remaps; cache them for the sort/NEI hot paths. */
    private static final Map<Item, String> OWNER_CACHE = new ConcurrentHashMap<>();

    private Util() {}

    /**
     * The registry namespace of an item's owning mod, e.g. {@code "minecraft"} for
     * {@code minecraft:iron_ingot}.
     *
     * <p>
     * Defensive by design: this runs on hot paths (sorting every unified container, the NEI hide pass,
     * report rendering), so a null stack, an unregistered item (no registry name) or a name without a
     * namespace must never throw — a single odd Ore Dictionary entry used to be able to abort the
     * resource pipeline (docs/CODE_REVIEW.md BUG-7). Such stacks report {@link #UNKNOWN_MOD}, which no
     * owner list or hide blacklist contains, so they simply sort last / stay visible.
     */
    public static String getModName(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) return UNKNOWN_MOD;
        final Item item = itemStack.getItem();
        final String cached = OWNER_CACHE.get(item);
        if (cached != null) return cached;
        final String name = GameData.getItemRegistry()
            .getNameForObject(item);
        if (name == null) return UNKNOWN_MOD;
        final int colon = name.indexOf(':');
        final String owner = colon < 0 ? name : name.substring(0, colon);
        // Do not cache UNKNOWN_MOD: the registry may not be fully populated when this is first called.
        OWNER_CACHE.putIfAbsent(item, owner);
        return owner;
    }

    /**
     * Comparator that orders {@link ItemStack}s by the global owner-mod priority in the current
     * {@link Config}, with a deterministic lexical tiebreak for mods absent from the list (via
     * {@link OwnerOrder#compareGlobal}). Upstream exposed this as a {@code static final} built at
     * class-load; because config loads at FML pre-init, it is a method that resolves against the live
     * config so ordering is never captured before config is ready.
     */
    public static Comparator<ItemStack> itemStackComparatorByModName() {
        return (stack1, stack2) -> OwnerOrder.compareGlobal(Config.get(), getModName(stack1), getModName(stack2));
    }
}
