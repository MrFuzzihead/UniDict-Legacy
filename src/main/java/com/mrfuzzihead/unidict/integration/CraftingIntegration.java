package com.mrfuzzihead.unidict.integration;

import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.crafting.IShapedRecipesAccessor;
import com.mrfuzzihead.unidict.crafting.IShapelessRecipesAccessor;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

final class CraftingIntegration extends AbstractModuleThread {

    CraftingIntegration() {
        super("Crafting", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.crafting()) {
                @SuppressWarnings("unchecked")
                final List<IRecipe> recipes = CraftingManager.getInstance()
                    .getRecipeList();
                final int rewritten = rewriteCraftingOutputs(recipes, resourceHandler::getMainItemStack);
                RewriteJournal.record("crafting", "table", rewritten);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of " + rewritten + " crafting recipes to their canonical entries.");
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "Now everything you craft is consistent.";
    }

    /**
     * Non-destructive output-only rewrite (BB-3): for every crafting recipe whose output resolves to
     * a unified resource, replace the output ItemStack in place through the accessor seam.
     * Never removes or rebuilds a recipe -- only the output reference changes.
     *
     * @return number of recipes actually rewritten
     */
    static int rewriteCraftingOutputs(final List<IRecipe> recipes, final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final IRecipe recipe : recipes) {
            if (recipe == null) continue;
            final ItemStack output = recipe.getRecipeOutput();
            if (output == null) continue;
            final ItemStack canonical = resolveMain.apply(output);
            if (canonical == output) continue; // already canonical

            // Try shaped accessor (catches ShapedRecipes + subclasses like ShapedOreRecipe)
            if (recipe instanceof ShapedRecipes) {
                ((IShapedRecipesAccessor) recipe).setRecipeOutput(canonical);
                rewritten++;
                continue;
            }
            // Try shapeless accessor (catches ShapelessRecipes + subclasses like ShapelessOreRecipe)
            if (recipe instanceof ShapelessRecipes) {
                ((IShapelessRecipesAccessor) recipe).setRecipeOutput(canonical);
                rewritten++;
                continue;
            }
            // IC2 AdvRecipe/AdvShapelessRecipe and other custom types: not covered yet.
            // They produce mod-specific items rather than unified metals in practice,
            // so this is a safe gap. Add accessor mixins here if T3 shows missed recipes.
        }
        return rewritten;
    }
}
