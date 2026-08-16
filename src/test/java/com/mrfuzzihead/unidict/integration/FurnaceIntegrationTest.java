package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

/**
 * T2 test for the M4 vanilla furnace rewrite (docs/PLAN.md §M4). Fabricted recipe maps with
 * {@code Item}/{@code ItemStack} stand-ins drive {@link FurnaceIntegration#rewriteOutputs} through
 * its resolver seam — asserting the BB-3 non-destructive guarantee: only outputs change, no recipe
 * is added or removed, unchanged maps and {@code null} outputs are left alone.
 *
 * <p>
 * The {@code Item}/{@code ItemStack} stand-ins touch MC <em>types</em> but no MC <em>statics</em>;
 * the real {@code FurnaceRecipes} static is exercised in-game (T3).
 */
class FurnaceIntegrationTest {

    @Test
    void rewriteOutputsRemapsOutputsNonDestructively() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack inputA = new ItemStack(itemA, 1, 0);
        final ItemStack inputB = new ItemStack(itemB, 1, 0);
        final ItemStack outputA = new ItemStack(itemA, 2, 1);
        final ItemStack outputB = new ItemStack(itemB, 2, 1);

        final Map<ItemStack, ItemStack> recipes = new HashMap<>();
        recipes.put(inputA, outputA);
        recipes.put(inputB, outputB);

        final ItemStack canonicalA = new ItemStack(itemA, 9, 3);
        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final UnaryOperator<ItemStack> resolveMain = s -> {
            if (s == outputA) return canonicalA;
            if (s == outputB) return canonicalB;
            return s;
        };

        final int rewritten = FurnaceIntegration.rewriteOutputs(recipes, resolveMain);

        assertEquals(2, rewritten, "both outputs should have been rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertTrue(recipes.containsKey(inputA), "inputs must be preserved");
        assertTrue(recipes.containsKey(inputB), "inputs must be preserved");
        assertSame(canonicalA, recipes.get(inputA));
        assertSame(canonicalB, recipes.get(inputB));
    }

    @Test
    void rewriteOutputsLeavesUnresolvedAndNullOutputsAlone() {
        final Item itemA = new Item();
        final ItemStack input = new ItemStack(itemA, 1, 0);
        final ItemStack unresolved = new ItemStack(itemA, 2, 1);
        final ItemStack nullKey = new ItemStack(itemA, 1, 5);

        final Map<ItemStack, ItemStack> recipes = new HashMap<>();
        recipes.put(input, unresolved);
        recipes.put(nullKey, null);

        // Identity resolver: every output "resolves" to itself -> nothing changes.
        final int rewritten = FurnaceIntegration.rewriteOutputs(recipes, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(2, recipes.size());
        assertSame(unresolved, recipes.get(input), "unresolved output must stay identical");
        assertNull(recipes.get(nullKey), "null output must not be touched");
    }

    @Test
    void rewriteOutputsCountsOnlyDistinctOutputChanges() {
        final Item itemA = new Item();
        final ItemStack inputA = new ItemStack(itemA, 1, 0);
        final ItemStack inputB = new ItemStack(itemA, 1, 1);
        final ItemStack outputA = new ItemStack(itemA, 2, 1);
        final ItemStack outputB = new ItemStack(itemA, 2, 2);

        final Map<ItemStack, ItemStack> recipes = new HashMap<>();
        recipes.put(inputA, outputA);
        recipes.put(inputB, outputB);

        // Only outputA resolves to a different canonical stack; outputB maps to itself.
        final ItemStack canonical = new ItemStack(itemA, 9, 9);
        final UnaryOperator<ItemStack> resolveMain = s -> (s == outputA) ? canonical : s;

        final int rewritten = FurnaceIntegration.rewriteOutputs(recipes, resolveMain);

        assertEquals(1, rewritten);
        assertEquals(2, recipes.size());
        assertSame(canonical, recipes.get(inputA));
        assertSame(outputB, recipes.get(inputB));
    }
}
