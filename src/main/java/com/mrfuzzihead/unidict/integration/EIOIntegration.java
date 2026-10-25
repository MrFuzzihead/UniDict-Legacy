package com.mrfuzzihead.unidict.integration;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
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
     * Alloy Smelter: single output, rebuilt into a {@code BasicManyToOneRecipe} at its index.
     * Lazy ({@link Supplier}) so {@code EIOIntegration.<clinit>} never resolves Ender IO's classes —
     * those types only exist on a live classpath, so a T2 test can reach the pure seams without them.
     */
    private static final Supplier<OutputRewriter.OutputView<IManyToOneRecipe>> ALLOY_VIEW = () -> new OutputRewriter.OutputView<IManyToOneRecipe>() {

        @Override
        public List<ItemStack> getItems(final IManyToOneRecipe recipe) {
            return single(recipe.getOutput());
        }

        @Override
        public IManyToOneRecipe rebuild(final IManyToOneRecipe original, final List<ItemStack> mapped) {
            final RecipeOutput oldOutput = original.getOutputs()[0];
            final RecipeOutput newOutput = new RecipeOutput(
                mapped.get(0),
                oldOutput.getChance(),
                oldOutput.getExperiance());
            final Recipe rebuilt = new Recipe(
                newOutput,
                original.getEnergyRequired(),
                original.getBonusType(),
                original.getInputs());
            return new BasicManyToOneRecipe(rebuilt);
        }
    };

    /**
     * SAG Mill: possibly multiple outputs, each rebuilt into a new {@code Recipe} at its index. Lazy for
     * the same reason as {@link #ALLOY_VIEW}.
     */
    private static final Supplier<OutputRewriter.OutputView<Recipe>> SAG_VIEW = () -> new OutputRewriter.OutputView<Recipe>() {

        @Override
        public List<ItemStack> getItems(final Recipe recipe) {
            return outputs(recipe);
        }

        @Override
        public Recipe rebuild(final Recipe original, final List<ItemStack> mapped) {
            final RecipeOutput[] oldOutputs = original.getOutputs();
            final RecipeOutput[] newOutputs = new RecipeOutput[oldOutputs.length];
            for (int i = 0; i < oldOutputs.length; i++) newOutputs[i] = new RecipeOutput(
                mapped.get(i),
                oldOutputs[i].getChance(),
                oldOutputs[i].getExperiance());
            return new Recipe(original.getInputs(), newOutputs, original.getEnergyRequired(), original.getBonusType());
        }
    };

    EIOIntegration() {
        super("Ender IO", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walks.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.enderIO()) {
                fixOreDictPreferences((IOreDictionaryPreferencesAccessor) (Object) OreDictionaryPreferences.instance);
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

    /** EIO seam over the shared {@link OutputRewriter} core (non-destructive in-place {@code List.set}). */
    static <R> int rewriteRecipes(final List<R> recipes, final OutputRewriter.OutputView<R> view,
        final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteList(recipes, view, resolveMain);
    }

    private static List<ItemStack> single(final ItemStack stack) {
        final List<ItemStack> items = new java.util.ArrayList<>(1);
        items.add(stack);
        return items;
    }

    private static List<ItemStack> outputs(final Recipe recipe) {
        final RecipeOutput[] outputs = recipe.getOutputs();
        final List<ItemStack> items = new java.util.ArrayList<>(outputs.length);
        for (final RecipeOutput output : outputs) items.add(output.getOutput());
        return items;
    }
}
