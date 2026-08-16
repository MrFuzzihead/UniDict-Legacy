package com.mrfuzzihead.unidict.galacticraft;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over {@code net.minecraftforge.oredict.ShapelessOreRecipe#output} (a <b>private</b> Forge-added
 * field — 1.7.10 Forge; {@code getRecipeOutput()} reads it but no setter exists).
 *
 * <p>
 * <b>Does not share the {@code ShapedOreRecipe} base.</b> In 1.7.10 {@code ShapelessOreRecipe}
 * {@code implements IRecipe} directly and owns its own {@code output} field, so the
 * {@code ShapedOreRecipeMixin} does <b>not</b> apply here — a dedicated early accessor mixin is
 * required. Live implementation is {@code com.mrfuzzihead.unidict.mixins.early.ShapelessOreRecipeMixin}
 * (early, {@code remap = false}: Forge-defined field keeps its name in dev and SRG). Galacticraft's
 * shapeless compressor recipes are {@code ShapelessOreRecipe} instances, so mutating {@code output}
 * in place is exactly the non-destructive (BB-3) rewrite the compressor path needs.
 *
 * <p>
 * Method names are prefixed so they never collide with the inherited {@code IRecipe#getRecipeOutput()}.
 * T2 tests use {@link com.mrfuzzihead.unidict.galacticraft.FakeShapelessOreRecipeAccessor}.
 */
public interface IShapelessOreRecipeAccessor {

    ItemStack unidict$getOutput();

    void unidict$setOutput(ItemStack output);
}
