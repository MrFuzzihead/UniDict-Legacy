package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

/**
 * T2 test for the defensive owner lookup {@link Util#getModName} (docs/CODE_REVIEW.md BUG-7). Uses MC
 * <em>types</em> only (an unregistered {@link Item} whose registry name is {@code null}) — no game
 * bootstrap, no registry mutation, which is exactly the edge case that used to throw.
 */
class UtilTest {

    @Test
    void nullStackReportsUnknownInsteadOfThrowing() {
        assertEquals(Util.UNKNOWN_MOD, Util.getModName(null));
    }

    @Test
    void unregisteredItemReportsUnknownInsteadOfThrowing() {
        // getNameForObject(new Item()) is null in this environment: the owner is unknown, not a crash.
        assertEquals(Util.UNKNOWN_MOD, Util.getModName(new ItemStack(new Item())));
    }
}
