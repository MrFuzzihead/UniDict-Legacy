package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

class CanonicalIndexTest {

    @Test
    void matchesByItemAndDamageNotByStackIdentity() {
        final Item item = new Item();
        final CanonicalIndex index = new CanonicalIndex();
        index.add(new ItemStack(item, 1, 0));

        assertTrue(index.contains(new ItemStack(item, 64, 0))); // different instance/size, same item+damage
        assertFalse(index.contains(new ItemStack(item, 1, 1)));
        assertFalse(index.contains(new ItemStack(new Item(), 1, 0)));
    }

    @Test
    void directItemDamageAddMatchesTheStackOverloadWithoutAllocating() {
        final Item item = new Item();
        final CanonicalIndex index = new CanonicalIndex();
        index.add(item, 7);

        assertTrue(index.contains(new ItemStack(item, 1, 7)));
        assertFalse(index.contains(new ItemStack(item, 1, 0)));
    }

    @Test
    void nullAndEmptyInputsAreSafe() {
        final CanonicalIndex index = new CanonicalIndex();
        assertFalse(index.contains(null));
        index.add(null);
        assertFalse(index.contains(new ItemStack(new Item(), 1, 0)));
        index.clear();
        assertFalse(index.contains(null));
    }
}
