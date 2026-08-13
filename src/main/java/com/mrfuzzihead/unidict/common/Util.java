package com.mrfuzzihead.unidict.common;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameData;

/**
 * Small MC-touching helpers shared across integrations. Ported from
 * {@code wanion.unidict.common.Util} (WanionCane, MPL-2.0), with ONLY the reflective helpers removed:
 * {@code getField}/{@code setField} (the last direct reflection in core; the Ore Dictionary bridge
 * and accessor mixins replaced it — see docs/PLAN.md §M3).
 *
 * <p>
 * The config-coupled {@code itemStackComparatorByModName} and {@link SpecificKindItemStackComparator}
 * are ported in the config rework commit (M2 commit 2): they depend on the owner-of-kind maps that
 * arrive with the new {@code Config}.
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
}
