package com.mrfuzzihead.unidict.integration;

/*
 * Rebuilt (and tightened) from wanion.unidict.integration.IEIntegration (WanionCane, MPL-2.0) as the
 * M6 IE machine rewrite (docs/PLAN.md §M6 #4). Rewrites every Immersive Engineering machine recipe
 * OUTPUT to the canonical (main) entry of its unified resource.
 * <p>Scope: the four machines with cross-mod unifiable (metal) outputs — the Arc Furnace, Blast
 * Furnace, Crusher and Metal Press. This is the complete IE crafting API surface that matters for
 * unification; the remaining {@code api.crafting} recipe types (Coke Oven, Bottling Machine,
 * Blueprint Crafting) produce mod-specific / non-metal outputs, so they are intentionally untouched.
 * <p>IE recipes are <b>immutable value objects</b> ({@code output} is a {@code final} field), so we
 * cannot mutate the output in place like Furnace/IC2. Instead each affected recipe is rebuilt with
 * the canonical output and replaced <em>at its index</em> in its recipe list
 * ({@link OutputRewriter#rewriteList} → {@code List.set}): the entry count and order are preserved
 * and no recipe is ever removed — fully non-destructive (BB-3). The upstream head-map-builder,
 * {@code UniOreDictionary.getFirstEntry} input lookup and {@code uniques} dedup are gone (they only
 * existed because upstream <em>removed and re-added</em> recipes; input rewriting is M5-deferred).
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import blusunrize.immersiveengineering.api.ComparableItemStack;
import blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.CrusherRecipe;
import blusunrize.immersiveengineering.api.crafting.MetalPressRecipe;

final class IEIntegration extends AbstractModuleThread {

    /** Arc Furnace: single final output; preserves the (optional) special recipe type. */
    private static final OutputRewriter.OutputView<ArcFurnaceRecipe> ARC_VIEW = new OutputRewriter.OutputView<ArcFurnaceRecipe>() {

        @Override
        public List<ItemStack> getItems(final ArcFurnaceRecipe recipe) {
            return single(recipe.output);
        }

        @Override
        public ArcFurnaceRecipe rebuild(final ArcFurnaceRecipe original, final List<ItemStack> mapped) {
            final ArcFurnaceRecipe rebuilt = new ArcFurnaceRecipe(
                mapped.get(0),
                original.oreInputString,
                original.slag,
                original.time,
                original.energyPerTick,
                (Object[]) original.additives);
            // Rebuilding drops the special-type tag in upstream; preserve it so recycling/obsidian
            // behaviour is unchanged by unification.
            if (original.specialRecipeType != null) rebuilt.setSpecialRecipeType(original.specialRecipeType);
            return rebuilt;
        }
    };

    /** Blast Furnace: single final output. */
    private static final OutputRewriter.OutputView<BlastFurnaceRecipe> BLAST_VIEW = new OutputRewriter.OutputView<BlastFurnaceRecipe>() {

        @Override
        public List<ItemStack> getItems(final BlastFurnaceRecipe recipe) {
            return single(recipe.output);
        }

        @Override
        public BlastFurnaceRecipe rebuild(final BlastFurnaceRecipe original, final List<ItemStack> mapped) {
            return new BlastFurnaceRecipe(mapped.get(0), original.input, original.time, original.slag);
        }
    };

    /**
     * Crusher: primary + (optional) secondary outputs. The secondary {@code output}/{@code chance}
     * fields are public and non-final, so after rebuilding the (final) primary we set them directly —
     * no {@code UniCrusherRecipe} subclass needed, unlike the upstream port.
     */
    private static final OutputRewriter.OutputView<CrusherRecipe> CRUSHER_VIEW = new OutputRewriter.OutputView<CrusherRecipe>() {

        @Override
        public List<ItemStack> getItems(final CrusherRecipe recipe) {
            final int extra = recipe.secondaryOutput == null ? 0 : recipe.secondaryOutput.length;
            if (extra == 0) return single(recipe.output);
            final List<ItemStack> items = new ArrayList<>(1 + extra);
            items.add(recipe.output);
            items.addAll(Arrays.asList(recipe.secondaryOutput));
            return items;
        }

        @Override
        public CrusherRecipe rebuild(final CrusherRecipe original, final List<ItemStack> mapped) {
            final CrusherRecipe rebuilt = new CrusherRecipe(mapped.get(0), original.input, original.energy);
            final int secondaryCount = original.secondaryOutput == null ? 0 : original.secondaryOutput.length;
            if (secondaryCount > 0) {
                final ItemStack[] secondary = new ItemStack[secondaryCount];
                for (int i = 0; i < secondaryCount; i++) secondary[i] = mapped.get(1 + i);
                rebuilt.secondaryOutput = secondary;
                rebuilt.secondaryChance = chanceCopy(original.secondaryChance, secondaryCount);
            }
            return rebuilt;
        }
    };

    /** Metal Press: single final output; preserves a non-default input size. */
    private static final OutputRewriter.OutputView<MetalPressRecipe> METAL_PRESS_VIEW = new OutputRewriter.OutputView<MetalPressRecipe>() {

        @Override
        public List<ItemStack> getItems(final MetalPressRecipe recipe) {
            return single(recipe.output);
        }

        @Override
        public MetalPressRecipe rebuild(final MetalPressRecipe original, final List<ItemStack> mapped) {
            final MetalPressRecipe rebuilt = new MetalPressRecipe(
                mapped.get(0),
                original.input,
                original.mold.stack,
                original.energy);
            if (original.inputSize != 1) rebuilt.setInputSize(original.inputSize);
            return rebuilt;
        }
    };

    IEIntegration() {
        super("IE", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: no unified resource -> the canonical lookup is a no-op.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.ie()) {
                final int rewritten = rewriteArc(resourceHandler) + rewriteBlast(resourceHandler)
                    + rewriteCrusher(resourceHandler)
                    + rewriteMetalPress(resourceHandler);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of " + rewritten + " IE machine recipes to their canonical entries.");
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(true, "integration=ie", "machines=4", "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "The world's engineer appears to be more immersive.";
    }

    private static int rewriteArc(final ResourceHandler resourceHandler) {
        final int n = OutputRewriter
            .rewriteList(ArcFurnaceRecipe.recipeList, ARC_VIEW, resourceHandler::getMainItemStack);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=ie", "machine=arcFurnace", "rewritten=" + n);
        }
        return n;
    }

    private static int rewriteBlast(final ResourceHandler resourceHandler) {
        final int n = OutputRewriter
            .rewriteList(BlastFurnaceRecipe.recipeList, BLAST_VIEW, resourceHandler::getMainItemStack);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=ie", "machine=blastFurnace", "rewritten=" + n);
        }
        return n;
    }

    private static int rewriteCrusher(final ResourceHandler resourceHandler) {
        final int n = OutputRewriter
            .rewriteList(CrusherRecipe.recipeList, CRUSHER_VIEW, resourceHandler::getMainItemStack);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=ie", "machine=crusher", "rewritten=" + n);
        }
        return n;
    }

    /**
     * Metal Press recipes live in an {@code ArrayListMultimap} keyed by mold, so we rewrite each
     * mold's backing {@link List} in place ({@code List.set}) — never mutating the multimap structure
     * ({@code ListMultimap.get(key)} returns a modifiable list view back into the multimap).
     */
    private static int rewriteMetalPress(final ResourceHandler resourceHandler) {
        int rewritten = 0;
        for (final ComparableItemStack mold : new ArrayList<>(MetalPressRecipe.recipeList.keySet())) {
            final List<MetalPressRecipe> moldRecipes = MetalPressRecipe.recipeList.get(mold);
            rewritten += OutputRewriter.rewriteList(moldRecipes, METAL_PRESS_VIEW, resourceHandler::getMainItemStack);
        }
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=ie", "machine=metalPress", "rewritten=" + rewritten);
        }
        return rewritten;
    }

    private static List<ItemStack> single(final ItemStack stack) {
        final List<ItemStack> items = new ArrayList<>(1);
        items.add(stack);
        return items;
    }

    /** Builds a secondary-chance array aligned with the secondary outputs, defaulting gaps to 100%. */
    private static float[] chanceCopy(final float[] chances, final int count) {
        if (chances != null && chances.length == count) return chances;
        final float[] out = new float[count];
        if (chances != null) {
            final int n = Math.min(chances.length, count);
            System.arraycopy(chances, 0, out, 0, n);
            for (int i = n; i < count; i++) out[i] = 1f;
        } else {
            for (int i = 0; i < count; i++) out[i] = 1f;
        }
        return out;
    }
}
