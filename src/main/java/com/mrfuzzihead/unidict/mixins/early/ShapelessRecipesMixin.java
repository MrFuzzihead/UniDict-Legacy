package com.mrfuzzihead.unidict.mixins.early;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.crafting.IShapelessRecipesAccessor;

@Mixin(ShapelessRecipes.class)
public abstract class ShapelessRecipesMixin implements IShapelessRecipesAccessor {

    @Accessor("recipeOutput")
    protected abstract ItemStack accessor$getRecipeOutput();

    @Accessor("recipeOutput")
    protected abstract void accessor$setRecipeOutput(final ItemStack output);

    @Override
    public ItemStack getRecipeOutput() {
        return accessor$getRecipeOutput();
    }

    @Override
    public void setRecipeOutput(final ItemStack output) {
        accessor$setRecipeOutput(output);
    }
}
