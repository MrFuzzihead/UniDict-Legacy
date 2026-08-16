package com.mrfuzzihead.unidict.mixins.early;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.galacticraft.IShapelessOreRecipeAccessor;

/**
 * Accessor mixin for {@link ShapelessOreRecipe} — realises {@link IShapelessOreRecipeAccessor} for the
 * Galacticraft compressor integration. Exposes the private {@code output} field so the compressor's
 * shapeless recipes can be canonicalised in place (no remove, no rebuild — BB-3).
 *
 * <p>
 * In 1.7.10 {@code ShapelessOreRecipe} {@code implements IRecipe} directly (it does <b>not</b> extend
 * {@code ShapedOreRecipe}), so this is a separate mixin from {@code ShapedOreRecipeMixin}. {@code output}
 * is a Forge-added field (identical name in dev and SRG), so {@code remap=false}; both accessors follow
 * the proven {@code ChestGenHooksMixin} rules — {@code protected abstract} (the Mixin transformer rejects
 * non-abstract instance {@code @Accessor}s). Public {@code @Override} methods use the {@code unidict$}
 * prefix so they never collide with the inherited {@code IRecipe#getRecipeOutput()}.
 */
@Mixin(ShapelessOreRecipe.class)
public abstract class ShapelessOreRecipeMixin implements IShapelessOreRecipeAccessor {

    @Accessor(value = "output", remap = false)
    protected abstract ItemStack accessor$getOutput();

    @Accessor(value = "output", remap = false)
    protected abstract void accessor$setOutput(ItemStack output);

    @Override
    public ItemStack unidict$getOutput() {
        return accessor$getOutput();
    }

    @Override
    public void unidict$setOutput(final ItemStack output) {
        accessor$setOutput(output);
    }
}
