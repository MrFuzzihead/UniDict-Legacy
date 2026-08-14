package com.mrfuzzihead.unidict.te;

import net.minecraft.item.ItemStack;

import cofh.thermalexpansion.util.crafting.SmelterManager;

/**
 * T2-seam factory over TE's {@code SmelterManager.RecipeSmelter} package-private constructor.
 *
 * <p>
 * Outward-facing side of the M0-Spike-B {@code @Invoker} mixin ({@code RecipeSmelterInvoker} in
 * {@code mixins.late}). Mixin packages are <em>closed</em>, so the invoker is surfaced like the
 * accessors: the {@code @Mixin} implements this plain interface and {@code TEIntegration} casts the
 * value object to it. The {@code @Invoker} is invoked from <em>inside</em> the mixin package (legal)
 * and the rebuilt recipe is handed out here.
 */
public interface IRecipeSmelterFactory {

    /** Rebuilds a {@code RecipeSmelter} with the mapped outputs via the constructor {@code @Invoker}. */
    SmelterManager.RecipeSmelter rebuildSmelter(ItemStack primaryInput, ItemStack secondaryInput,
        ItemStack primaryOutput, ItemStack secondaryOutput, int secondaryChance, int energy);
}
