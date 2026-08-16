package com.mrfuzzihead.unidict.crafting;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over the vanilla ShapelessRecipes#recipeOutput field
 * (private final, SRG field_77580_b). Also covers every subclass --
 * notably ShapelessOreRecipe which extends ShapelessRecipes.
 *
 * Even though the field is final in Java, Mixin @Accessor can
 * set it via field-offset access -- proven across GTNH modpack.
 */
public interface IShapelessRecipesAccessor {

    ItemStack getRecipeOutput();

    void setRecipeOutput(ItemStack output);
}
