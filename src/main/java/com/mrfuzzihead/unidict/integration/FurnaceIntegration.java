package com.mrfuzzihead.unidict.integration;

/*
 * Rebuilt from wanion.unidict.integration.FurnaceIntegration (WanionCane, MPL-2.0) as the M4
 * "vanilla furnace rewrite" vertical slice (docs/PLAN.md §M4, scope rework 2026-08-12).
 * The flagship behavior — rewrite existing furnace recipe OUTPUTS to the canonical (main) entry of
 * their unified resource — is implemented NON-DESTRUCTIVELY: we only ever {@code setValue} on the
 * recipe map, never remove a recipe and never mutate a global registry (BB-3). The upstream
 * {@code inputReplacement} branch removed recipes and is NOT ported (craft-rewrite territory, deferred).
 */

import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

/**
 * Runs at {@link LoadStage#LOAD_COMPLETE} (not the POST_INIT default) so every mod has finished its
 * {@code init}/{@code postInit} — mods register vanilla furnace recipes there (Et Futurum's raw-ore
 * smelting, Galacticraft's electric/arc-furnace-relevant entries), and this global {@code FurnaceRecipes}
 * map is read by vanilla, IC2's electric furnace and Galacticraft's electric/arc furnace alike. Running
 * earlier (POST_INIT) missed recipes a later mod added after UniDict's own postInit, leaving copper
 * ore/raw → non-priority ingot (docs/INTEGRATIONS.md §Furnace; same ordering rationale as TE's LOAD_COMPLETE).
 */
@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
final class FurnaceIntegration extends AbstractModuleThread {

    FurnaceIntegration() {
        super("Furnace", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walk.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.furnace()) {
                final Map<ItemStack, ItemStack> smeltingList = FurnaceRecipes.smelting()
                    .getSmeltingList();
                final int rewritten = rewriteOutputs(smeltingList, resourceHandler::getMainItemStack);
                RewriteJournal.record("furnace", "furnace", rewritten);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of " + rewritten + " furnace recipes to their canonical entries.");
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "Some things that you smelted appear to be different now.";
    }

    /** Furnace seam over the shared {@link OutputRewriter} core (single-stack outputs). */
    static int rewriteOutputs(final Map<ItemStack, ItemStack> recipes, final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteSingleOutputs(recipes, resolveMain);
    }
}
