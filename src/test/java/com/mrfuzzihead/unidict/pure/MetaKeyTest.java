package com.mrfuzzihead.unidict.pure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for the pure {@link MetaKey} item-key packing (M1). Pure arithmetic only — no
 * {@code net.minecraft*} imports here.
 */
class MetaKeyTest {

    @Test
    void wildcardPacksToJustTheId() {
        assertEquals(5, MetaKey.forItemAndDamage(5, MetaKey.WILDCARD));
        assertTrue(MetaKey.isValid(5));
    }

    @Test
    void invalidIdPacksToZero() {
        assertEquals(0, MetaKey.forItemAndDamage(0, 3));
        assertEquals(0, MetaKey.forItemAndDamage(-1, 3));
        assertEquals(0, MetaKey.forItem(-5));
        assertFalse(MetaKey.isValid(0));
    }

    @Test
    void itemAndDamageRoundTrips() {
        final int key = MetaKey.forItemAndDamage(42, 7);
        assertTrue(MetaKey.isValid(key));
        assertEquals(42, MetaKey.itemIdOf(key));
        assertEquals(8, MetaKey.metaOf(key)); // damage + 1
        assertFalse(MetaKey.isItemLevel(key));
    }

    @Test
    void damageZeroMatchesItemLevelPacking() {
        // damage 0 packs as id | (0+1)<<16 == id | ITEM_FLAG, i.e. the same as forItem.
        assertEquals(MetaKey.forItem(7), MetaKey.forItemAndDamage(7, 0));
        assertTrue(MetaKey.isItemLevel(MetaKey.forItem(7)));
    }

    @Test
    void itemPackingSetsFlagAndKeepsIdInLowBits() {
        final int key = MetaKey.forItem(9);
        assertTrue(MetaKey.isValid(key));
        assertTrue(MetaKey.isItemLevel(key));
        assertEquals(9, MetaKey.itemIdOf(key));
    }
}
