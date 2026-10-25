package com.mrfuzzihead.unidict.integration;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.crafting.IShapedRecipesAccessor;
import com.mrfuzzihead.unidict.galacticraft.IShapelessOreRecipeAccessor;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import micdoodle8.mods.galacticraft.api.recipe.CompressorRecipes;

/**
 * M8-build-better Galacticraft compressor rewrite (docs/PLAN.md §M8 / INTEGRATIONS.md). Rewrites the Ingot
 * Compressor / Electric Ingot Compressor outputs (shared {@code CompressorRecipes.getRecipeList()}) to the
 * canonical main entry, non-destructively (BB-3). GC registers its configurable compressor recipes at
 * {@code FMLServerStarting} ({@code RecipeManagerGC.setConfigurableRecipes}) — later than any
 * {@code LoadStage} — so this is invoked from {@code UniDict.serverStarted}, not the module executor.
 * Both recipe types ({@code ShapedRecipes} and Forge {@code ShapelessOreRecipe}) hold a mutable output,
 * written in place through their early accessor seams. GC's Electric/Arc Furnace reuse the vanilla
 * {@code FurnaceRecipes} map and are covered by {@link FurnaceIntegration} (LOAD_COMPLETE) instead.
 */
final class GalacticraftIntegration {

    private GalacticraftIntegration() {}

    /** Dev seam self-check (docs/CODE_REVIEW.md BUG-9), asserted by the verifyServer harness. */
    static void checkSeams() {
        VerifyHarness.checkSeam("galacticraft/shapedRecipes", IShapedRecipesAccessor.class, ShapedRecipes.class);
        VerifyHarness
            .checkSeam("galacticraft/shapelessOreRecipe", IShapelessOreRecipeAccessor.class, ShapelessOreRecipe.class);
    }

    static int runCompressor() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walk.
        if (resourceHandler == null || resourceHandler.resources.isEmpty() || !Config.galacticraft()) return 0;
        final UnaryOperator<ItemStack> resolveMain = resourceHandler::getMainItemStack;
        int rewritten = 0;
        for (final IRecipe recipe : CompressorRecipes.getRecipeList()) {
            // Both compressor recipe types hold a mutable output; rewrite in place (BB-3).
            if (recipe instanceof ShapedRecipes) {
                final IShapedRecipesAccessor accessor = (IShapedRecipesAccessor) recipe;
                rewritten += rewriteOutput(
                    accessor::unidict$getRecipeOutput,
                    accessor::unidict$setRecipeOutput,
                    resolveMain);
            } else if (recipe instanceof ShapelessOreRecipe) {
                final IShapelessOreRecipeAccessor accessor = (IShapelessOreRecipeAccessor) recipe;
                rewritten += rewriteOutput(accessor::unidict$getOutput, accessor::unidict$setOutput, resolveMain);
            }
        }
        // Record every run: RewriteJournal accumulates the per-pass delta into one line per machine, so a
        // later world load (GC re-registers at every FMLServerStarting) adds only newly-rewritten recipes
        // instead of freezing the first run's count (docs/CODE_REVIEW.md BUG-13).
        RewriteJournal.record("galacticraft", "compressor", rewritten);
        UniDict.LOG.info(
            "Galacticraft Integration: rewrote outputs of " + rewritten
                + " Galacticraft compressor products to their canonical entries.");
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Galacticraft", "machine=compressor", "rewritten=" + rewritten);
        }
        return rewritten;
    }

    /** Non-destructive in-place output rewrite (BB-3); {@code @return} 1 if rewritten, else 0. */
    static int rewriteOutput(final Supplier<ItemStack> getOutput, final Consumer<ItemStack> setOutput,
        final UnaryOperator<ItemStack> resolveMain) {
        final ItemStack original = getOutput.get();
        if (original == null) return 0;
        final ItemStack mapped = resolveMain.apply(original);
        if (mapped != original) {
            setOutput.accept(mapped);
            return 1;
        }
        return 0;
    }

    /** List-backed convenience over {@link #rewriteOutput}; {@code @return} outputs changed. */
    static int rewriteOutputs(final List<?> recipes, final AccessorPairFactory adapter,
        final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final Object recipe : recipes) {
            final AccessorPair pair = adapter.create(recipe);
            if (pair == null) continue;
            rewritten += rewriteOutput(pair.getter, pair.setter, resolveMain);
        }
        return rewritten;
    }

    /** Small adapter that wires a recipe object to its output getter/setter (see {@link #rewriteOutputs}). */
    interface AccessorPairFactory {

        AccessorPair create(Object recipe);
    }

    /** A getter/setter pair bound to a recipe's mutable output field. */
    static final class AccessorPair {

        final Supplier<ItemStack> getter;
        final Consumer<ItemStack> setter;

        AccessorPair(final Supplier<ItemStack> getter, final Consumer<ItemStack> setter) {
            this.getter = getter;
            this.setter = setter;
        }
    }
}
