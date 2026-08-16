package com.mrfuzzihead.unidict.galacticraft;

import net.minecraft.item.ItemStack;

public final class FakeShapelessOreRecipeAccessor implements IShapelessOreRecipeAccessor {

    private ItemStack output;

    public FakeShapelessOreRecipeAccessor(final ItemStack output) {
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
