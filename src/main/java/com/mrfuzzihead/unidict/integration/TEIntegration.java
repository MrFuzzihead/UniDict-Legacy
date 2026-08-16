package com.mrfuzzihead.unidict.integration;

/*
 * Rebuilt (and tightened) from wanion.unidict.integration.TEIntegration (WanionCane, MPL-2.0) as the M7
 * Thermal Expansion machine rewrite (docs/PLAN.md §M7 #4). Rewrites every TE machine recipe OUTPUT to
 * the canonical (main) entry of its unified resource for the Redstone Furnace, Pulverizer and Induction
 * Smelter.
 * <p>The three TE recipe types ({@code RecipeFurnace}, {@code RecipePulverizer}, {@code RecipeSmelter})
 * are immutable value objects ({@code final} output fields) whose constructors are package-private, so a
 * rewritten recipe must be <b>rebuilt</b> via the M0-Spike-B {@code @Invoker} mixins
 * ({@link com.mrfuzzihead.unidict.mixins.late.RecipeFurnaceInvoker} &amp; co.) and replaced by {@code Map.setValue}
 * — never a removal, never a global-registry mutation (BB-3). The per-manager {@code private static}
 * {@code recipeMap} fields are read through the M7 accessor seam
 * ({@code IFurnaceManagerAccessor} &amp; co. realised by {@code FurnaceManagerMixin} &amp; co.), replacing
 * upstream's {@code Util.getField} reflection. Runs at {@link com.mrfuzzihead.unidict.LoadStage#LOAD_COMPLETE}
 * (as upstream) so TE's managers are fully populated.
 * <p>Note on {@code isOutputFood} (furnace): the {@code @Invoker} ctor does not (and cannot) set the
 * package-private {@code isOutputFood} field, exactly as upstream's reflection-based rebuild could not;
 * behaviour is preserved relative to upstream.
 */

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.report.RewriteJournal;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.te.IFurnaceManagerAccessor;
import com.mrfuzzihead.unidict.te.IPulverizerManagerAccessor;
import com.mrfuzzihead.unidict.te.IRecipeFurnaceFactory;
import com.mrfuzzihead.unidict.te.IRecipePulverizerFactory;
import com.mrfuzzihead.unidict.te.IRecipeSmelterFactory;
import com.mrfuzzihead.unidict.te.ISmelterManagerAccessor;

import cofh.thermalexpansion.util.crafting.FurnaceManager;
import cofh.thermalexpansion.util.crafting.PulverizerManager;
import cofh.thermalexpansion.util.crafting.SmelterManager;

@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
final class TEIntegration extends AbstractModuleThread {

    /** Redstone Furnace: single output, rebuilt via the {@link RecipeFurnaceInvoker}. */
    private static final OutputRewriter.OutputView<FurnaceManager.RecipeFurnace> FURNACE_VIEW = new OutputRewriter.OutputView<FurnaceManager.RecipeFurnace>() {

        @Override
        public List<ItemStack> getItems(final FurnaceManager.RecipeFurnace recipe) {
            return single(recipe.getOutput());
        }

        @Override
        public FurnaceManager.RecipeFurnace rebuild(final FurnaceManager.RecipeFurnace original,
            final List<ItemStack> mapped) {
            return ((IRecipeFurnaceFactory) original)
                .rebuildFurnace(original.getInput(), mapped.get(0), original.getEnergy());
        }
    };

    /** Pulverizer: primary + secondary outputs, rebuilt via the {@link RecipePulverizerInvoker}. */
    private static final OutputRewriter.OutputView<PulverizerManager.RecipePulverizer> PULVERIZER_VIEW = new OutputRewriter.OutputView<PulverizerManager.RecipePulverizer>() {

        @Override
        public List<ItemStack> getItems(final PulverizerManager.RecipePulverizer recipe) {
            return two(recipe.getPrimaryOutput(), recipe.getSecondaryOutput());
        }

        @Override
        public PulverizerManager.RecipePulverizer rebuild(final PulverizerManager.RecipePulverizer original,
            final List<ItemStack> mapped) {
            return ((IRecipePulverizerFactory) original).rebuildPulverizer(
                original.getInput(),
                mapped.get(0),
                mapped.get(1),
                original.getSecondaryOutputChance(),
                original.getEnergy());
        }
    };

