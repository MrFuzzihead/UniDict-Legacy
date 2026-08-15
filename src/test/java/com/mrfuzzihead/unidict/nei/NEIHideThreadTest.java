package com.mrfuzzihead.unidict.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

/**
 * T2 test for the NEI hide-decision seam {@link NEIHideThread#stacksToHide} (TODO.md P0 #1 "hide-set
 * builder fed by fakes"). Drives the pure {@code SelectionRules.hiddenIndices} decision with plain
 * {@link ItemStack} fakes (no registry / no MC statics), asserting {@code autoHideInNEI} with both
 * exemptions: the per-kind {@code hideInNEIBlackSet} and the per-mod {@code keepOneEntryModBlackSet}.
 */
class NEIHideThreadTest {

    /** @return {@code count} distinct fake stacks, ordered by selection priority (index 0 is main). */
    private static List<ItemStack> stacks(final int count) {
        final Item item = new Item();
        final List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(new ItemStack(item, 1, i));
        return list;
    }

    @Test
    void autoHideHidesNonMainWhenKindNotBlacklisted() {
        final List<ItemStack> entries = stacks(4);
        // autoHide on, kind 2L not blacklisted (only 1L is), no mod blacklist -> hide every non-main.
        final List<ItemStack> hidden = NEIHideThread
            .stacksToHide(entries, 2L, true, new LinkedHashSet<>(Arrays.asList(1L)), s -> false);
        assertEquals(Arrays.asList(entries.get(1), entries.get(2), entries.get(3)), hidden);
    }

    @Test
    void autoHideDisabledOrBlacklistedKindHidesNothing() {
        final List<ItemStack> entries = stacks(4);
        // autoHide off -> nothing hidden.
        assertEquals(
            Arrays.asList(),
            NEIHideThread.stacksToHide(entries, 2L, false, new LinkedHashSet<>(), s -> false));
        // autoHide on but the container's kind IS blacklisted -> nothing hidden for it.
        assertEquals(
            Arrays.asList(),
            NEIHideThread.stacksToHide(entries, 1L, true, new LinkedHashSet<>(Arrays.asList(1L)), s -> false));
    }

    @Test
    void autoHideKeepsCanonicalAndDropsEverythingElse() {
        final List<ItemStack> entries = stacks(4);
        // Only the main (index 0) survives; kind blacklist empty -> hide all the rest.
        assertEquals(
            Arrays.asList(entries.get(1), entries.get(2), entries.get(3)),
            NEIHideThread.stacksToHide(entries, 2L, true, new LinkedHashSet<>(), s -> false));
    }

    @Test
    void modBlacklistExemptsASpecificEntryFromHiding() {
        final List<ItemStack> entries = stacks(4);
        final ItemStack survivor = entries.get(3);
        // The mod-blacklisted entry (index 3) stays visible; the others are collapsed.
        assertEquals(
            Arrays.asList(entries.get(1), entries.get(2)),
            NEIHideThread.stacksToHide(entries, 2L, true, new LinkedHashSet<>(), s -> s == survivor));
    }

    @Test
    void emptyEntryListHidesNothing() {
        assertEquals(
            Arrays.asList(),
            NEIHideThread.stacksToHide(stacks(0), 1L, true, new LinkedHashSet<>(), s -> false));
    }
}
