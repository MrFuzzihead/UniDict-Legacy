package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.chest.FakeChestGenHooksAccessor;
import com.mrfuzzihead.unidict.chest.FakeWeightedRandomChestContent;

/**
 * T2 test for the M6 Chest / loot rewrite (docs/PLAN.md §M6 #5). Fakes drive
 * {@link ChestIntegration#rewriteContents} and {@link ChestIntegration#rewriteCategory} through their
 * resolver seams — asserting the BB-3 non-destructive guarantee: only each loot entry's item reference
 * changes, never an entry is added or removed, and unresolved entries are left alone.
 *
 * <p>
 * The {@link Item}/{@link ItemStack} stand-ins and {@code WeightedRandomChestContent} subclass touch MC
 * <em>types</em> but no MC <em>statics</em>; the real Forge {@code ChestGenHooks} static registry is
 * exercised in-game (T3).
 */
class ChestIntegrationTest {

    @Test
    void rewriteContentsRemapsItemsNonDestructively() {
        final Item itemA = new Item();
        final ItemStack lootA = new ItemStack(itemA, 1, 1);
        final ItemStack lootB = new ItemStack(itemA, 1, 2);
        final FakeWeightedRandomChestContent a = new FakeWeightedRandomChestContent(lootA);
        final FakeWeightedRandomChestContent b = new FakeWeightedRandomChestContent(lootB);
        final List<WeightedRandomChestContent> contents = new ArrayList<>();
        contents.add(a);
        contents.add(b);

        final ItemStack canonicalA = new ItemStack(itemA, 9, 3);
        final ItemStack canonicalB = new ItemStack(itemA, 9, 4);
        final UnaryOperator<ItemStack> resolve = s -> {
            if (s == lootA) return canonicalA;
            if (s == lootB) return canonicalB;
            return s;
        };

        final int rewritten = ChestIntegration.rewriteContents(contents, resolve);

        assertEquals(2, rewritten, "both loot entries should have been rewritten");
        assertEquals(2, contents.size(), "rewriting must never add or remove loot entries");
        assertSame(canonicalA, a.getTheItemId());
        assertSame(canonicalB, b.getTheItemId());
    }

    @Test
    void rewriteContentsLeavesUnresolvedEntriesAlone() {
        final Item itemA = new Item();
        final ItemStack loot = new ItemStack(itemA, 1, 1);
        final FakeWeightedRandomChestContent content = new FakeWeightedRandomChestContent(loot);
        final List<WeightedRandomChestContent> contents = new ArrayList<>();
        contents.add(content);

        // Identity resolver: every item "resolves" to itself -> nothing changes.
        final int rewritten = ChestIntegration.rewriteContents(contents, UnaryOperator.identity());

        assertEquals(0, rewritten);
        assertEquals(1, contents.size());
        assertSame(loot, content.getTheItemId(), "unresolved entry must stay identical");
    }

    @Test
    void rewriteCategoryDelegatesToContentsSeam() {
        final Item itemA = new Item();
        final ItemStack lootA = new ItemStack(itemA, 1, 1);
        final ItemStack lootB = new ItemStack(itemA, 1, 2);
        final FakeWeightedRandomChestContent a = new FakeWeightedRandomChestContent(lootA);
        final FakeWeightedRandomChestContent b = new FakeWeightedRandomChestContent(lootB);
        final List<WeightedRandomChestContent> contents = new ArrayList<>();
        contents.add(a);
        contents.add(b);

        final ItemStack canonicalB = new ItemStack(itemA, 9, 7);
        final FakeChestGenHooksAccessor category = new FakeChestGenHooksAccessor(contents);

        final int rewritten = ChestIntegration.rewriteCategory(category, s -> (s == lootB) ? canonicalB : s);

        assertEquals(1, rewritten, "only the resolvable entry is rewritten");
        assertSame(lootA, a.getTheItemId());
        assertSame(canonicalB, b.getTheItemId());
    }
}
