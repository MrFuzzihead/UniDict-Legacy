package com.mrfuzzihead.unidict.mixins.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.ic2.IAdvRecipeAccessor;

/**
 * Accessor mixin for {@code ic2.core.AdvRecipe}, exposing its {@code public final output} field so
 * {@code CraftingIntegration} can canonicalise IC2's shaped recipes (e.g. 9× tiny-copper-dust →
 * copper-dust, 9 ingots → block) in place. The field is {@code final} at the Java level, but the
 * mutator writes it from inside the declaring class (where the JVM allows instance-final writes) —
 * this is the only mechanism that reliably changes what NEI and {@code findMatchingRecipe} observe;
 * read-side {@code @Inject} return replacement has proven ineffective in this GTNH mixin runtime.
 * Mod-gated (IC2).
 */
@Mixin(value = ic2.core.AdvRecipe.class)
public abstract class AdvRecipeMixin implements IAdvRecipeAccessor {

    @Accessor(value = "output", remap = false)
    protected abstract ItemStack accessor$getOutput();

    @Accessor(value = "output", remap = false)
    protected abstract void accessor$setOutput(ItemStack output);

    @Override
    public ItemStack unidict$getOutput() {
        return accessor$getOutput();
    }

    @Override
    public void unidict$setOutput(final ItemStack output) {
        accessor$setOutput(output);
    }
}
