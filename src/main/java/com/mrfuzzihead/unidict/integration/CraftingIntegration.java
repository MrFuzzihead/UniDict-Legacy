package com.mrfuzzihead.unidict.integration;

import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.crafting.IShapedRecipesAccessor;
import com.mrfuzzihead.unidict.crafting.IShapelessRecipesAccessor;
import com.mrfuzzihead.unidict.forestry.IShapedOreRecipeAccessor;
import com.mrfuzzihead.unidict.galacticraft.IShapelessOreRecipeAccessor;
import com.mrfuzzihead.unidict.ic2.IAdvRecipeAccessor;
import com.mrfuzzihead.unidict.ic2.IAdvShapelessRecipeAccessor;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

/**
 * Crafting-table output rewrite (M5), non-destructive output-only (BB-3).
 *
 * <p>
 * Runs at {@link LoadStage#LOAD_COMPLETE} <em>and</em> is re-run at server start. In GTNH-style packs
 * the nugget→ingot / ingot→block / alloy compaction recipes (and scripted recipe changes) are often
 * registered only late — during another mod's postInit or even at {@code FMLServerStartingEvent} (the
 * same reason Galacticraft's compressor is deferred to server start). So the rewrite is idempotent and
 * re-runs against the final {@link CraftingManager} list at server start, after every recipe source
 * has run; already-canonical outputs are skipped so re-running is safe.
 */
@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
final class CraftingIntegration extends AbstractModuleThread {

    private static final Logger LOG = LogManager.getLogger("UniDict");
    private static final String PREFIX = "Crafting Integration: ";

    CraftingIntegration() {
        super("Crafting", "Integration");
    }

    @Override
    public String call() {
        try {
            runCraftingRewrite();
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "Now everything you craft is consistent.";
    }

    /**
     * Runs the crafting-table output rewrite against the current {@link CraftingManager} list.
     * <b>Idempotent</b> (already-canonical outputs are skipped), so it is safe to invoke at LOAD_COMPLETE
     * and again at server start (the authoritative point, after script/mod recipe registration). No-op
     * unless the crafting toggle + integration master are on and the resource model is ready.
     */
    static void runCraftingRewrite() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        if (resourceHandler == null || resourceHandler.resources.isEmpty()
            || !Config.crafting()
            || !Config.integrationModule()) return;
        @SuppressWarnings("unchecked")
        final List<IRecipe> recipes = CraftingManager.getInstance()
            .getRecipeList();
        final int rewritten = rewriteCraftingOutputs(recipes, resourceHandler::getMainItemStack);
        RewriteJournal.record("crafting", "table", rewritten);
        LOG.info(PREFIX + "rewrote outputs of " + rewritten + " crafting recipes to their canonical entries.");
        if (VerifyHarness.isEnabled()) VerifyHarness.record(true, "integration=crafting", "rewritten=" + rewritten);
    }

    /**
     * Non-destructive output-only rewrite (BB-3): for every crafting recipe whose output resolves to
     * a unified resource, replace the output ItemStack in place through the accessor seam.
     * Never removes or rebuilds a recipe -- only the output reference changes.
     * Handles vanilla ShapedRecipes/ShapelessRecipes AND Forge's ShapedOreRecipe/ShapelessOreRecipe
     * (implementing IRecipe directly in 1.7.10, incl. subclasses like Forestry's ShapedRecipeCustom)
     * via their accessor interfaces.
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
            // Try vanilla shapeless accessor (catches ShapelessRecipes + subclasses like ShapelessOreRecipe)
            if (recipe instanceof ShapelessRecipes) {
                ((IShapelessRecipesAccessor) recipe).setRecipeOutput(canonical);
                rewritten++;
                continue;
            }
            // Forge ShapedOreRecipe (and subclasses like Forestry's ShapedRecipeCustom) — these implement
            // IRecipe directly in 1.7.10 (they do NOT extend ShapedRecipes), so they need their own
            // accessor. This is what was being missed for GTNH metal compaction/alloy recipes.
            if (recipe instanceof IShapedOreRecipeAccessor) {
                ((IShapedOreRecipeAccessor) recipe).unidict$setOutput(canonical);
                rewritten++;
                continue;
            }
            // Forge ShapelessOreRecipe — a distinct IRecipe from the shaped variant.
            if (recipe instanceof IShapelessOreRecipeAccessor) {
                ((IShapelessOreRecipeAccessor) recipe).unidict$setOutput(canonical);
                rewritten++;
                continue;
            }
            // IC2 AdvShapelessRecipe — output field is PUBLIC (non-final) so it can be canonicalised in
            // place via its accessor (e.g. tiny-copper-dust → copper-dust). This is the reliable path for
            // IC2's shapeless recipes.
            if (recipe instanceof IAdvShapelessRecipeAccessor) {
                ((IAdvShapelessRecipeAccessor) recipe).unidict$setOutput(canonical);
                rewritten++;
                continue;
            }
            // IC2 AdvRecipe (shaped) — output field is public but FINAL; the mutator writes it from inside
            // the declaring class (JVM permits instance-final writes there), which is the only mechanism
            // reliably observed by NEI + findMatchingRecipe in this runtime (read-side @Inject return
            // replacement has proven ineffective). Covers 9× tiny-dust → dust, 9 ingots → block, etc.
            if (recipe instanceof IAdvRecipeAccessor) {
                ((IAdvRecipeAccessor) recipe).unidict$setOutput(canonical);
                rewritten++;
                continue;
            }
            // Custom IRecipe subclasses (GT/IC2/etc.) aren't covered by the accessor seam. Log it only
            // when the canonical genuinely differs (fresh-identical stacks from getMainItemStack would
            // otherwise spam misleading warnings).
            if (canonical.getItem() != output.getItem() || canonical.getItemDamage() != output.getItemDamage()) {
                LOG.warn(
                    PREFIX + "recipe type {} (output={}) resolves to canonical {} but is not a Shaped/Shapeless "
                        + "recipe — not rewritten by UniDict",
                    recipe.getClass()
                        .getName(),
                    output,
                    canonical);
            }
        }
        return rewritten;
    }
}
