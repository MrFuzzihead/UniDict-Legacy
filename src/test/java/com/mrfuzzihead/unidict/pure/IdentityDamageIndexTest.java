package com.mrfuzzihead.unidict.pure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for {@link IdentityDamageIndex} — the fix for the drop-time cross-wiring bug
 * (docs/TODO.md "UnifyDrops swaps drops to unrelated ores").
 *
 * <p>
 * The regression these tests pin is not arithmetic, it is <em>key lifetime</em>: the resource index is
 * built once at postInit and read for the rest of the session, while Forge renumbers item registry ids
 * out from under it on {@code FMLModIdMappingEvent}. The old numeric key therefore resolved item A to
 * item B's entry (A's post-remap id equals B's load-time id), which is how an Amethyst Shard drop became
 * an aluminum ore block. Everything here uses plain {@link Object} keys, so the invariant is testable
 * with zero {@code net.minecraft*} imports (docs/TestPlan.md rule 2).
 */
class IdentityDamageIndexTest {

    @Test
    void lookupSurvivesTheKeyBeingRenumberedElsewhere() {
        // Two distinct objects stand in for two different Items. The old key was
        // registryId(item) | (damage+1)<<16, so only the NUMBER mattered — after a remap, itemA's new
        // id equalled itemB's old id and the lookup silently crossed over.
        final Object itemA = new Object();
        final Object itemB = new Object();
        final String canonicalOfB = "oreAluminum";

        final IdentityDamageIndex<String> index = new IdentityDamageIndex<>();
        index.put(itemB, 0, canonicalOfB);

        // Identity, not number, decides: A never resolves to B's entry, however ids are assigned.
        assertNull(index.get(itemA, 0), "a different item must never inherit another item's entry");
        assertSame(canonicalOfB, index.get(itemB, 0));
    }

    @Test
    void sameValueObjectFromADifferentInstanceDoesNotMatch() {
        // IdentityHashMap, not HashMap: two Items with the same toString/equals must stay separate.
        final IdentityDamageIndex<String> index = new IdentityDamageIndex<>();
        index.put(new String("minecraft:gold_ore"), 0, "minecraft:gold_ore");
        assertNull(index.get(new String("minecraft:gold_ore"), 0));
    }

    @Test
    void damageIsPartOfTheKey() {
        final Object item = new Object();
        final IdentityDamageIndex<String> index = new IdentityDamageIndex<>();
        index.put(item, 3, "metaThree");

        assertEquals("metaThree", index.get(item, 3));
        assertNull(index.get(item, 0));
        assertNull(index.get(item, 4));
    }

    @Test
    void wildcardEntryMatchesAnyDamage() {
        final Object item = new Object();
        final IdentityDamageIndex<String> index = new IdentityDamageIndex<>();
        index.put(item, MetaKey.WILDCARD, "anyDamage");

        assertEquals("anyDamage", index.get(item, MetaKey.WILDCARD));
        assertEquals("anyDamage", index.get(item, 0));
        assertEquals("anyDamage", index.get(item, 15));
        assertTrue(index.contains(item, 7));
    }

    @Test
    void exactEntryWinsOverWildcard() {
        final Object item = new Object();
        final IdentityDamageIndex<String> index = new IdentityDamageIndex<>();
        index.put(item, MetaKey.WILDCARD, "wildcard");
        index.put(item, 2, "exact");

        assertEquals("exact", index.get(item, 2));
        assertEquals("wildcard", index.get(item, 5));
    }

    @Test
    void reindexingDoesNotLeaveStaleEntries() {
        final Object item = new Object();
        final IdentityDamageIndex<String> index = new IdentityDamageIndex<>();
        index.put(item, 0, "first");

        index.clear();
        index.put(item, 0, "second");

        assertEquals("second", index.get(item, 0));
        assertTrue(!index.isEmpty());
    }

    @Test
    void nullKeyOrValueIsIgnoredRatherThanThrowing() {
        final Object item = new Object();
        final IdentityDamageIndex<String> index = new IdentityDamageIndex<>();

        index.put(null, 0, "noKey");
        index.put(item, 0, null);

        assertTrue(index.isEmpty());
        assertNull(index.get(null, 0));
        assertNull(index.get(item, 0));
    }
}
