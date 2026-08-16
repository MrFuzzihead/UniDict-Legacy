package com.mrfuzzihead.unidict.oredict;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * Read-only view of Forge's {@link net.minecraftforge.oredict.OreDictionary} internals — the five
 * private static caches that {@code UniOreDictionary} needs. This is the T2 seam described in
 * docs/PLAN.md §0: a live implementation is fed by {@code OreDictionaryMixin} into
 * {@link OreDictionaryBridge}, and tests use a {@code FakeOreDictionaryAccessor}.
 *
 * <p>
 * This is a plain seam interface and deliberately lives OUTSIDE the mixin packages (which hold
 * only {@code @Mixin} classes). See docs/TestPlan.md rule 6.
 */
public interface IOreDictionaryAccessor {

    Map<String, Integer> getNameToId();

    List<String> getIdToName();

    List<List<ItemStack>> getIdToStack();

    List<List<ItemStack>> getIdToStackUn();

    Map<Integer, List<Integer>> getStackToId();
}
