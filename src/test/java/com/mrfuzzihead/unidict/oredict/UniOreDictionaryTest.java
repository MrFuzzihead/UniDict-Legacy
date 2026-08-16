package com.mrfuzzihead.unidict.oredict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

/**
 * M3 T2 test for the read-only {@link UniOreDictionary}. Every map is served by a
 * {@link FakeOreDictionaryAccessor} (no Minecraft statics); the {@link ItemStack}s are opaque handles
 * (constructed with a bare {@link Item}) and hashed via an identity-keyed {@link MetaItemProvider}, so
 * no registry / GameData is touched. See docs/TestPlan.md rule 2 + rule 6.
 */
class UniOreDictionaryTest {

    /** Identity-keyed hash: lets a test assign a stable per-stack key with no Item introspection. */
    private final IdentityHashMap<ItemStack, Integer> hashByStack = new IdentityHashMap<>();
    private final MetaItemProvider provider = stack -> hashByStack.getOrDefault(stack, 0);

    private ItemStack stackHashingTo(final int hash) {
        final ItemStack stack = new ItemStack(new Item(), 1, 0);
        hashByStack.put(stack, hash);
        return stack;
    }

    private static List<ItemStack> list(final ItemStack... stacks) {
        return new ArrayList<>(Arrays.asList(stacks));
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
            new FakeOreDictionaryAccessor(f.nameToId, f.idToName, f.idToStack, f.idToStackUn, f.stackToId),
            provider);
    }

    @Test
    void getThoseThatMatchesFiltersNamesByRegex() {
        final Fixture f = new Fixture();
        f.ore("ingotIron", list(), list());
        f.ore("ingotGold", list(), list());
        f.ore("oreIron", list(), list());
        final UniOreDictionary u = u(f);

        assertEquals(
            2,
            u.getThoseThatMatches("ingot")
                .size());
        assertEquals(
            1,
            u.getThoseThatMatches("ore")
                .size());
        assertTrue(
            u.getThoseThatMatches("xyz")
                .isEmpty());
    }

    @Test
    void getAndGetUnRespectIdBounds() {
        final Fixture f = new Fixture();
        f.ore("ingotIron", list(stackHashingTo(1)), list(stackHashingTo(2)));
        f.ore("ingotGold", list(stackHashingTo(3)), list(stackHashingTo(4)));
        f.ore("oreIron", list(stackHashingTo(5)), list(stackHashingTo(6)));
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
    void getNameResolvesListItemStackAndOther() {
        final Fixture f = new Fixture();
        final ItemStack s1 = stackHashingTo(101);
        final ItemStack s2 = stackHashingTo(102);
        final List<ItemStack> l0Un = list(s1, s2);
        f.ore("ingotIron", list(), l0Un); // idToStack empty; idToStackUn = [s1, s2]
        final UniOreDictionary u = u(f);

        assertEquals("ingotIron", u.getName(l0Un)); // entryToName keyed by the idToStackUn reference
        assertNull(u.getName(f.idToStack.get(0))); // a different (idToStack) list object -> unknown
        assertNull(u.getName("not-a-list"));
        assertEquals("ingotIron", u.getName(s1));
        assertEquals("ingotIron", u.getName(s2));
        assertNull(u.getName(stackHashingTo(999))); // not seen while building stackToName
    }

    @Test
    void firstAndLastEntryReturnCopiesAndGuardEmptyOrUnknown() {
        final Fixture f = new Fixture();
        final ItemStack first = stackHashingTo(10);
        final ItemStack last = stackHashingTo(11);
        f.ore("ingotIron", list(first, last), list());
        f.ore("empty", list(), new ArrayList<>());
        final UniOreDictionary u = u(f);

        final ItemStack gotFirst = u.getFirstEntry("ingotIron");
        assertNotSame(first, gotFirst);
        assertSame(first.getItem(), gotFirst.getItem());
        assertEquals(first.getItemDamage(), gotFirst.getItemDamage());

        final ItemStack gotLast = u.getLastEntry("ingotIron");
        assertNotSame(last, gotLast);
        assertSame(last.getItem(), gotLast.getItem());

        assertNull(u.getFirstEntry("empty")); // empty entry list
        assertNull(u.getLastEntry("empty"));
        assertNull(u.getFirstEntry("nonexistent"));
        assertNull(u.getLastEntry("nonexistent"));
        assertNull(u.getFirstEntry("")); // empty name
    }

    @Test
    void getCollectionReturnsUnionOfEntries() {
        final Fixture f = new Fixture();
        final ItemStack a = stackHashingTo(1);
        final ItemStack d = stackHashingTo(3);
        f.ore("ingotIron", list(a), list());
        f.ore("oreIron", list(d), list());
        final UniOreDictionary u = u(f);

        final Collection<String> names = Arrays.asList("ingotIron", "oreIron");
        final Set<ItemStack> result = u.get(names);
        assertEquals(2, result.size());
        assertTrue(result.contains(a));
        assertTrue(result.contains(d));
    }

    @Test
    void getItemStackResolvesViaStackToId() {
        final Fixture f = new Fixture();
        f.ore("ingotIron", list(), list());
        f.ore("ingotGold", list(), list());
        f.ore("oreIron", list(), list()); // id 2
        final ItemStack target = stackHashingTo(555);
        f.stackToId.put(555, Arrays.asList(2));
        final UniOreDictionary u = u(f);

        assertSame(f.idToStack.get(2), u.get(target));
        assertNull(u.get(stackHashingTo(777))); // unknown key -> no stackToId entry
    }
}
