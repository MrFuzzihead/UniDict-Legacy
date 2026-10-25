package com.mrfuzzihead.unidict.integration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.railcraft.IBlastFurnaceCraftingManagerAccessor;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import mods.railcraft.api.crafting.IBlastFurnaceCraftingManager;
import mods.railcraft.api.crafting.IRockCrusherCraftingManager;
import mods.railcraft.api.crafting.IRockCrusherRecipe;
import mods.railcraft.common.util.crafting.BlastFurnaceCraftingManager;
import mods.railcraft.common.util.crafting.RockCrusherCraftingManager;

@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
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
                final int rewritten = rewriteBlastFurnace(resourceHandler) + rewriteRockCrusher(resourceHandler);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of "
                        + rewritten
                        + " Railcraft products (blast furnace + rock crusher) to their canonical entries.");
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(true, "integration=Railcraft", "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "The things that are made in the explosion chamber had to change.";
    }

    /** Blast Furnace: single final output, rebuilt and replaced at its index (non-destructive). */
    private int rewriteBlastFurnace(final ResourceHandler resourceHandler) {
        final IBlastFurnaceCraftingManager manager = BlastFurnaceCraftingManager.getInstance();
        @SuppressWarnings("unchecked")
        final List<BlastFurnaceCraftingManager.BlastFurnaceRecipe> recipes = (List<BlastFurnaceCraftingManager.BlastFurnaceRecipe>) ((IBlastFurnaceCraftingManagerAccessor) manager)
            .getRecipes();
        if (recipes == null) return 0;
        final int n = rewriteRecipes(recipes, BLAST_VIEW, resourceHandler::getMainItemStack);
        RewriteJournal.record("railcraft", "blastFurnace", n);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Railcraft", "machine=blastFurnace", "rewritten=" + n);
        }
        return n;
    }

    /**
     * Rock Crusher: multi-output + per-output chance, rewritten <em>in place</em> — {@code getRecipes()}
     * and {@code IRockCrusherRecipe#getOutputs()} are public, so no accessor mixin is needed (BB-3).
     */
    private int rewriteRockCrusher(final ResourceHandler resourceHandler) {
        final IRockCrusherCraftingManager manager = RockCrusherCraftingManager.getInstance();
        final int n = rewriteCrusherOutputs(
            manager.getRecipes(),
            IRockCrusherRecipe::getOutputs,
            resourceHandler::getMainItemStack);
        RewriteJournal.record("railcraft", "rockCrusher", n);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Railcraft", "machine=rockCrusher", "rewritten=" + n);
        }
        return n;
    }

    /** Railcraft seam over the shared {@link OutputRewriter} core (non-destructive in-place {@code List.set}). */
    static <R> int rewriteRecipes(final List<R> recipes, final OutputRewriter.OutputView<R> view,
        final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteList(recipes, view, resolveMain);
    }

    /**
     * Rock Crusher seam over the shared {@link OutputRewriter} chance-outputs core (T2-testable, no Railcraft types).
     */
    static <R> int rewriteCrusherOutputs(final List<? extends R> recipes,
        final Function<R, List<Map.Entry<ItemStack, Float>>> outputsOf, final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteChanceOutputs(recipes, outputsOf, resolveMain);
    }

    private static List<ItemStack> single(final ItemStack stack) {
        final List<ItemStack> items = new java.util.ArrayList<>(1);
        items.add(stack);
        return items;
    }
}
