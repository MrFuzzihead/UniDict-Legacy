package com.mrfuzzihead.unidict.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mrfuzzihead.unidict.te.IRecipeFurnaceFactory;

import cofh.thermalexpansion.util.crafting.FurnaceManager;

/**
 * M0 "Spike B": prove Sponge {@code @Invoker} can invoke Thermal Expansion's package-private
 * constructor {@code RecipeFurnace(ItemStack, ItemStack, int)}. TEIntegration (M7) needs this to
 * reconstruct a recipe with a unified output. Late + mod-gated: TE only exists when loaded.
 *
 * <p>
 * Implements {@link IRecipeFurnaceFactory} so {@code TEIntegration} can reach the constructor without
 * referencing this mixin class directly (mixin packages are closed to non-mixin callers). The
 * {@code @Invoker} is invoked from here — inside the mixin package — and the rebuilt recipe is handed
 * out via the interface.
 */
@Mixin(FurnaceManager.RecipeFurnace.class)
public abstract class RecipeFurnaceInvoker implements IRecipeFurnaceFactory {

    @Invoker("<init>")
    static FurnaceManager.RecipeFurnace unidict$new(ItemStack input, ItemStack output, int energy) {
        throw new AssertionError();
    }

    @Override
    public FurnaceManager.RecipeFurnace rebuildFurnace(final ItemStack input, final ItemStack output,
        final int energy) {
        return unidict$new(input, output, energy);
    }
}
