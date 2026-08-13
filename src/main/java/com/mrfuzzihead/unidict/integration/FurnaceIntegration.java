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
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

final class FurnaceIntegration extends AbstractModuleThread {

    FurnaceIntegration() {
        super("Furnace", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            if (resourceHandler != null && Config.furnace()) {
                final Map<ItemStack, ItemStack> smeltingList = FurnaceRecipes.smelting()
                    .getSmeltingList();
                final int rewritten = rewriteOutputs(smeltingList, resourceHandler::getMainItemStack);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of " + rewritten + " furnace recipes to their canonical entries.");
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "Some things that you smelted appear to be different now.";
    }

    /**
     * Non-destructive output rewrite (T2 seam): maps every recipe's output through {@code resolveMain}
     * (in production the resource handler's canonical lookup) without adding/removing any recipe.
     *
     * @return number of outputs that were actually changed
     */
    static int rewriteOutputs(final Map<ItemStack, ItemStack> recipes, final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final Map.Entry<ItemStack, ItemStack> furnaceRecipe : recipes.entrySet()) {
            final ItemStack output = furnaceRecipe.getValue();
            if (output == null) continue;
            final ItemStack main = resolveMain.apply(output);
            if (main != null && main != output) {
                furnaceRecipe.setValue(main);
                rewritten++;
            }
        }
        return rewritten;
    }
}
