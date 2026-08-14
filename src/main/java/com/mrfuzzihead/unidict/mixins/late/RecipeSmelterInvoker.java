package com.mrfuzzihead.unidict.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mrfuzzihead.unidict.te.IRecipeSmelterFactory;

import cofh.thermalexpansion.util.crafting.SmelterManager;

/**
 * M0 "Spike B": {@code @Invoker} for Thermal Expansion's package-private
 * {@code RecipeSmelter(ItemStack, ItemStack, ItemStack, ItemStack, int, int)} constructor.
 *
 * <p>
 * Implements {@link IRecipeSmelterFactory} so {@code TEIntegration} can reach the constructor without
 * referencing this mixin class directly (mixin packages are closed to non-mixin callers).
 */
@Mixin(SmelterManager.RecipeSmelter.class)
public abstract class RecipeSmelterInvoker implements IRecipeSmelterFactory {

    @Invoker("<init>")
    static SmelterManager.RecipeSmelter unidict$new(ItemStack primaryInput, ItemStack secondaryInput,
        ItemStack primaryOutput, ItemStack secondaryOutput, int secondaryChance, int energy) {
        throw new AssertionError();
    }

    @Override
    public SmelterManager.RecipeSmelter rebuildSmelter(final ItemStack primaryInput, final ItemStack secondaryInput,
        final ItemStack primaryOutput, final ItemStack secondaryOutput, final int secondaryChance, final int energy) {
        return unidict$new(primaryInput, secondaryInput, primaryOutput, secondaryOutput, secondaryChance, energy);
    }
}
