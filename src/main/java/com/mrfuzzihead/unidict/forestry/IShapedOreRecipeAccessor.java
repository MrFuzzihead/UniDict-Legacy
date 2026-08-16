package com.mrfuzzihead.unidict.forestry;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over {@code net.minecraftforge.oredict.ShapedOreRecipe#output} (a <b>private</b> Forge-added
 * field — 1.7.10 Forge, {@code getRecipeOutput()} reads it, but no setter exists).
 *
 * <p>
 * Live implementation is {@code ShapedOreRecipeMixin} ({@code mixins.early}, {@code remap=false}:
 * Forge-defined fields keep their names in dev and SRG); Forestry's carpenter grid recipes
 * ({@code forestry.core.recipes.ShapedRecipeCustom}) inherit this field, so mutating it in place is
 * exactly what the carpenter output rewrite needs — no recipe is ever removed or rebuilt (BB-3).
 *
 * <p>
 * Method names are prefixed to never collide with the inherited {@code IRecipe#getRecipeOutput()}.
 * T2 tests use {@link com.mrfuzzihead.unidict.forestry.FakeShapedOreRecipeAccessor}.
 */
public interface IShapedOreRecipeAccessor {

    ItemStack unidict$getOutput();

    void unidict$setOutput(ItemStack output);
}
