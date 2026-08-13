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

/**
 * T2 test for the shared, non-destructive {@link OutputRewriter} core that Furnace and IC2 both
 * delegate to (docs/PLAN.md §BB-3, M6). A neutral {@link Holder} view drives the generic multi-item
 * path without any mod API, and {@link OutputRewriter#rewriteSingleOutputs} verifies the single-stack
 * convenience (the vanilla furnace shape). Together they assert the BB-3 guarantee at the shared
 * core: only outputs change, no recipe is added or removed, unchanged/{@code null} outputs untouched.
 *
 * <p>
 * Uses MC {@code Item}/{@code ItemStack} <em>types</em> only, no MC statics (T2).
 */
class OutputRewriterTest {

    /** Minimal output holder so the generic seam is testable with no external recipe classes. */
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

    @Test
    void genericCoreRemapsOutputsNonDestructively() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);

        final Map<String, Holder> recipes = new HashMap<>();
        recipes.put("c1", new Holder(new ArrayList<>(Arrays.asList(outA, outB))));
        recipes.put("c2", new Holder(new ArrayList<>(Arrays.asList(outB))));

        final ItemStack canonicalA = new ItemStack(itemA, 9, 3);
        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final UnaryOperator<ItemStack> resolveMain = s -> {
            if (s == outA) return canonicalA;
            if (s == outB) return canonicalB;
            return s;
        };

        final int rewritten = OutputRewriter.rewriteOutputs(recipes, HOLDER_VIEW, resolveMain);

        assertEquals(2, rewritten, "both outputs should have been rewritten");
        assertEquals(2, recipes.size(), "rewriting must never add or remove recipes");
        assertSame(canonicalA, recipes.get("c1").items.get(0));
        assertSame(canonicalB, recipes.get("c1").items.get(1));
        assertSame(canonicalB, recipes.get("c2").items.get(0));
    }

    @Test
    void genericCoreListRemapsOutputsInPlacePreservingCount() {
        final Item itemA = new Item();
        final Item itemB = new Item();
        final ItemStack outA = new ItemStack(itemA, 2, 1);
        final ItemStack outB = new ItemStack(itemB, 2, 1);

        // IE-backed list (entry identity is opaque to the core; swap happens by index, not removal).
        final List<Holder> recipes = new ArrayList<>();
        recipes.add(new Holder(new ArrayList<>(Arrays.asList(outA))));
        recipes.add(new Holder(new ArrayList<>(Arrays.asList(outB))));
        recipes.add(null); // defensive; will be skipped, never replaced

        final ItemStack canonicalA = new ItemStack(itemA, 9, 3);
        final ItemStack canonicalB = new ItemStack(itemB, 9, 3);
        final UnaryOperator<ItemStack> resolveMain = s -> {
            if (s == outA) return canonicalA;
            if (s == outB) return canonicalB;
            return s;
        };

        final int rewritten = OutputRewriter.rewriteList(recipes, HOLDER_VIEW, resolveMain);

        assertEquals(2, rewritten, "both present outputs should have been rewritten");
        assertEquals(3, recipes.size(), "rewriting a list must never change its size (no remove)");
        assertSame(canonicalA, recipes.get(0).items.get(0));
        assertSame(canonicalB, recipes.get(1).items.get(0));
        assertNull(recipes.get(2), "null list entry is skipped and left untouched");
    }

    @Test
    void rewriteListLeavesUnchangedEntriesAndTheirIdentityAlone() {
        final Item itemA = new Item();
        final ItemStack unchanged = new ItemStack(itemA, 1, 1);

        final Holder entry = new Holder(new ArrayList<>(Arrays.asList(unchanged)));
        final List<Holder> recipes = new ArrayList<>(Arrays.asList(entry));

        final int rewritten = OutputRewriter.rewriteList(recipes, HOLDER_VIEW, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(1, recipes.size());
        assertSame(entry, recipes.get(0), "an unchanged entry must not be replaced (identity preserved)");
    }

    @Test
    void genericCoreLeavesUnchangedAndNullOutputsAlone() {
        final Item itemA = new Item();
        final ItemStack stack = new ItemStack(itemA, 2, 1);

        final Map<String, Holder> recipes = new HashMap<>();
        recipes.put("k1", new Holder(new ArrayList<>(Arrays.asList(stack))));
        recipes.put("k2", null);

        final int rewritten = OutputRewriter.rewriteOutputs(recipes, HOLDER_VIEW, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(2, recipes.size());
        assertSame(stack, recipes.get("k1").items.get(0));
        assertNull(recipes.get("k2"));
    }

    @Test
    void rewriteSingleOutputsRemapsOnlyChangedValues() {
        final Item itemA = new Item();
        final ItemStack unchanged = new ItemStack(itemA, 1, 1);
        final ItemStack changed = new ItemStack(itemA, 1, 2);
        final ItemStack canonical = new ItemStack(itemA, 9, 9);

        final Map<String, ItemStack> recipes = new HashMap<>();
        recipes.put("u", unchanged);
        recipes.put("c", changed);

        final UnaryOperator<ItemStack> resolveMain = s -> (s == changed) ? canonical : s;

        final int rewritten = OutputRewriter.rewriteSingleOutputs(recipes, resolveMain);

        assertEquals(1, rewritten);
        assertEquals(2, recipes.size());
        assertSame(unchanged, recipes.get("u"));
        assertSame(canonical, recipes.get("c"));
    }

    @Test
    void rewriteSingleOutputsCountsOnlyDistinctChanges() {
        final Item itemA = new Item();
        final ItemStack s1 = new ItemStack(itemA, 1, 1);
        final ItemStack s2 = new ItemStack(itemA, 1, 2);
        final ItemStack canonical = new ItemStack(itemA, 9, 9);

        final Map<String, ItemStack> recipes = new HashMap<>();
        recipes.put("a", s1);
        recipes.put("b", s2);

        final UnaryOperator<ItemStack> resolveMain = s -> (s == s2) ? canonical : s;

        assertEquals(1, OutputRewriter.rewriteSingleOutputs(recipes, resolveMain));
        assertSame(s1, recipes.get("a"));
        assertSame(canonical, recipes.get("b"));
    }
}
