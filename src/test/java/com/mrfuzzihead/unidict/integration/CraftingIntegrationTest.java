package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.crafting.FakeShapedRecipesAccessor;
import com.mrfuzzihead.unidict.crafting.FakeShapelessRecipesAccessor;

/**
 * T2 test for the M5 crafting output rewrite.
 * Fakes drive CraftingIntegration.rewriteCraftingOutputs through
 * its resolver seam -- asserting the BB-3 non-destructive guarantee:
 * only each recipe's output reference changes, count is preserved.
 *
 * Item/ItemStack stand-ins touch MC types but no MC statics (T2).
 */
class CraftingIntegrationTest {

    @Test
    void shapedRecipesAreRewrittenNonDestructively() {
        final Item itemA = new Item();
        final ItemStack outA = new ItemStack(itemA, 1, 1);
        final ItemStack outB = new ItemStack(itemA, 1, 2);
        final FakeShapedRecipesAccessor r1 = new FakeShapedRecipesAccessor(outA);
        final FakeShapedRecipesAccessor r2 = new FakeShapedRecipesAccessor(outB);
        final List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
        recipes.add(r1);
        recipes.add(r2);

        final ItemStack canonical = new ItemStack(itemA, 9, 3);
        final UnaryOperator<ItemStack> resolve = s -> (s == outA) ? canonical : s;

        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, resolve);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(canonical, r1.getRecipeOutput());
        assertSame(outB, r2.getRecipeOutput());
    }

    @Test
    void shapelessRecipesAreRewrittenNonDestructively() {
        final Item itemA = new Item();
        final ItemStack outA = new ItemStack(itemA, 1, 1);
        final ItemStack outB = new ItemStack(itemA, 1, 2);
        final FakeShapelessRecipesAccessor r1 = new FakeShapelessRecipesAccessor(outA);
        final FakeShapelessRecipesAccessor r2 = new FakeShapelessRecipesAccessor(outB);
        final List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
        recipes.add(r1);
        recipes.add(r2);

        final ItemStack canonical = new ItemStack(itemA, 9, 3);
        final UnaryOperator<ItemStack> resolve = s -> (s == outB) ? canonical : s;

        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, resolve);

        assertEquals(1, rewritten, "only the resolvable output should be rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(outA, r1.getRecipeOutput());
        assertSame(canonical, r2.getRecipeOutput());
    }

    @Test
    void unresolvedRecipesAreLeftAlone() {
        final Item itemA = new Item();
        final ItemStack out = new ItemStack(itemA, 1, 1);
        final FakeShapedRecipesAccessor recipe = new FakeShapedRecipesAccessor(out);
        final List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
        recipes.add(recipe);

        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(1, recipes.size());
        assertSame(out, recipe.getRecipeOutput());
    }

    @Test
    void nullRecipesAreSkipped() {
        final List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
        recipes.add(null);

        final int rewritten = CraftingIntegration.rewriteCraftingOutputs(recipes, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(1, recipes.size());
    }
}
