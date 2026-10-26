package com.mrfuzzihead.unidict.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.common.Accessors;
import com.mrfuzzihead.unidict.enderio.IOreDictionaryPreferencesAccessor;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;

import crazypants.enderio.machine.alloy.AlloyRecipeManager;
import crazypants.enderio.machine.crusher.CrusherRecipeManager;
import crazypants.enderio.machine.recipe.BasicManyToOneRecipe;
import crazypants.enderio.machine.recipe.IManyToOneRecipe;
import crazypants.enderio.machine.recipe.Recipe;
import crazypants.enderio.machine.recipe.RecipeOutput;
import crazypants.enderio.material.OreDictionaryPreferences;

@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
final class EIOIntegration extends AbstractModuleThread {

    /**
     * Alloy Smelter: every item output is rebuilt (chance/experience preserved) into a new
     * {@code BasicManyToOneRecipe} at its index; fluid outputs are kept as-is. Previously only the first
     * output survived the rebuild (docs/CODE_REVIEW.md BUG-16). Lazy ({@link Supplier}) so
     * {@code EIOIntegration.<clinit>} never resolves Ender IO's classes — those types only exist on a live
     * classpath, so the T2 test can reach the neutral weighted core without them.
     */
    private static final Supplier<OutputRewriter.WeightedView<IManyToOneRecipe>> ALLOY_VIEW = () -> new OutputRewriter.WeightedView<IManyToOneRecipe>() {

        @Override
        public List<OutputRewriter.WeightedOutput> getOutputs(final IManyToOneRecipe recipe) {
            return itemOutputs(recipe.getOutputs());
        }

        @Override
        public IManyToOneRecipe rebuild(final IManyToOneRecipe original,
            final List<OutputRewriter.WeightedOutput> mapped) {
            final Recipe rebuilt = new Recipe(
                original.getInputs(),
                mergeOutputs(original.getOutputs(), mapped),
                original.getEnergyRequired(),
                original.getBonusType());
            return new BasicManyToOneRecipe(rebuilt);
        }
    };

    /**
     * SAG Mill: same weighted, all-outputs rebuild as the alloy smelter (chance/experience preserved, fluid
     * outputs kept). Lazy for the same reason as {@link #ALLOY_VIEW}.
     */
    private static final Supplier<OutputRewriter.WeightedView<Recipe>> SAG_VIEW = () -> new OutputRewriter.WeightedView<Recipe>() {

        @Override
        public List<OutputRewriter.WeightedOutput> getOutputs(final Recipe recipe) {
            return itemOutputs(recipe.getOutputs());
        }

        @Override
        public Recipe rebuild(final Recipe original, final List<OutputRewriter.WeightedOutput> mapped) {
            return new Recipe(
                original.getInputs(),
                mergeOutputs(original.getOutputs(), mapped),
                original.getEnergyRequired(),
                original.getBonusType());
        }
    };

    EIOIntegration() {
        super("Ender IO", "Integration");
    }

    /** Dev seam self-check (docs/CODE_REVIEW.md BUG-9), asserted by the verifyServer harness. */
    static void checkSeams() {
        VerifyHarness.checkSeam(
            "enderio/oreDictionaryPreferences",
            IOreDictionaryPreferencesAccessor.class,
            OreDictionaryPreferences.class);
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walks.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.enderIO()) {
                final IOreDictionaryPreferencesAccessor preferences = Accessors
                    .as(OreDictionaryPreferences.instance, IOreDictionaryPreferencesAccessor.class);
                if (preferences == null) {
                    UniDict.LOG
                        .warn(threadName + "OreDictionaryPreferences accessor not applied; preferences not cleared.");
                } else {
                    fixOreDictPreferences(preferences);
                }
                final UnaryOperator<ItemStack> resolveMain = resourceHandler::getMainItemStack;
                final int rewritten = rewriteAlloySmelter(resolveMain) + rewriteSagMill(resolveMain);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of "
                        + rewritten
                        + " Ender IO machine recipes to their canonical entries.");
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(true, "integration=EIO", "machines=2", "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "Some inanimate objects appear to have used ender pearls.";
    }

    /**
     * Clears Ender IO's ore-dictionary preferences so it yields the canonical entry (accessor seam).
     * Package-private (not private) so the T2 test can drive it through a {@link FakeOreDictionaryPreferencesAccessor}.
     */
    static void fixOreDictPreferences(final IOreDictionaryPreferencesAccessor preferencesAccessor) {
        final Map<String, ItemStack> preferences = preferencesAccessor.getPreferences();
        if (preferences != null) preferences.clear();
    }

    private static int rewriteAlloySmelter(final UnaryOperator<ItemStack> resolveMain) {
        final List<IManyToOneRecipe> recipes = AlloyRecipeManager.getInstance()
            .getRecipes();
        final int n = rewriteRecipes(recipes, ALLOY_VIEW.get(), resolveMain);
        RewriteJournal.record("eio", "alloySmelter", n);
        if (VerifyHarness.isEnabled())
            VerifyHarness.record(true, "integration=EIO", "machine=alloySmelter", "rewritten=" + n);
        return n;
    }

    private static int rewriteSagMill(final UnaryOperator<ItemStack> resolveMain) {
        final List<Recipe> recipes = CrusherRecipeManager.getInstance()
            .getRecipes();
        final int n = rewriteRecipes(recipes, SAG_VIEW.get(), resolveMain);
        RewriteJournal.record("eio", "sagMill", n);
        if (VerifyHarness.isEnabled())
            VerifyHarness.record(true, "integration=EIO", "machine=sagMill", "rewritten=" + n);
        return n;
    }

    /**
     * EIO seam over the shared weighted core (non-destructive in-place {@code List.set}): every output is
     * remapped 1:1, chances/experience are preserved, and no output is ever dropped.
     */
    static <R> int rewriteRecipes(final List<R> recipes, final OutputRewriter.WeightedView<R> view,
        final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteWeightedList(recipes, view, resolveMain);
    }

    /**
     * The item outputs of an Ender IO recipe as neutral weighted entries. Fluid outputs are not unified and
     * are preserved untouched by {@link #mergeOutputs(RecipeOutput[], List)}.
     */
    private static List<OutputRewriter.WeightedOutput> itemOutputs(final RecipeOutput[] outputs) {
        final List<OutputRewriter.WeightedOutput> items = new ArrayList<>(outputs.length);
        for (final RecipeOutput output : outputs) {
            if (output == null || output.isFluid()) continue;
            items
                .add(new OutputRewriter.WeightedOutput(output.getOutput(), output.getChance(), output.getExperiance()));
        }
        return items;
    }

    /**
     * Rebuilds the full output array 1:1: each item slot takes the mapped entry at its position (so count,
     * order, chance and experience are preserved), and fluid/opaque slots keep their original object.
     */
    private static RecipeOutput[] mergeOutputs(final RecipeOutput[] original,
        final List<OutputRewriter.WeightedOutput> mapped) {
        final RecipeOutput[] rebuilt = new RecipeOutput[original.length];
        int itemIndex = 0;
        for (int i = 0; i < original.length; i++) {
            final RecipeOutput output = original[i];
            if (output == null || output.isFluid()) {
                rebuilt[i] = output;
                continue;
            }
            final OutputRewriter.WeightedOutput replacement = mapped.get(itemIndex++);
            rebuilt[i] = new RecipeOutput(replacement.stack, replacement.chance, replacement.experience);
        }
        return rebuilt;
    }
}
