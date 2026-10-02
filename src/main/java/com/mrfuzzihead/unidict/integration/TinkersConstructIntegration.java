package com.mrfuzzihead.unidict.integration;

/*
 * Tinkers' Construct smeltery casting rewrite (docs/INTEGRATIONS.md §Tinkers' Construct). This is the
 * BB-3 port of upstream's wanion.unidict.api.helper.TConUniHelper#removeCast — rewritten to be
 * NON-DESTRUCTIVE: upstream ITERATED AND REMOVED every casting recipe whose output or cast was a
 * hidden variant, which deleted a player's ability to cast a TiC item at all. Here the canonical
 * entry is written IN PLACE instead, so the recipe survives and simply pours the unified item.
 * <p>SCOPE — why only casting, and why the melt side is out of reach: a TiC smeltery never yields an
 * ItemStack from its melt/alloy stages. javap on tconstruct.library.crafting.Smeltery shows
 * `smeltingList : Map<ItemMetaWrapper, FluidStack>`, `getSmelteryResult(ItemStack) -> FluidStack` and
 * `mixMetals(List<FluidStack>) -> List<FluidStack>` — melting an Ardite ore produces MOLTEN ARDITE, a
 * fluid, and alloying stays fluid-to-fluid. UniDict's model is ItemStack/OreDictionary-based (Resource,
 * MetaItem, getMainItemStack), so there is no item at the melt stage to redirect to e.g. Thermal
 * Foundation's: that would need a fluid-equivalence class, which 1.7.10 has no OreDict-style model for
 * (BB-4 — the same deferral already recorded for the Forestry squeezer/fermenter fluid outputs). An
 * ingot first becomes an ITEM at the casting step, so casting is the one and only ItemStack-output
 * seam, and it is the one rewritten here.
 * <p>SOURCE (all public TiC API — no accessor mixin, no @Invoker, no reflection):
 * <ul>
 * <li>{@code TConstructRegistry.getTableCasting()} / {@code getBasinCasting()} -> {@link LiquidCasting}
 * — the Casting Table and Casting Basin share this one recipe holder.</li>
 * <li>{@code LiquidCasting.getCastingRecipes()} returns the LIVE backing {@code ArrayList} (upstream's
 * removeCast relied on exactly that to mutate it), so no accessor seam is needed.</li>
 * <li>{@link CastingRecipe#output} is a public, non-final field, so the rewrite is a single in-place
 * field write — no recipe rebuild, so no chance of losing a field (unlike IE's immutable value recipes,
 * which need the @Invoker rebuild path; see {@link TEIntegration} for that shape).</li>
 * </ul>
 * {@code LiquidCasting.getCastingRecipe(FluidStack, ItemStack)} scans its list on every lookup and
 * {@code CastingRecipe.getResult()} returns the field, so the changed output is live immediately — no
 * cache to bust. {@code cast} (the ItemStack input) and {@code castingMetal} (the FluidStack input) are
 * deliberately untouched: inputs are M5-deferred (INTEGRATIONS.md), and a cast is consumed, not produced.
 * <p>ORDER: runs at LOAD_COMPLETE (the latest LoadStage) because TiC and its addons (ExtraTiC,
 * TSteelworks, Mariculture, TiCTooltips are all present in the dev pack) register casting recipes during
 * their own init, and the earlier POST_INIT default was shown to miss late registrations (the reason
 * the vanilla Furnace rewrite was bumped to LOAD_COMPLETE — see STATUS.md). A server-start re-run
 * ({@link #runAtServerStart()}) is idempotent and catches anything registered later still, mirroring
 * {@link IC2Integration#runAtServerStart()}.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.CastingRecipe;
import tconstruct.library.crafting.LiquidCasting;

@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
final class TinkersConstructIntegration extends AbstractModuleThread {

    /** {@link RewriteJournal} source key — stable so a server-start re-run updates, not duplicates. */
    static final String SOURCE = "tinkersconstruct";

    /**
     * Adapts a {@link CastingRecipe} to the shared {@link OutputRewriter} core. The rebuild writes
     * {@code output} IN PLACE and returns the same instance: TiC holds no separate cache of the output
     * (its lookup re-reads this field), so an in-place write is both sufficient and the only way to
     * keep the recipe's other fields (coolTime, consumeCast, ignoreNBT, fluidRenderProperties)
     * untouched — a rebuild could not set them.
     */
    private static final OutputRewriter.OutputView<CastingRecipe> CASTING_VIEW = new OutputRewriter.OutputView<CastingRecipe>() {

        @Override
        public List<ItemStack> getItems(final CastingRecipe recipe) {
            return singleOutput(recipe.output);
        }

        @Override
        public CastingRecipe rebuild(final CastingRecipe original, final List<ItemStack> mapped) {
            original.output = mapped.get(0);
            return original;
        }
    };

    TinkersConstructIntegration() {
        super("Tinkers' Construct", "Integration");
    }

    @Override
    public String call() {
        try {
            final int rewritten = runCasting();
            return threadName + "rewrote outputs of "
                + rewritten
                + " Tinkers' Construct casting recipes to their canonical entries.";
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
            return threadName + "The smeltery keeps its own ideas about metals.";
        }
    }

    /**
     * Rewrites the Casting Table and Casting Basin recipe outputs to the canonical (main) entry of their
     * unified resource. Runs at LOAD_COMPLETE and again at server start; idempotent by construction
     * (the shared core only writes when the mapped stack differs by reference from the current one, and
     * {@link RewriteJournal#record} updates rather than appends on a re-run).
     *
     * @return the number of casting outputs rewritten on this pass
     */
    static int runCasting() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        // Early-skip: with no unified resource the canonical lookup is a no-op, so skip the walk.
        if (resourceHandler == null || resourceHandler.resources.isEmpty() || !Config.tinkersConstruct()) return 0;
        final UnaryOperator<ItemStack> resolveMain = resourceHandler::getMainItemStack;
        int rewritten = 0;
        // Fixed order (table, then basin) keeps the journal/verify dump diffable run-to-run.
        rewritten += rewriteCasting(getCastingTable(), "castingTable", resolveMain);
        rewritten += rewriteCasting(getCastingBasin(), "castingBasin", resolveMain);
        if (rewritten > 0) {
            UniDict.LOG.info(
                "Tinkers' Construct Integration: rewrote outputs of " + rewritten
                    + " smeltery casting recipes to their canonical entries.");
            if (VerifyHarness.isEnabled()) {
                VerifyHarness.record(true, "integration=TinkersConstruct", "rewritten=" + rewritten);
            }
        }
        return rewritten;
    }

    /**
     * Server-started re-run of the casting rewrite. TiC addons may register additional casting recipes
     * after POST_INIT; the (idempotent, in-place) rewrite is re-run so the authoritative final recipe
     * list is canonicalized. Mirrors {@link IC2Integration#runAtServerStart()}.
     *
     * @return the number of casting outputs rewritten on this pass
     */
    static int runAtServerStart() {
        final int rewritten = runCasting();
        if (rewritten > 0) {
            UniDict.LOG.info(
                "[tic-server-start] re-rewrote outputs of " + rewritten
                    + " Tinkers' Construct casting recipes to their canonical entries.");
        }
        return rewritten;
    }

    /** One casting holder: journal + verify line for it, then the shared non-destructive rewrite. */
    private static int rewriteCasting(final LiquidCasting casting, final String machine,
        final UnaryOperator<ItemStack> resolveMain) {
        if (casting == null) return 0;
        final List<CastingRecipe> recipes = casting.getCastingRecipes();
        if (recipes == null) return 0;
        final int n = rewriteCastingOutputs(recipes, CASTING_VIEW, resolveMain);
        RewriteJournal.record(SOURCE, machine, n);
        if (VerifyHarness.isEnabled()) {
            VerifyHarness.record(true, "integration=TinkersConstruct", "machine=" + machine, "rewritten=" + n);
        }
        return n;
    }

    /**
     * T2 seam over the shared non-destructive {@link OutputRewriter#rewriteList} core. Generic in the
     * recipe type so the test drives a neutral holder instead of TiC's {@code CastingRecipe} (which is
     * not on the JUnit classpath) — the same seam/fake split as every other kept integration.
     */
    static <R> int rewriteCastingOutputs(final List<R> recipes, final OutputRewriter.OutputView<R> view,
        final UnaryOperator<ItemStack> resolveMain) {
        return OutputRewriter.rewriteList(recipes, view, resolveMain);
    }

    /**
     * A recipe's single output as a one-element list, or an EMPTY list for a null output. TiC uses
     * {@code output == null} legitimately (a bare "pour the metal" recipe registered through
     * {@code addCastingRecipe(ItemStack, FluidStack, int, int)} has no item result), so null must be
     * reported as "nothing to rewrite": the shared core maps 1:1 and then sees no change, and never
     * calls {@code rebuild} (which would NPE on {@code mapped.get(0)}).
     */
    private static List<ItemStack> singleOutput(final ItemStack stack) {
        if (stack == null) return Collections.emptyList();
        final List<ItemStack> items = new ArrayList<>(1);
        items.add(stack);
        return items;
    }

    private static LiquidCasting getCastingTable() {
        return TConstructRegistry.getTableCasting();
    }

    private static LiquidCasting getCastingBasin() {
        return TConstructRegistry.getBasinCasting();
    }
}
