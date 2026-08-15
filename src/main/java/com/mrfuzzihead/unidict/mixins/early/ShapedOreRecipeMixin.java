package com.mrfuzzihead.unidict.mixins.early;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.forestry.IShapedOreRecipeAccessor;

/**
 * Accessor mixin for {@link ShapedOreRecipe} (docs/PLAN.md §M7 #2, mixin summary table) — realises
 * {@link IShapedOreRecipeAccessor} for the Forestry carpenter integration. Exposes the private
 * {@code output} field so {@code ForestryIntegration} can rewrite a carpenter grid recipe's output in
 * place; Forestry's {@code ShapedRecipeCustom} extends {@code ShapedOreRecipe} and inherits this field,
 * and {@code getRecipeOutput()} reads it — so writing it canonicalises the recipe without removing or
 * rebuilding anything (BB-3).
 *
 * <p>
 * {@code output} is a Forge-added field (its name is identical in dev and SRG), so {@code remap=false};
 * both instance accessors follow the proven {@code ChestGenHooksMixin} rules — {@code protected abstract}
 * (the Mixin transformer rejects non-abstract instance {@code @Accessor}s). The public {@code @Override}
 * methods use the {@code unidict$} prefix so they never collide with the inherited
 * {@code IRecipe#getRecipeOutput()}.
 */
@Mixin(ShapedOreRecipe.class)
public abstract class ShapedOreRecipeMixin implements IShapedOreRecipeAccessor {

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
