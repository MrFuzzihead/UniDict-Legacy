package com.mrfuzzihead.unidict.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import cofh.thermalexpansion.util.crafting.PulverizerManager;

/**
 * M0 "Spike B": {@code @Invoker} for Thermal Expansion's package-private
 * {@code RecipePulverizer(ItemStack, ItemStack, ItemStack, int, int)} constructor.
 */
@Mixin(PulverizerManager.RecipePulverizer.class)
public abstract class RecipePulverizerInvoker {

    @Invoker("<init>")
    static PulverizerManager.RecipePulverizer unidict$new(ItemStack input, ItemStack primaryOutput,
        ItemStack secondaryOutput, int secondaryChance, int energy) {
        throw new AssertionError();
    }
}
