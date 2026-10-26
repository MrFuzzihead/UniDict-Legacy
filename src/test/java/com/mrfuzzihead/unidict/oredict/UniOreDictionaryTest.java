package com.mrfuzzihead.unidict.oredict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

/**
 * T2 test for the read-only {@link UniOreDictionary}. Every map is served by a
 * {@link FakeOreDictionaryAccessor} (no Minecraft statics) and the {@link ItemStack}s are opaque handles
 * (a bare {@link Item}), so no registry / GameData is touched (docs/TestPlan.md rules 2 and 6).
 *
 * <p>
 * Covers the retained minimal surface — id/name lookups, processed vs unprocessed lists, bounds, and the
 * name snapshot (docs/CODE_REVIEW.md BUG-19 removed the unused name-hashing helpers).
 */
class UniOreDictionaryTest {

    private static List<ItemStack> list(final ItemStack... stacks) {
        return new ArrayList<>(Arrays.asList(stacks));
    }

    private static ItemStack stack() {
        return new ItemStack(new Item(), 1, 0);
    }

    /** Builds a fake accessor from a simple sequential registry of ore entries. */
    private static final class Fixture {

        final Map<String, Integer> nameToId = new LinkedHashMap<>();
        final List<String> idToName = new ArrayList<>();
        final List<List<ItemStack>> idToStack = new ArrayList<>();
        final List<List<ItemStack>> idToStackUn = new ArrayList<>();
        final Map<Integer, List<Integer>> stackToId = new LinkedHashMap<>();

        int ore(final String name, final List<ItemStack> stacks, final List<ItemStack> stacksUn) {
            final int id = idToName.size();
            nameToId.put(name, id);
            idToName.add(name);
            idToStack.add(stacks);
            idToStackUn.add(stacksUn);
            return id;
        }
    }

    private UniOreDictionary u(final Fixture f) {
        return new UniOreDictionary(
            new FakeOreDictionaryAccessor(f.nameToId, f.idToName, f.idToStack, f.idToStackUn, f.stackToId));
    }

    @Test
    void getAndGetUnRespectIdBounds() {
        final Fixture f = new Fixture();
        f.ore("ingotIron", list(stack()), list(stack()));
        f.ore("ingotGold", list(stack()), list(stack()));
        f.ore("oreIron", list(stack()), list(stack()));
        final UniOreDictionary u = u(f); // ids 0..2, so size = 3

        assertSame(f.idToStack.get(0), u.get("ingotIron"));
        assertSame(f.idToStack.get(0), u.get(0));
        assertSame(f.idToStack.get(2), u.get(2));
        assertNull(u.get(3)); // == size -> out of bounds
        assertNull(u.get(-1));
        assertNull(u.get((Integer) null));
        assertNull(u.get("nonexistent"));

        assertSame(f.idToStackUn.get(1), u.getUn("ingotGold"));
        assertSame(f.idToStackUn.get(2), u.getUn(2));
        assertNull(u.getUn("nonexistent"));

        assertEquals(0, (int) u.getId("ingotIron"));
        assertEquals(2, (int) u.getId("oreIron"));
        assertNull(u.getId("nonexistent"));
    }

    @Test
    void getOreNamesReturnsAnOrderedDefensiveCopy() {
        final Fixture f = new Fixture();
        f.ore("ingotIron", list(), list());
        f.ore("ingotGold", list(), list());
        f.ore("oreIron", list(), list());
        final UniOreDictionary u = u(f);

        final List<String> names = u.getOreNames();
        assertEquals(Arrays.asList("ingotIron", "ingotGold", "oreIron"), names);
        names.clear();
        assertEquals(
            3,
            u.getOreNames()
                .size(),
            "callers cannot clear the live dictionary view");
        assertNotSame(u.getOreNames(), u.getOreNames());
    }

    @Test
    void emptyDictionaryIsHandled() {
        final UniOreDictionary u = u(new Fixture());

        assertTrue(
            u.getOreNames()
                .isEmpty());
        assertNull(u.get(0));
        assertNull(u.get("ingotIron"));
        assertNull(u.getId("ingotIron"));
    }
}
