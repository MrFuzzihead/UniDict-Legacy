package com.mrfuzzihead.unidict.te;

import net.minecraft.item.ItemStack;

import cofh.thermalexpansion.util.crafting.PulverizerManager;

/**
 * T2-seam factory over TE's {@code PulverizerManager.RecipePulverizer} package-private constructor.
 *
 * <p>
 * Outward-facing side of the M0-Spike-B {@code @Invoker} mixin ({@code RecipePulverizerInvoker} in
 * {@code mixins.late}). Mixin packages are <em>closed</em>, so the invoker is surfaced like the
 * accessors: the {@code @Mixin} implements this plain interface and {@code TEIntegration} casts the
 * value object to it. The {@code @Invoker} is invoked from <em>inside</em> the mixin package (legal)
 * and the rebuilt recipe is handed out here.
 */
public interface IRecipePulverizerFactory {

    /** Rebuilds a {@code RecipePulverizer} with the mapped outputs via the constructor {@code @Invoker}. */
    PulverizerManager.RecipePulverizer rebuildPulverizer(ItemStack input, ItemStack primaryOutput,
        ItemStack secondaryOutput, int secondaryChance, int energy);
}
