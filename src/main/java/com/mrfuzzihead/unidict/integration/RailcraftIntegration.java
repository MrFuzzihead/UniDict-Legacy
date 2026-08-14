package com.mrfuzzihead.unidict.integration;

/*
 * Rebuilt (and tightened) from wanion.unidict.integration.RailcraftIntegration (WanionCane, MPL-2.0) as
 * the M7 Railcraft machine rewrite (docs/PLAN.md §M7 #3). Rewrites every Railcraft blast-furnace recipe
 * OUTPUT to the canonical (main) entry of its unified resource.
 * <p>Railcraft's {@code BlastFurnaceRecipe} is an immutable value object ({@code final} output field), so
 * a rewritten recipe is <b>rebuilt</b> and replaced <em>at its index</em> in the manager's recipe list via
 * {@link OutputRewriter#rewriteList} ({@code List.set}) — the entry count and order are preserved and no
 * recipe is ever removed, and no global registry is mutated (BB-3). The manager's {@code private final}
 * {@code recipes} list is read through the M7 accessor seam
 * ({@link com.mrfuzzihead.unidict.railcraft.IBlastFurnaceCraftingManagerAccessor} realised by
 * {@code BlastFurnaceCraftingManagerMixin}), replacing upstream's {@code Util.getField} reflection.
 * Upstream's {@code FixedSizeList} staging + {@code Iterator.remove()} + {@code addAll} is dropped for the
 * non-destructive in-place rewrite (BB-3).
 */

import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.railcraft.IBlastFurnaceCraftingManagerAccessor;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import mods.railcraft.api.crafting.IBlastFurnaceCraftingManager;
import mods.railcraft.common.util.crafting.BlastFurnaceCraftingManager;

final class RailcraftIntegration extends AbstractModuleThread {

    /** Blast Furnace: single final output, rebuilt and replaced at its index (non-destructive). */
    private static final OutputRewriter.OutputView<BlastFurnaceCraftingManager.BlastFurnaceRecipe> BLAST_VIEW = new OutputRewriter.OutputView<BlastFurnaceCraftingManager.BlastFurnaceRecipe>() {

        @Override
        public List<ItemStack> getItems(final BlastFurnaceCraftingManager.BlastFurnaceRecipe recipe) {
            return single(recipe.getOutput());
        }

        @Override
        public BlastFurnaceCraftingManager.BlastFurnaceRecipe rebuild(
            final BlastFurnaceCraftingManager.BlastFurnaceRecipe original, final List<ItemStack> mapped) {
            return new BlastFurnaceCraftingManager.BlastFurnaceRecipe(
                original.getInput(),
                original.matchDamage(),
                original.matchNBT(),
                original.getCookTime(),
                mapped.get(0));
        }
    };

    RailcraftIntegration() {
        super("Railcraft", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walk.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.railcraft()) {
                final IBlastFurnaceCraftingManager manager = BlastFurnaceCraftingManager.getInstance();
                @SuppressWarnings("unchecked")
                final List<BlastFurnaceCraftingManager.BlastFurnaceRecipe> recipes = (List<BlastFurnaceCraftingManager.BlastFurnaceRecipe>) ((IBlastFurnaceCraftingManagerAccessor) manager)
                    .getRecipes();
                if (recipes != null) {
                    final int rewritten = rewriteRecipes(recipes, BLAST_VIEW, resourceHandler::getMainItemStack);
                    UniDict.LOG.info(
                        threadName + "rewrote outputs of "
                            + rewritten
                            + " Railcraft blast-furnace recipes to their canonical entries.");
                    if (VerifyHarness.isEnabled()) {
                        VerifyHarness.record(true, "integration=Railcraft", "rewritten=" + rewritten);
                    }
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "The things that are made in the explosion chamber had to change.";
    }

    /** Railcraft seam over the shared {@link OutputRewriter} core (non-destructive in-place {@code List.set}). */
    static <R> int rewriteRecipes(final List<R> recipes, final OutputRewriter.OutputView<R> view,
        final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteList(recipes, view, resolveMain);
    }

    private static List<ItemStack> single(final ItemStack stack) {
        final List<ItemStack> items = new java.util.ArrayList<>(1);
        items.add(stack);
        return items;
    }
}
