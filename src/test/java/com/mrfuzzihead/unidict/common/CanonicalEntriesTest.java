package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

class CanonicalEntriesTest {

    @Test
    void matchesByItemAndDamageNotByStackIdentity() {
        final Item item = new Item();
        final ItemStack canonical = new ItemStack(item, 1, 0);
        final ItemStack sameVariant = new ItemStack(item, 64, 0); // different size/instance, same item+damage
        final ItemStack otherDamage = new ItemStack(item, 1, 1);

        assertTrue(CanonicalEntries.contains(Collections.singletonList(canonical), sameVariant));
        assertFalse(CanonicalEntries.contains(Collections.singletonList(canonical), otherDamage));
    }

    @Test
    void nullAndEmptyInputsAreSafe() {
        final ItemStack stack = new ItemStack(new Item(), 1, 0);
        assertFalse(CanonicalEntries.contains(null, stack));
        assertFalse(CanonicalEntries.contains(Collections.emptyList(), stack));
        assertFalse(CanonicalEntries.contains(Arrays.asList((ItemStack) null), stack));
        assertFalse(CanonicalEntries.contains(Collections.singletonList(stack), null));
    }
}
