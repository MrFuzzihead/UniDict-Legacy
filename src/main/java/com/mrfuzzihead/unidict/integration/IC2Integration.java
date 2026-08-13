package com.mrfuzzihead.unidict.integration;

/*
 * Rebuilt (and extended) from wanion.unidict.integration.IC2Integration (WanionCane, MPL-2.0) as the
 * M6 IC2 machine rewrite (docs/PLAN.md §M6 #3).
 * <p>The flagship unification behavior — rewrite each machine's recipe OUTPUTS to the canonical
 * (main) entry of their unified resource — is implemented NON-DESTRUCTIVELY (BB-3): we only ever
 * rebuild a RecipeOutput's item list and setValue it on the map, never remove a recipe and never
 * mutate a global registry (via the shared {@link OutputRewriter} core). IC2's {@code inputReplacement}
 * branch from the furnace port is the same craft-rewrite territory as upstream's and is not touched.
 * <p>Coverage &amp; improvements over upstream:
 * <ul>
 * <li><b>10 {@code Recipes.*} machine-manager maps</b> — upstream covered five
 * (centrifuge, metalformerRolling, blastfurance, compressor, macerator); we add extractor,
 * metalformerExtruding, metalformerCutting, blockcutter and oreWashing. ({@code recycler} is
 * excluded: its {@code RecyclerRecipeManager} is a randomizer whose {@code getRecipes()} returns
 * null — there are no outputs to unify.)</li>
 * <li>A generic machine-key seam makes the core {@link OutputRewriter} logic purely T2-testable
 * without fabricating {@code IRecipeInput} stubs (no Mockito in this project's test sources).</li>
 * <li>Per-machine dev-verify lines ({@code integration=ic2 machine=&lt;name&gt;}) plus a summary,
 * so each machine is independently diffable across runs (M6 gate + BB-1 transparency).</li>
 * <li>An early-skip guard: when the resource model is empty there is nothing to unify, so we skip
 * the machine-map walks entirely; a defensive null-guard means no single machine map can ever
 * abort the module.</li>
 * </ul>
 */

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import ic2.api.recipe.IRecipeInput;
import ic2.api.recipe.RecipeOutput;
import ic2.api.recipe.Recipes;

final class IC2Integration extends AbstractModuleThread {

    /** Adapts IC2's {@link RecipeOutput}s to the shared {@link OutputRewriter} core. */
    private static final OutputRewriter.OutputView<RecipeOutput> OUTPUT_VIEW = new OutputRewriter.OutputView<RecipeOutput>() {

        @Override
        public List<ItemStack> getItems(final RecipeOutput output) {
            return output.items;
        }

        @Override
        public RecipeOutput rebuild(final RecipeOutput original, final List<ItemStack> mapped) {
            return new RecipeOutput(original.metadata, mapped);
        }
    };

    /** A machine display-name bound to its live {@code Recipes.*} recipe map. */
    private static final class Machine {

        final String name;
        final Map<IRecipeInput, RecipeOutput> recipes;

        Machine(final String name, final Map<IRecipeInput, RecipeOutput> recipes) {
            this.name = name;
            this.recipes = recipes;
        }
    }

    IC2Integration() {
        super("IC2", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip (improvement): with no unified resource the canonical lookup is a no-op, so
            // avoid walking the machine maps. resourceHandler.resources is a live view of the model.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.ic2()) {
                final List<Machine> machines = machineList();
                int rewritten = 0;
                for (final Machine machine : machines) {
                    // Defensive null-guard: IC2's recycler manager returns null from getRecipes()
                    // (it is a randomizer with no output recipes), and no single machine map should
                    // ever be allowed to abort the whole module. Skip + warn; never crash.
                    if (machine.recipes == null) {
                        UniDict.LOG.warn(
                            threadName + "machine '"
                                + machine.name
                                + "' has no recipe map (getRecipes() == null); skipping.");
                        if (VerifyHarness.isEnabled()) {
                            VerifyHarness
                                .record(true, "integration=ic2", "machine=" + machine.name, "skipped=null-map");
                        }
                        continue;
                    }
                    final int n = rewriteOutputs(machine.recipes, resourceHandler::getMainItemStack);
                    rewritten += n;
                    // Per-machine line (BB-1) — fixed iteration order keeps the dump diffable.
                    if (VerifyHarness.isEnabled()) {
                        VerifyHarness.record(true, "integration=ic2", "machine=" + machine.name, "rewritten=" + n);
                    }
                }
                UniDict.LOG.info(
                    threadName + "rewrote outputs of "
                        + rewritten
                        + " IC2 machine recipes to their canonical entries.");
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness
                        .record(true, "integration=ic2", "machines=" + machines.size(), "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "The world appears to be entirely industrialized.";
    }

    /**
     * The explicit, deterministic (ordered) IC2 machine registry. Covers every {@code Recipes.*}
     * machine manager that actually holds output recipes. {@code recycler} is deliberately excluded:
     * its {@code RecyclerRecipeManager} is a randomizer whose {@code getRecipes()} returns null
     * (verified against IC2 2.2.828) — there are no outputs to unify.
     */
    private static List<Machine> machineList() {
        return Arrays.asList(
            new Machine("macerator", Recipes.macerator.getRecipes()),
            new Machine("compressor", Recipes.compressor.getRecipes()),
            new Machine("extractor", Recipes.extractor.getRecipes()),
            new Machine("centrifuge", Recipes.centrifuge.getRecipes()),
            new Machine("metalformerExtruding", Recipes.metalformerExtruding.getRecipes()),
            new Machine("metalformerCutting", Recipes.metalformerCutting.getRecipes()),
            new Machine("metalformerRolling", Recipes.metalformerRolling.getRecipes()),
            new Machine("blockcutter", Recipes.blockcutter.getRecipes()),
            new Machine("blastfurance", Recipes.blastfurance.getRecipes()),
            new Machine("oreWashing", Recipes.oreWashing.getRecipes()));
    }

    /**
     * Package-private seam over the shared {@link OutputRewriter} core, bound to IC2's
     * {@link RecipeOutput} view. {@code K} is the machine map's key type ({@link IRecipeInput} in
     * production) so tests can drive it with any key factory — no IC2 stub needed.
     *
     * @return number of outputs actually changed
     */
    static <K> int rewriteOutputs(final Map<K, RecipeOutput> recipes, final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteOutputs(recipes, OUTPUT_VIEW, resolveMain);
    }
}
