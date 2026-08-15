package com.mrfuzzihead.unidict.forestry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * T2 fake {@link ICarpenterRecipeAdder} — records every addition so tests can assert the two
 * recipe shapes and that nothing is ever removed/rebuilt (BB-3). No Forestry types reach the JUnit
 * classpath. {@code add*Recipe} always returns {@code true} (like the live adder, where
 * {@code ICarpenterManager.addRecipe} is void); the recorded {@code product} stacks are copies so a
 * later caller-side {@code stackSize} mutation can't leak into the assertions.
 */
public final class FakeCarpenterRecipeAdder implements ICarpenterRecipeAdder {

    private final List<ItemStack> singleProducts = new ArrayList<>();
    private final List<Object> singleIngredients = new ArrayList<>();
    private final List<ItemStack> gridProducts = new ArrayList<>();
    private final List<Object> gridIngredients = new ArrayList<>();

    @Override
    public boolean addSingleRecipe(final ItemStack product, final Object ingredient) {
        singleProducts.add(product.copy());
        singleIngredients.add(ingredient);
        return true;
    }

    @Override
    public boolean addGridRecipe(final ItemStack product, final Object ingredient) {
        gridProducts.add(product.copy());
        gridIngredients.add(ingredient);
        return true;
    }

    public List<ItemStack> singleProducts() {
        return new ArrayList<>(singleProducts);
    }

    public List<Object> singleIngredients() {
        return new ArrayList<>(singleIngredients);
    }

    public List<ItemStack> gridProducts() {
        return new ArrayList<>(gridProducts);
    }

    public List<Object> gridIngredients() {
        return new ArrayList<>(gridIngredients);
    }
}
