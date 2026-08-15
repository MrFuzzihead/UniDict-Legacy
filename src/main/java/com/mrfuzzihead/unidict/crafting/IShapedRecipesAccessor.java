package com.mrfuzzihead.unidict.crafting;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over the vanilla ShapedRecipes#recipeOutput field
 * (private, SRG field_77572_b). Also covers every subclass --
 * notably ShapedOreRecipe which extends ShapedRecipes and
 * inherits the same field.
 *
 * Live impl is ShapedRecipesMixin (mixins.early, remap=true);
 * T2 tests use FakeShapedRecipesAccessor.
 */
public interface IShapedRecipesAccessor {

    ItemStack getRecipeOutput();

    void setRecipeOutput(ItemStack output);
}
