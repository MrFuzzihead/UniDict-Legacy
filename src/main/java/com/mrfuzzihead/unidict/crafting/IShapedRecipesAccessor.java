package com.mrfuzzihead.unidict.crafting;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over the vanilla {@code ShapedRecipes#recipeOutput} field (private). Also covers every subclass
 * — notably {@code ShapedOreRecipe}, which extends {@code ShapedRecipes} and inherits the same field.
 *
 * <p>
 * Method names are {@code unidict$}-prefixed so the mixin adds <b>new</b> methods instead of shadowing the
 * target's own {@code IRecipe#getRecipeOutput()} (docs/CODE_REVIEW.md BUG-20). Live impl is
 * {@code ShapedRecipesMixin} ({@code mixins.early}, {@code remap=true}); T2 tests use
 * {@code FakeShapedRecipesAccessor}.
 */
public interface IShapedRecipesAccessor {

    ItemStack unidict$getRecipeOutput();

    void unidict$setRecipeOutput(ItemStack output);
}
