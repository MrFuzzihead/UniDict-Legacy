package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.railcraft.FakeBlastFurnaceCraftingManagerAccessor;

/**
 * T2 test for the M7 Railcraft integration (docs/PLAN.md §M7 #3). The blast-furnace recipe list is
 * reached through {@link FakeBlastFurnaceCraftingManagerAccessor} (the accessor seam hacked by
 * {@code BlastFurnaceCraftingManagerMixin}); the fabricated list is then driven through the
 * package-private, generic {@link RailcraftIntegration#rewriteRecipes} seam over the shared
 * non-destructive {@link OutputRewriter#rewriteList} core. A neutral {@link Holder} view stands in for
 * Railcraft's immutable {@code BlastFurnaceRecipe} (not on the JUnit test classpath), asserting BB-3:
 * only outputs change, no recipe is added or removed, {@code null} entries and unchanged outputs are
 * left alone.
 *
 * <p>
 * The {@link Item}/{@link ItemStack} stand-ins touch MC <em>types</em> but no MC <em>statics</em> (T2);
 * the real {@code BlastFurnaceCraftingManager} statics are exercised in-game (T3).
 */
class RailcraftIntegrationTest {

    /** Neutral output holder so the generic seam is testable with no Railcraft classes. */
    private static final class Holder {

        final List<ItemStack> items;

        Holder(final List<ItemStack> items) {
            this.items = items;
        }
    }

    private static final OutputRewriter.OutputView<Holder> HOLDER_VIEW = new OutputRewriter.OutputView<Holder>() {

        @Override
        public List<ItemStack> getItems(final Holder output) {
            return output.items;
        }

        @Override
        public Holder rebuild(final Holder original, final List<ItemStack> mapped) {
            return new Holder(new ArrayList<>(mapped));
        }
    };

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void rewriteRecipesThroughAccessorRemapsOutputsInPlace() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);

        final List<Holder> underlying = new ArrayList<>();
        underlying.add(new Holder(new ArrayList<>(Arrays.asList(outA))));
        underlying.add(new Holder(new ArrayList<>(Arrays.asList(outB))));

        // The accessor fake is what the integration sees; it hands back the SAME list to rewrite in place.
        final FakeBlastFurnaceCraftingManagerAccessor manager = new FakeBlastFurnaceCraftingManagerAccessor(underlying);
        final List<Holder> recipes = (List<Holder>) manager.getRecipes();

        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final int rewritten = RailcraftIntegration
            .rewriteRecipes(recipes, HOLDER_VIEW, s -> (s == outB) ? canonicalB : s);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(outA, recipes.get(0).items.get(0), "unchanged output keeps its identity");
        assertSame(canonicalB, recipes.get(1).items.get(0), "the mapped output is applied in place");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void rewriteRecipesLeavesNullEntriesAndUnchangedOutputsAlone() {
        final Item itemA = new Item();
        final Holder entry = new Holder(new ArrayList<>(Arrays.asList(new ItemStack(itemA, 1, 1))));

        final List<Holder> underlying = new ArrayList<>(Arrays.asList(entry, null));
        final FakeBlastFurnaceCraftingManagerAccessor manager = new FakeBlastFurnaceCraftingManagerAccessor(underlying);
        final List<Holder> recipes = (List<Holder>) manager.getRecipes();

        final int rewritten = RailcraftIntegration.rewriteRecipes(recipes, HOLDER_VIEW, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(2, recipes.size(), "rewriting a list must never change its size (no remove)");
        assertSame(entry, recipes.get(0), "unchanged entry keeps its identity");
        assertNull(recipes.get(1), "null list entry is skipped and left untouched");
    }

    /** Neutral Rock Crusher output holder so the chance-outputs seam is testable with no Railcraft classes. */
    private static final class CrushRecipe {

        final List<Map.Entry<ItemStack, Float>> outputs;

        CrushRecipe(final List<Map.Entry<ItemStack, Float>> outputs) {
            this.outputs = outputs;
        }
    }

    @Test
    void rewriteCrusherOutputsRemapsChanceOutputsInPlacePreservingCountAndChance() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);
        final List<Map.Entry<ItemStack, Float>> outputs = new ArrayList<>(
            Arrays.asList(
                new AbstractMap.SimpleImmutableEntry<>(outA, 1.0f),
                new AbstractMap.SimpleImmutableEntry<>(outB, 0.25f),
                null));

        // The Rock Crusher output list is a live, nullable, chance-paired entry list (BB-3: in place).
        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final int rewritten = RailcraftIntegration.rewriteCrusherOutputs(
            Collections.singletonList(new CrushRecipe(outputs)),
            recipe -> recipe.outputs,
            s -> (s == outB) ? canonicalB : s);

        assertEquals(1, rewritten, "only the resolvable output entry should be rewritten");
        assertEquals(3, outputs.size(), "rewriting must never change the entry count (no remove)");
        assertSame(
            outA,
            outputs.get(0)
                .getKey(),
            "unchanged output keeps its identity");
        assertEquals(
            1.0f,
            outputs.get(0)
                .getValue()
                .floatValue(),
            "chance of an unchanged output is preserved");
        assertSame(
            canonicalB,
            outputs.get(1)
                .getKey(),
            "the mapped output is applied in place");
        assertEquals(
            0.25f,
            outputs.get(1)
                .getValue()
                .floatValue(),
            "chance of a rewritten output is preserved");
        assertNull(outputs.get(2), "null output entry is skipped and left untouched");
    }

    @Test
    void rewriteCrusherOutputsLeavesUnchangedOutputsAlone() {
        final Item itemA = new Item();
        final ItemStack unchanged = new ItemStack(itemA, 1, 1);
        final List<Map.Entry<ItemStack, Float>> outputs = new ArrayList<>(
            Collections.singletonList(new AbstractMap.SimpleImmutableEntry<>(unchanged, 0.5f)));

        final int rewritten = RailcraftIntegration.rewriteCrusherOutputs(
            Collections.singletonList(new CrushRecipe(outputs)),
            recipe -> recipe.outputs,
            UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertSame(
            unchanged,
            outputs.get(0)
                .getKey(),
            "an unchanged output entry keeps its identity");
    }
}
