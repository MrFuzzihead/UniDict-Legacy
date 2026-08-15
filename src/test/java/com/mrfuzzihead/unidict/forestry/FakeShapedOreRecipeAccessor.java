package com.mrfuzzihead.unidict.forestry;

import net.minecraft.item.ItemStack;

public final class FakeShapedOreRecipeAccessor implements IShapedOreRecipeAccessor {

    private ItemStack output;

    public FakeShapedOreRecipeAccessor(final ItemStack output) {
        this.output = output;
    }

    @Override
    public ItemStack unidict$getOutput() {
        return output;
    }

    @Override
    public void unidict$setOutput(final ItemStack output) {
        this.output = output;
    }
}
