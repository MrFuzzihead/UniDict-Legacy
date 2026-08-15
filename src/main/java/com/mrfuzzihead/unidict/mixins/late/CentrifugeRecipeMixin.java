package com.mrfuzzihead.unidict.mixins.late;

import java.util.Map;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mrfuzzihead.unidict.forestry.ICentrifugeRecipeAccessor;

import forestry.factory.recipes.CentrifugeRecipe;

/**
 * Accessor mixin (late, Forestry-gated) for {@link CentrifugeRecipe} (docs/PLAN.md §M7 #2, mixin
 * summary table) — realises {@link ICentrifugeRecipeAccessor} for the Forestry centrifuge rewrite.
 * Exposes the private {@code outputs} product map so {@code ForestryIntegration} can canonicalise its
 * contents <em>in place</em> (clear + putAll) without ever removing or rebuilding the recipe (BB-3);
 * {@code CentrifugeRecipe.getProducts(Random)} reads exactly this map, so the rewrite is what the
 * machine actually rolls.
 *
 * <p>
 * Late phase + {@code TargetMods.FORESTRY} required-mod gating (the proven EIO/Railcraft/TE pattern):
 * {@code CentrifugeRecipe} is a Forestry class that only exists when Forestry is loaded, unlike the
 * early {@code ShapedOreRecipeMixin} (a Forge class, always present). {@code outputs} is a mod field
 * whose name is identical in dev and SRG, so {@code remap=false}; the instance accessor follows the
 * {@code protected abstract} rule from the {@code ChestGenHooksMixin} toolchain notes.
 */
@Mixin(CentrifugeRecipe.class)
abstract class CentrifugeRecipeMixin implements ICentrifugeRecipeAccessor {

    @Accessor(value = "outputs", remap = false)
    protected abstract Map<ItemStack, Float> accessor$getProducts();

    @Override
    public Map<ItemStack, Float> unidict$getProducts() {
        return accessor$getProducts();
    }
}
