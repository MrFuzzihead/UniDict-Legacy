package com.mrfuzzihead.unidict.oredict;

import net.minecraft.item.ItemStack;

/**
 * T2 seam for hashing an {@link ItemStack} into the {@code int} key space shared with the Ore
 * Dictionary's {@code stackToId}. Mirrors the "MetaItem provider" seam from docs/PLAN.md §0: the live
 * implementation is the M4 {@code MetaItem} glue (registry id + damage arithmetic via the pure
 * {@code MetaKey}); tests inject a fake that keys stacks by identity, so {@code UniOreDictionary}
 * (M3) is fully T2-drivable with no Minecraft statics.
 */
@FunctionalInterface
public interface MetaItemProvider {

    /** @return the stable {@code int} key for {@code stack}. */
    int of(ItemStack stack);
}
