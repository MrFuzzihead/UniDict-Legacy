package com.mrfuzzihead.unidict.integration;

/*
 * Rebuilt (and strictly scoped) from wanion.unidict.integration.ForestryIntegration (WanionCane, MPL-2.0)
 * as the M7 Forestry machine rewrite (docs/PLAN.md §M7 #2). Two non-destructive machine rewrites:
 * <ul>
 * <li><b>Carpenter</b> — each grid recipe's OUTPUT is canonicalised <em>in place</em>: Forestry's
 * {@code ShapedRecipeCustom} extends Forge's {@code ShapedOreRecipe}, so writing that private
 * {@code output} field (via the early {@code ShapedOreRecipeMixin} accessor) is all a rewrite is.
 * The manager's {@code Collections.unmodifiableSet} is only <em>iterated</em>, never mutated (BB-3).</li>
 * <li><b>Squeezer (container recipes)</b> — each {@code SqueezerRecipeManager.containerRecipes} value's
 * {@code remnants} byproduct is canonicalised by replacing the value for the SAME key
 * ({@code Map.Entry.setValue}) — the key (empty container) and entry count are preserved (BB-3). The
 * container-recipe map is Forestry's public static {@code ItemStackMap}, so no mixin is needed.</li>
 * </ul>
 * Deliberately NOT implemented here (see docs/INTEGRATIONS.md §Forestry):
 * <ul>
 * <li><b>Centrifuge</b> — {@code CentrifugeRecipe#getAllProducts()} returns an {@code ImmutableMap}
 * and the recipes themselves are immutable, so output rewriting would require remove+add against the
 * unmodifiable manager set — a destructive mutation the rework never does.</li>
 * <li><b>Fluid outputs</b> (squeezer/fermenter/still) — fluid equivalence has no OreDictionary-style
 * model in 1.7.10 (BB-4 territory, deferred).</li>
 * <li><b>Crate registration</b> (runtime {@code ItemCrated}) and NEI hiding — deferred (fragile).</li>
 * </ul>
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.forestry.IShapedOreRecipeAccessor;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import forestry.api.recipes.ICarpenterRecipe;
import forestry.api.recipes.IDescriptiveRecipe;
import forestry.api.recipes.RecipeManagers;
import forestry.factory.recipes.ISqueezerContainerRecipe;
import forestry.factory.recipes.SqueezerContainerRecipe;
import forestry.factory.recipes.SqueezerRecipeManager;

final class ForestryIntegration extends AbstractModuleThread {

    /**
     * Lazy ({@link Supplier}) so {@code ForestryIntegration.<clinit>} never resolves Forestry's
     * classes — the T2 test can reach the generic {@link #rewriteContainerRecipes} seam with a neutral
     * view and no Forestry types on the test classpath (mirrors {@code EIOIntegration}'s lazy views).
     */
    private static final Supplier<ContainerRecipeView<ISqueezerContainerRecipe>> CONTAINER_VIEW = () -> new ContainerRecipeView<ISqueezerContainerRecipe>() {

        @Override
        public ItemStack getRemnants(final ISqueezerContainerRecipe recipe) {
            return recipe.getRemnants();
        }

        @Override
        public int getProcessingTime(final ISqueezerContainerRecipe recipe) {
            return recipe.getProcessingTime();
        }

        @Override
        public float getRemnantsChance(final ISqueezerContainerRecipe recipe) {
            return recipe.getRemnantsChance();
        }

        @Override
        public ISqueezerContainerRecipe rebuild(final ISqueezerContainerRecipe original,
            final ItemStack canonicalRemnants) {
            return new SqueezerContainerRecipe(
                original.getEmptyContainer(),
                original.getProcessingTime(),
                canonicalRemnants,
                original.getRemnantsChance());
        }
    };

    /** Seam over one squeezer container recipe for the generic rewrite core. */
    interface ContainerRecipeView<V> {

        ItemStack getRemnants(V recipe);

        int getProcessingTime(V recipe);

        float getRemnantsChance(V recipe);

        /** @return a new recipe equal to {@code original} but with the canonical remnants stack */
        V rebuild(V original, ItemStack canonicalRemnants);
    }

    ForestryIntegration() {
        super("Forestry", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walks.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.forestry()) {
                final UnaryOperator<ItemStack> resolveMain = resourceHandler::getMainItemStack;
                final int rewritten = rewriteCarpenter(resolveMain) + rewriteSqueezer(resolveMain);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of "
                        + rewritten
                        + " Forestry machine recipes (carpenter grid outputs + squeezer container remnants)"
                        + " to their canonical entries.");
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(true, "integration=Forestry", "machines=2", "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "All these bees... they can hurt, you know?";
    }

    /**
     * Carpenter: canonicalises each grid recipe's output <em>in place</em> (BB-3). The manager's
     * {@code recipes()} collection is live but unmodifiable — it is only ever iterated; the
     * rewrite writes the private {@code ShapedOreRecipe#output} field through the mixin accessor, which
     * is exactly what the inherited {@code IRecipe#getRecipeOutput()} reads.
     */
    private int rewriteCarpenter(final UnaryOperator<ItemStack> resolveMain) {
        final Collection<ICarpenterRecipe> recipes;
        if (RecipeManagers.carpenterManager == null) return 0;
        recipes = RecipeManagers.carpenterManager.recipes();
        if (recipes == null) return 0;
        final List<IShapedOreRecipeAccessor> gridRecipes = new ArrayList<>();
        for (final ICarpenterRecipe carpenterRecipe : recipes) {
            if (carpenterRecipe == null) continue; // never expected; stay defensive
            final IDescriptiveRecipe grid = carpenterRecipe.getCraftingGridRecipe();
            if (grid == null) continue; // a real ICarpenterRecipe always carries one
            // Only Forestry's ShapedRecipeCustom (= a ShapedOreRecipe) has the mutable output field we
            // rewrite; a foreign ICarpenterRecipe impl is safely skipped rather than crashing the walk.
            if (!(grid instanceof ShapedOreRecipe)) continue;
            gridRecipes.add((IShapedOreRecipeAccessor) (Object) grid);
        }
        final int n = rewriteCarpenterOutputs(gridRecipes, resolveMain);
        RewriteJournal.record("forestry", "carpenter", n);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Forestry", "machine=carpenter", "rewritten=" + n);
        }
        return n;
    }

    /**
     * Squeezer: canonicalises each container recipe's {@code remnants} byproduct in place (BB-3). The
     * {@code containerRecipes} map is Forestry's public static {@code ItemStackMap}; each affected value
     * is replaced via {@code Map.Entry.setValue} under the SAME key — the entry count and the key
     * (empty container) are preserved, so no recipe is removed and none is added.
     */
    private int rewriteSqueezer(final UnaryOperator<ItemStack> resolveMain) {
        final Map<ItemStack, ISqueezerContainerRecipe> containerRecipes = SqueezerRecipeManager.containerRecipes;
        if (containerRecipes == null) return 0; // defensive: the field is a public static, never null
        final int n = rewriteContainerRecipes(containerRecipes, CONTAINER_VIEW.get(), resolveMain);
        RewriteJournal.record("forestry", "squeezer", n);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=Forestry", "machine=squeezer", "rewritten=" + n);
        }
        return n;
    }

    /**
     * Carpenter output-rewrite seam (T2-testable, no Forestry types on the test classpath): rewrites
     * each grid recipe's output against the canonical resolver, in place, via the accessor seam.
     * Never removes a recipe and never touches the recipe's inputs (BB-3).
     *
     * @return number of grid recipes actually rewritten
     */
    static int rewriteCarpenterOutputs(final Iterable<? extends IShapedOreRecipeAccessor> recipes,
        final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final IShapedOreRecipeAccessor recipe : recipes) {
            if (recipe == null) continue;
            final ItemStack output = recipe.unidict$getOutput();
            if (output == null) continue;
            final ItemStack canonical = resolveMain.apply(output);
            if (canonical != output) {
                recipe.unidict$setOutput(canonical);
                rewritten++;
            }
        }
        return rewritten;
    }

    /**
     * Squeezer container-recipe seam (T2-testable, no Forestry types on the test classpath): rewrites
     * each container recipe's {@code remnants} in place via {@code Map.Entry.setValue} (key + entry
     * count preserved — never a removal, never a global-registry mutation, BB-3). Recipes without a
     * byproduct ({@code null} remnants) are left alone.
     *
     * @return number of container recipes actually rewritten
     */
    static <V> int rewriteContainerRecipes(final Map<ItemStack, V> containerRecipes, final ContainerRecipeView<V> view,
        final UnaryOperator<ItemStack> resolveMain) {
        int rewritten = 0;
        for (final Map.Entry<ItemStack, V> entry : containerRecipes.entrySet()) {
            final V recipe = entry.getValue();
            if (recipe == null) continue;
            final ItemStack remnants = view.getRemnants(recipe);
            if (remnants == null) continue; // no byproduct -> nothing to unify
            final ItemStack canonical = resolveMain.apply(remnants);
            if (canonical != remnants) {
                entry.setValue(view.rebuild(recipe, canonical));
                rewritten++;
            }
        }
        return rewritten;
    }
}
