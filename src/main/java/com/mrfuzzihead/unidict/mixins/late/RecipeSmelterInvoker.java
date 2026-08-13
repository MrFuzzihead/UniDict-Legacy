package com.mrfuzzihead.unidict.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import cofh.thermalexpansion.util.crafting.SmelterManager;

/**
 * M0 "Spike B": {@code @Invoker} for Thermal Expansion's package-private
 * {@code RecipeSmelter(ItemStack, ItemStack, ItemStack, ItemStack, int, int)} constructor.
 */
@Mixin(SmelterManager.RecipeSmelter.class)
public abstract class RecipeSmelterInvoker {

    @Invoker("<init>")
    static SmelterManager.RecipeSmelter unidict$new(ItemStack primaryInput, ItemStack secondaryInput,
        ItemStack primaryOutput, ItemStack secondaryOutput, int secondaryChance, int energy) {
        throw new AssertionError();
    }
}
