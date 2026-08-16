package com.mrfuzzihead.unidict.crafting;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;

public final class FakeShapelessRecipesAccessor extends ShapelessRecipes implements IShapelessRecipesAccessor {

    private ItemStack item;

    public FakeShapelessRecipesAccessor(final ItemStack output) {
        super(output, new ArrayList<ItemStack>());
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
