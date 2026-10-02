package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.pure.report.RewriteRecord;
import com.mrfuzzihead.unidict.report.RewriteJournal;

/**
 * T2 test for the Tinkers' Construct smeltery casting rewrite (docs/INTEGRATIONS.md §Tinkers'
 * Construct). Drives the package-private BB-3 seam ({@link TinkersConstructIntegration#rewriteCastingOutputs})
 * with a neutral mutable-output holder standing in for TiC's {@code CastingRecipe} (not on the JUnit
 * test classpath), asserting the non-destructive guarantee: only outputs change, no recipe is added or
 * removed, unchanged and null outputs are left alone, and the count is per-output.
 *
 * <p>
 * The two properties that make this module different from every other kept integration and that the
 * test therefore pins down explicitly:
 * <ul>
 * <li><b>Inputs are never touched.</b> Upstream's {@code TConUniHelper#removeCast} walked both the
 * output AND the cast and deleted the recipe; the port writes only the output, because a cast is
 * consumed rather than produced (inputs are M5-deferred).</li>
 * <li><b>A null output is legitimate</b> — a bare "pour the metal" recipe has no item result — and must
 * be reported as an empty item list so the core sees no change and never calls {@code rebuild}.</li>
 * </ul>
 *
 * <p>
 * The {@link Item}/{@link ItemStack} stand-ins touch MC <em>types</em> but no MC <em>statics</em>
 * (T2); the real {@code TConstructRegistry} statics and the actual smeltery pour are exercised in-game
 * (T3).
 */
class TinkersConstructIntegrationTest {

    private static final UnaryOperator<ItemStack> IDENTITY = UnaryOperator.identity();

    /**
     * Neutral stand-in for {@code CastingRecipe}: a single mutable {@code output} field plus the
     * input fields the port must leave alone, so the test can assert both.
     */
    private static final class Holder {

        ItemStack output;
        final ItemStack cast;
        final int coolTime;

        Holder(final ItemStack output, final ItemStack cast, final int coolTime) {
            this.output = output;
            this.cast = cast;
            this.coolTime = coolTime;
        }
    }

    /** Mirrors {@code TinkersConstructIntegration.CASTING_VIEW}: mutate the output in place. */
    private static final OutputRewriter.OutputView<Holder> HOLDER_VIEW = new OutputRewriter.OutputView<Holder>() {

        @Override
        public List<ItemStack> getItems(final Holder recipe) {
            if (recipe.output == null) return Collections.emptyList();
            final List<ItemStack> items = new ArrayList<>(1);
            items.add(recipe.output);
            return items;
        }

        @Override
        public Holder rebuild(final Holder original, final List<ItemStack> mapped) {
            original.output = mapped.get(0);
            return original;
        }
    };

