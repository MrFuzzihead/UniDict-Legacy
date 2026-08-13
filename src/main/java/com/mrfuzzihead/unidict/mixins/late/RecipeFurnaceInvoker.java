package com.mrfuzzihead.unidict.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import cofh.thermalexpansion.util.crafting.FurnaceManager;

/**
 * M0 "Spike B": prove Sponge {@code @Invoker} can invoke Thermal Expansion's package-private
 * constructor {@code RecipeFurnace(ItemStack, ItemStack, int)}. TEIntegration (M7) needs this to
 * reconstruct a recipe with a unified output. Late + mod-gated: TE only exists when loaded.
 */
@Mixin(FurnaceManager.RecipeFurnace.class)
public abstract class RecipeFurnaceInvoker {

    @Invoker("<init>")
    static FurnaceManager.RecipeFurnace unidict$new(ItemStack input, ItemStack output, int energy) {
        throw new AssertionError();
    }
}
