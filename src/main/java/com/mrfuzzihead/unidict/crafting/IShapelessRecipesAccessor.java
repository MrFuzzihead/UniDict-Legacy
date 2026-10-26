package com.mrfuzzihead.unidict.crafting;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over the vanilla {@code ShapelessRecipes#recipeOutput} field. Also covers every subclass —
 * notably {@code ShapelessOreRecipe}, which extends {@code ShapelessRecipes}.
 *
 * <p>
 * Method names are {@code unidict$}-prefixed so the mixin adds <b>new</b> methods instead of shadowing
 * the target's own {@code IRecipe#getRecipeOutput()} (docs/CODE_REVIEW.md BUG-20). Live impl is
 * {@code ShapelessRecipesMixin} ({@code mixins.early}, {@code remap=true}); T2 tests use
 * {@code FakeShapelessRecipesAccessor}.
 */
public interface IShapelessRecipesAccessor {

    ItemStack unidict$getRecipeOutput();

    void unidict$setRecipeOutput(ItemStack output);
}