    @Test
    void rewriteCastingOutputsRemapsOutputsInPlaceWithoutRemovingRecipes() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);
        final ItemStack canonicalB = new ItemStack(itemB, 2, 0);

        final List<Holder> recipes = new ArrayList<>(
            Arrays.asList(new Holder(outA, null, 20), new Holder(outB, null, 40)));

        final int rewritten = TinkersConstructIntegration
            .rewriteCastingOutputs(recipes, HOLDER_VIEW, s -> (s == outB) ? canonicalB : s);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting a list must never change its size (no remove)");
        assertSame(outA, recipes.get(0).output, "unchanged output keeps its identity");
        assertSame(canonicalB, recipes.get(1).output, "the mapped output is written in place");
    }

    @Test
    void rewriteCastingOutputsNeverTouchesCastInputsOrOtherRecipeFields() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack out = new ItemStack(itemA, 1, 0);
        final ItemStack cast = new ItemStack(itemB, 1, 5);
        final ItemStack canonicalOut = new ItemStack(itemA, 1, 0);

        final Holder recipe = new Holder(out, cast, 75);
        final List<Holder> recipes = new ArrayList<>(Collections.singletonList(recipe));

        final int rewritten = TinkersConstructIntegration
            .rewriteCastingOutputs(recipes, HOLDER_VIEW, s -> (s == out) ? canonicalOut : s);

        assertEquals(1, rewritten);
        assertSame(canonicalOut, recipe.output, "the output is the only field the rewrite writes");
        assertSame(cast, recipe.cast, "the cast (consumed input) must never be rewritten");
        assertEquals(75, recipe.coolTime, "the remaining recipe fields must survive an in-place rewrite");
    }

    @Test
    void rewriteCastingOutputsSkipsNullOutputsInsteadOfCrashing() {
        final Item itemA = new Item();
        final ItemStack outA = new ItemStack(itemA, 1, 1);
        final Holder pourOnly = new Holder(null, null, 10); // bare "pour the metal" recipe, no item result
        final Holder normal = new Holder(outA, null, 20);

        final List<Holder> recipes = new ArrayList<>(Arrays.asList(pourOnly, normal));

        // Identity resolver: a null output maps to an empty item list -> no change -> no rebuild -> no NPE.
        assertEquals(0, TinkersConstructIntegration.rewriteCastingOutputs(recipes, HOLDER_VIEW, IDENTITY));
        assertEquals(2, recipes.size(), "a null output must not remove the recipe");
        assertNull(pourOnly.output, "a null output stays null");
        assertSame(outA, normal.output, "an unchanged output keeps its identity");
    }

    @Test
    void rewriteCastingOutputsIsIdempotentForTheServerStartRerun() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outB = new ItemStack(itemB, 3, 0);
        final ItemStack canonicalB = new ItemStack(itemB, 3, 0);

        final List<Holder> recipes = new ArrayList<>(Collections.singletonList(new Holder(outB, null, 20)));
        final UnaryOperator<ItemStack> resolveMain = s -> (s == outB) ? canonicalB : s;

        assertEquals(1, TinkersConstructIntegration.rewriteCastingOutputs(recipes, HOLDER_VIEW, resolveMain));
        // Second pass (the FMLServerStarting re-run): the output is already canonical, so nothing is
        // rewritten and the list is untouched.
        assertEquals(0, TinkersConstructIntegration.rewriteCastingOutputs(recipes, HOLDER_VIEW, resolveMain));
        assertEquals(1, recipes.size());
        assertSame(canonicalB, recipes.get(0).output);
    }

    @Test
    void rewriteCastingOutputsHandlesEmptyAndNullListEntries() {
        final List<Holder> recipes = new ArrayList<>(Arrays.asList(null, new Holder(null, null, 5)));

        assertEquals(0, TinkersConstructIntegration.rewriteCastingOutputs(recipes, HOLDER_VIEW, IDENTITY));
        assertEquals(2, recipes.size(), "a null list entry must be skipped, never removed");
        assertNull(recipes.get(0));
    }

    @Test
    void journalRecordForCastingIsIdempotentAcrossTheTwoPasses() {
        RewriteJournal.clear();
        try {
            RewriteJournal.record(TinkersConstructIntegration.SOURCE, "castingTable", 4);
            RewriteJournal.record(TinkersConstructIntegration.SOURCE, "castingBasin", 2);
            // The server-start re-run updates the same (source, machine) keys instead of appending.
            RewriteJournal.record(TinkersConstructIntegration.SOURCE, "castingTable", 1);
            RewriteJournal.record(TinkersConstructIntegration.SOURCE, "castingBasin", 0);

            final List<RewriteRecord> records = RewriteJournal.snapshot();
            assertEquals(2, records.size(), "a re-run must not duplicate journal entries");
            assertTrue(
                records.stream()
                    .anyMatch(r -> "castingTable".equals(r.machine) && r.count == 1),
                "table holds the final count");
            assertTrue(
                records.stream()
                    .anyMatch(r -> "castingBasin".equals(r.machine) && r.count == 0),
                "basin holds the final count");
        } finally {
            RewriteJournal.clear();
        }
    }
}
