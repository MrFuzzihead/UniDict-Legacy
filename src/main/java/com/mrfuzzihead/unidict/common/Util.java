package com.mrfuzzihead.unidict.common;

import java.util.Comparator;

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

    private Util() {}

    /**
     * The registry namespace of an item's owning mod, e.g. {@code "minecraft"} for
     * {@code minecraft:iron_ingot}.
     */
    public static String getModName(final ItemStack itemStack) {
        final String name = GameData.getItemRegistry()
            .getNameForObject(itemStack.getItem());
        return name.substring(0, name.indexOf(58));
    }

    /**
     * Comparator that orders {@link ItemStack}s by the global owner-mod priority in the current
     * {@link Config}. Upstream exposed this as a {@code static final} built at class-load; because
     * config loads at FML pre-init, it is a method that resolves against the live config so ordering
     * is never captured before config is ready.
     */
    public static Comparator<ItemStack> itemStackComparatorByModName() {
        return (stack1, stack2) -> Long.compare(
            OwnerOrder.globalIndexOf(Config.get(), getModName(stack1)),
            OwnerOrder.globalIndexOf(Config.get(), getModName(stack2)));
    }
}
