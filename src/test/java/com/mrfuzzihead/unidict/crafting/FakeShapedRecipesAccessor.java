package com.mrfuzzihead.unidict.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;

public final class FakeShapedRecipesAccessor extends ShapedRecipes implements IShapedRecipesAccessor {

    private ItemStack item;

    public FakeShapedRecipesAccessor(final ItemStack output) {
        super(3, 3, new ItemStack[9], output);
        this.item = output;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return item;
    }

    @Override
    public void setRecipeOutput(final ItemStack output) {
        this.item = output;
    }
}
