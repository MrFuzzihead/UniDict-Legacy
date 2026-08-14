package com.mrfuzzihead.unidict.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mrfuzzihead.unidict.te.IRecipePulverizerFactory;

import cofh.thermalexpansion.util.crafting.PulverizerManager;

/**
 * M0 "Spike B": {@code @Invoker} for Thermal Expansion's package-private
 * {@code RecipePulverizer(ItemStack, ItemStack, ItemStack, int, int)} constructor.
 *
 * <p>
 * Implements {@link IRecipePulverizerFactory} so {@code TEIntegration} can reach the constructor
 * without referencing this mixin class directly (mixin packages are closed to non-mixin callers).
 */
@Mixin(PulverizerManager.RecipePulverizer.class)
public abstract class RecipePulverizerInvoker implements IRecipePulverizerFactory {

    @Invoker("<init>")
    static PulverizerManager.RecipePulverizer unidict$new(ItemStack input, ItemStack primaryOutput,
        ItemStack secondaryOutput, int secondaryChance, int energy) {
        throw new AssertionError();
    }

    @Override
    public PulverizerManager.RecipePulverizer rebuildPulverizer(final ItemStack input, final ItemStack primaryOutput,
        final ItemStack secondaryOutput, final int secondaryChance, final int energy) {
        return unidict$new(input, primaryOutput, secondaryOutput, secondaryChance, energy);
    }
}