    /** Induction Smelter: primary + secondary outputs, rebuilt via the {@link RecipeSmelterInvoker}. */
    private static final OutputRewriter.OutputView<SmelterManager.RecipeSmelter> SMELTER_VIEW = new OutputRewriter.OutputView<SmelterManager.RecipeSmelter>() {

        @Override
        public List<ItemStack> getItems(final SmelterManager.RecipeSmelter recipe) {
            return two(recipe.getPrimaryOutput(), recipe.getSecondaryOutput());
        }

        @Override
        public SmelterManager.RecipeSmelter rebuild(final SmelterManager.RecipeSmelter original,
            final List<ItemStack> mapped) {
            return ((IRecipeSmelterFactory) original).rebuildSmelter(
                original.getPrimaryInput(),
                original.getSecondaryInput(),
                mapped.get(0),
                mapped.get(1),
                original.getSecondaryOutputChance(),
                original.getEnergy());
        }
    };

    TEIntegration() {
        super("Thermal Expansion", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walks.
            if (resourceHandler != null && !resourceHandler.resources.isEmpty() && Config.thermalExpansion()) {
                final UnaryOperator<ItemStack> resolveMain = resourceHandler::getMainItemStack;
                final int rewritten = fixRedstoneFurnace(resolveMain) + fixPulverizer(resolveMain)
                    + fixInductionSmelter(resolveMain);
                UniDict.LOG.info(
                    threadName + "rewrote outputs of "
                        + rewritten
                        + " Thermal Expansion machine recipes to their canonical entries.");
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(true, "integration=TE", "machines=3", "rewritten=" + rewritten);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "The world seems to be more thermally involved.";
    }

    @SuppressWarnings("unchecked")
    private int fixRedstoneFurnace(final UnaryOperator<ItemStack> resolveMain) {
        final Map<FurnaceManager.ComparableItemStackFurnace, FurnaceManager.RecipeFurnace> recipes = (Map<FurnaceManager.ComparableItemStackFurnace, FurnaceManager.RecipeFurnace>) ((IFurnaceManagerAccessor) (Object) new FurnaceManager())
            .getRecipeMap();
        if (recipes == null) return 0;
        final int n = rewriteOutputs(recipes, FURNACE_VIEW, resolveMain);
        RewriteJournal.record("te", "redstoneFurnace", n);
        if (VerifyHarness.isEnabled())
            VerifyHarness.record(true, "integration=TE", "machine=redstoneFurnace", "rewritten=" + n);
        return n;
    }

    @SuppressWarnings("unchecked")
    private int fixPulverizer(final UnaryOperator<ItemStack> resolveMain) {
        final Map<PulverizerManager.ComparableItemStackPulverizer, PulverizerManager.RecipePulverizer> recipes = (Map<PulverizerManager.ComparableItemStackPulverizer, PulverizerManager.RecipePulverizer>) ((IPulverizerManagerAccessor) (Object) new PulverizerManager())
            .getRecipeMap();
        if (recipes == null) return 0;
        final int n = rewriteOutputs(recipes, PULVERIZER_VIEW, resolveMain);
        RewriteJournal.record("te", "pulverizer", n);
        if (VerifyHarness.isEnabled())
            VerifyHarness.record(true, "integration=TE", "machine=pulverizer", "rewritten=" + n);
        return n;
    }

    @SuppressWarnings("unchecked")
    private int fixInductionSmelter(final UnaryOperator<ItemStack> resolveMain) {
        final Map<List<SmelterManager.ComparableItemStackSmelter>, SmelterManager.RecipeSmelter> recipes = (Map<List<SmelterManager.ComparableItemStackSmelter>, SmelterManager.RecipeSmelter>) ((ISmelterManagerAccessor) (Object) new SmelterManager())
            .getRecipeMap();
        if (recipes == null) return 0;
        final int n = rewriteOutputs(recipes, SMELTER_VIEW, resolveMain);
        RewriteJournal.record("te", "inductionSmelter", n);
        if (VerifyHarness.isEnabled())
            VerifyHarness.record(true, "integration=TE", "machine=inductionSmelter", "rewritten=" + n);
        return n;
    }

    /** TE seam over the shared {@link OutputRewriter} core (non-destructive {@code Map.setValue}). */
    static <K, V> int rewriteOutputs(final Map<K, V> recipes, final OutputRewriter.OutputView<V> view,
        final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteOutputs(recipes, view, resolveMain);
    }

    private static List<ItemStack> single(final ItemStack stack) {
        final List<ItemStack> items = new java.util.ArrayList<>(1);
        items.add(stack);
        return items;
    }

    private static List<ItemStack> two(final ItemStack primary, final ItemStack secondary) {
        final List<ItemStack> items = new java.util.ArrayList<>(2);
        items.add(primary);
        items.add(secondary);
        return items;
    }
}
