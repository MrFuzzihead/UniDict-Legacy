package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import ic2.api.recipe.RecipeOutput;

/**
 * T2 test for the M6 IC2 machine rewrite (docs/PLAN.md §M6 #3). Fabricated {@code Map<String,
 * RecipeOutput>} stand-ins — real {@link RecipeOutput} instances, string keys standing in for the
 * machine maps' {@code IRecipeInput} keys — drive {@link IC2Integration#rewriteOutputs} through its
 * resolver seam, asserting the BB-3 non-destructive guarantee: only outputs change, no recipe is
 * added or removed, unchanged and {@code null} outputs are left alone, and the returned count is
 * per-<em>output</em> (not per stack).
 *
 * <p>
 * The generic-key seam means the T2 test needs no IC2 recipe-input stubs. The real
 * {@code ic2.api.recipe.Recipes} statics are exercised in-game (T3).
 */
class IC2IntegrationTest {

    private static RecipeOutput recipe(final ItemStack... items) {
        return new RecipeOutput(new NBTTagCompound(), new ArrayList<>(Arrays.asList(items)));
    }

    @Test
    void rewriteOutputsRemapsMachineOutputsNonDestructively() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);

        final Map<String, RecipeOutput> recipes = new HashMap<>();
        recipes.put("c1", recipe(outA, outB));
        recipes.put("c2", recipe(outB));

        final ItemStack canonicalA = new ItemStack(itemA, 9, 3);
        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final UnaryOperator<ItemStack> resolveMain = s -> {
            if (s == outA) return canonicalA;
            if (s == outB) return canonicalB;
            return s;
        };

        final int rewritten = IC2Integration.rewriteOutputs(recipes, resolveMain);

        assertEquals(2, rewritten, "both machine outputs should have been rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertEquals(2, recipes.get("c1").items.size(), "item-list cardinality must be preserved");
        assertSame(canonicalA, recipes.get("c1").items.get(0));
        assertSame(canonicalB, recipes.get("c1").items.get(1));
        assertSame(canonicalB, recipes.get("c2").items.get(0));
    }

    @Test
    void rewriteOutputsLeavesUnchangedAndNullOutputsAlone() {
        final Item itemA = new Item();
        final ItemStack stack = new ItemStack(itemA, 2, 1);

        final Map<String, RecipeOutput> recipes = new HashMap<>();
        recipes.put("k1", recipe(stack));
        recipes.put("k2", null);

        // Identity resolver: every stack "resolves" to itself -> nothing changes.
        final int rewritten = IC2Integration.rewriteOutputs(recipes, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(2, recipes.size());
        assertSame(stack, recipes.get("k1").items.get(0));
        assertNull(recipes.get("k2"));
    }

    @Test
    void rewriteOutputsCountsOnlyDistinctOutputChanges() {
        final Item itemA = new Item();
        final ItemStack s1 = new ItemStack(itemA, 1, 1);
        final ItemStack s2 = new ItemStack(itemA, 1, 2);
        final ItemStack canonical = new ItemStack(itemA, 9, 9);

        // 'unchanged' has only s1 (resolves to itself) -> not rewritten;
        // 'changed' has only s2 (resolves to canonical) -> rewritten once;
        // 'partial' has s1 + s2 -> rewritten once (one stack changed), not twice.
        final Map<String, RecipeOutput> recipes = new HashMap<>();
        recipes.put("unchanged", recipe(s1));
        recipes.put("changed", recipe(s2));
        recipes.put("partial", recipe(s1, s2));

        final UnaryOperator<ItemStack> resolveMain = s -> (s == s2) ? canonical : s;

        final int rewritten = IC2Integration.rewriteOutputs(recipes, resolveMain);

        assertEquals(2, rewritten);
        assertEquals(3, recipes.size());
        assertSame(s1, recipes.get("unchanged").items.get(0));
        assertSame(canonical, recipes.get("changed").items.get(0));
        assertEquals(2, recipes.get("partial").items.size());
        assertSame(s1, recipes.get("partial").items.get(0));
        assertSame(canonical, recipes.get("partial").items.get(1));
        assertTrue(recipes.containsKey("unchanged"), "all recipes must be preserved");
    }
}
