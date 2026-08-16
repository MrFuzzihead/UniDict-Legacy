package com.mrfuzzihead.unidict.te;

import net.minecraft.item.ItemStack;

import cofh.thermalexpansion.util.crafting.FurnaceManager;

/**
 * T2-seam factory over TE's {@code FurnaceManager.RecipeFurnace} package-private constructor.
 *
 * <p>
 * This is the outward-facing side of the M0-Spike-B {@code @Invoker} mixin
 * ({@code RecipeFurnaceInvoker} in {@code mixins.late}). Mixin packages are <em>closed</em>: a
 * non-mixin class may not reference a class in {@code com.mrfuzzihead.unidict.mixins.late}
 * ({@code IllegalClassLoadError}), so the invoker is surfaced the same way the accessors are — the
 * {@code @Mixin} implements this (plain) interface and {@code TEIntegration} casts the value object to
 * it. The {@code @Invoker} is then invoked from <em>inside</em> the mixin package (legal) and the
 * rebuilt recipe is handed out here.
 */
public interface IRecipeFurnaceFactory {

    /** Rebuilds a {@code RecipeFurnace} with the mapped output via the constructor {@code @Invoker}. */
    FurnaceManager.RecipeFurnace rebuildFurnace(ItemStack input, ItemStack output, int energy);
}
