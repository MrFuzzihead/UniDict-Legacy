package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.forestry.FakeShapedOreRecipeAccessor;
import com.mrfuzzihead.unidict.forestry.IShapedOreRecipeAccessor;
import com.mrfuzzihead.unidict.integration.ForestryIntegration.ContainerRecipeView;

/**
 * T2 test for the Forestry integration (docs/PLAN.md §M7 #2). Two concerns:
 *
 * <p>
 * <b>Carpenter grid-output rewrite</b> — {@link ForestryIntegration#rewriteCarpenterOutputs} maps
 * each {@link IShapedOreRecipeAccessor} through the {@code resolveMain} resolver via the accessor
 * seam, asserting BB-3: only outputs change, no recipe is removed, null/unchanged outputs are left
 * alone. Driven through a fake accessor (no Forestry classes on the JUnit test classpath).
 *
 * <p>
 * <b>Squeezer container-recipe remnants rewrite</b> — {@link ForestryIntegration#rewriteContainerRecipes}
 * rewrites each {@link Map.Entry}'s value remnants in place, asserting BB-3: only the remnants stack
 * changes, the entry count is preserved, and the map key is unchanged. Driven through a neutral
 * {@link Holder} map and a neutral view (no Forestry types on the test classpath).
 */
class ForestryIntegrationTest {

    // ---- Carpenter (grid-recipe output) ----------------------------------

    @Test
    void rewriteCarpenterOutputsMapsRecipesAndPreservesCount() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);

        final List<IShapedOreRecipeAccessor> recipes = new ArrayList<>();
        recipes.add(new FakeShapedOreRecipeAccessor(outA));
        recipes.add(new FakeShapedOreRecipeAccessor(outB));

        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final UnaryOperator<ItemStack> resolveMain = s -> (s == outB) ? canonicalB : s;

        final int rewritten = ForestryIntegration.rewriteCarpenterOutputs(recipes, resolveMain);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(
            outA,
            recipes.get(0)
                .unidict$getOutput(),
            "unchanged output keeps its identity");
        assertSame(
            canonicalB,
            recipes.get(1)
                .unidict$getOutput(),
            "the mapped output is set in place");
    }

    @Test
    void rewriteCarpenterOutputsLeavesUnchangedRecipeAlone() {
        final Item itemA = new Item();
        final ItemStack output = new ItemStack(itemA, 1, 1);

        final FakeShapedOreRecipeAccessor recipe = new FakeShapedOreRecipeAccessor(output);
        final int rewritten = ForestryIntegration
            .rewriteCarpenterOutputs(new ArrayList<>(Arrays.asList(recipe)), UnaryOperator.identity());

        assertEquals(0, rewritten, "identity resolver must not rewrite anything");
        assertSame(output, recipe.unidict$getOutput());
    }

    @Test
    void rewriteCarpenterOutputsSkipsNullEntries() {
        final Item itemA = new Item();
        final FakeShapedOreRecipeAccessor recipe = new FakeShapedOreRecipeAccessor(new ItemStack(itemA, 1, 1));
        final List<IShapedOreRecipeAccessor> recipes = new ArrayList<>(Arrays.asList(recipe, null));

        final int rewritten = ForestryIntegration.rewriteCarpenterOutputs(recipes, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(2, recipes.size(), "a null entry must not be removed");
        assertNull(recipes.get(1));
    }

    // ---- Squeezer (container-recipe remnants) -----------------------------

    /** Neutral container-recipe holder so the generic seam is testable with no Forestry classes. */
    private static final class Holder {

        ItemStack remnants;
        final int time;
        final float chance;

        Holder(final ItemStack remnants, final int time, final float chance) {
            this.remnants = remnants;
            this.time = time;
            this.chance = chance;
        }
    }

    private static final ContainerRecipeView<Holder> HOLDER_VIEW = new ContainerRecipeView<Holder>() {

        @Override
        public ItemStack getRemnants(final Holder recipe) {
            return recipe.remnants;
        }

        @Override
        public int getProcessingTime(final Holder recipe) {
            return recipe.time;
        }

        @Override
        public float getRemnantsChance(final Holder recipe) {
            return recipe.chance;
        }

        @Override
        public Holder rebuild(final Holder original, final ItemStack canonicalRemnants) {
            return new Holder(canonicalRemnants, original.time, original.chance);
        }
    };

    @Test
    void rewriteContainerRecipesRemapsRemnantsAndPreservesEntryCountAndKey() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);
        final ItemStack keyA = new ItemStack(itemA, 1, 0);
        final ItemStack keyB = new ItemStack(itemB, 1, 0);

        final Map<ItemStack, Holder> containerRecipes = new HashMap<>();
        containerRecipes.put(keyA, new Holder(outA, 20, 0.5f));
        containerRecipes.put(keyB, new Holder(outB, 40, 0.25f));

        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final UnaryOperator<ItemStack> resolveMain = s -> (s == outB) ? canonicalB : s;

        final int rewritten = ForestryIntegration.rewriteContainerRecipes(containerRecipes, HOLDER_VIEW, resolveMain);

        assertEquals(1, rewritten, "only the resolvable remnants should be rewritten");
        assertEquals(2, containerRecipes.size(), "rewriting must never add or remove entries");
        assertSame(outA, containerRecipes.get(keyA).remnants, "unchanged remnants keep their identity");
        assertSame(canonicalB, containerRecipes.get(keyB).remnants, "the mapped remnants are set in place");
        assertEquals(20, containerRecipes.get(keyA).time, "processing time is preserved for unchanged entries");
        assertEquals(40, containerRecipes.get(keyB).time, "processing time is preserved for rewritten entries");
        assertEquals(0.5f, containerRecipes.get(keyA).chance, 0.001f, "chance is preserved for unchanged entries");
        assertEquals(0.25f, containerRecipes.get(keyB).chance, 0.001f, "chance is preserved for rewritten entries");
    }

    @Test
    void rewriteContainerRecipesLeavesNullAndUnchangedEntriesAlone() {
        final Item itemA = new Item();
        final ItemStack outA = new ItemStack(itemA, 1, 1);
        final ItemStack keyA = new ItemStack(itemA, 1, 0);

        final Map<ItemStack, Holder> containerRecipes = new HashMap<>();
        containerRecipes.put(keyA, new Holder(outA, 10, 1.0f));

        final int rewritten = ForestryIntegration
            .rewriteContainerRecipes(containerRecipes, HOLDER_VIEW, UnaryOperator.identity());

        assertEquals(0, rewritten, "identity resolver must not rewrite anything");
        assertSame(outA, containerRecipes.get(keyA).remnants);
        assertEquals(1, containerRecipes.size(), "rewriting must never change the entry count");
    }

    @Test
    void rewriteContainerRecipesSkipsNullRemnantsWithoutIncident() {
        final Item itemA = new Item();
        final ItemStack keyA = new ItemStack(itemA, 1, 0);

        final Map<ItemStack, Holder> containerRecipes = new HashMap<>();
        containerRecipes.put(keyA, new Holder(null, 5, 0.75f)); // null remnants -> no byproduct

        final int rewritten = ForestryIntegration
            .rewriteContainerRecipes(containerRecipes, HOLDER_VIEW, UnaryOperator.identity());

        assertEquals(0, rewritten, "a recipe with no remnants must be skipped");
        assertNull(containerRecipes.get(keyA).remnants, "null remnants must be left as-is");
    }
}
