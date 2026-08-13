package com.mrfuzzihead.unidict.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T1 tests for the pure kind taxonomy and child mechanics of {@link Resource} (M1). Zero
 * {@code net.minecraft*} imports; {@code FakeResourceContainer} stands in for the MC-bound
 * {@code UniResourceContainer} (M4).
 */
class ResourceTest {

    @BeforeEach
    @AfterEach
    void resetKindRegistry() {
        Resource.clearKinds();
    }

    /** Builds a resource whose single child per kind shares its terminal name, so addChild accepts it. */
    private static Resource<FakeResourceContainer> resourceWith(final String name, final long... kinds) {
        final Resource<FakeResourceContainer> resource = new Resource<>(name);
        for (final long kind : kinds) assertTrue(resource.addChild(new FakeResourceContainer(name, kind)));
        return resource;
    }

    @Test
    void registerAssignsDistinctPowerOfTwoKinds() {
        Resource.register("ORE");
        Resource.register("INGOT");
        Resource.register("DUST");
        final long ore = Resource.getKindOfName("ORE");
        final long ingot = Resource.getKindOfName("INGOT");
        final long dust = Resource.getKindOfName("DUST");
        assertEquals(0, ore & ingot);
        assertEquals(0, ore & dust);
        assertEquals(0, ingot & dust);
        assertEquals(1, Long.bitCount(ore));
        assertEquals(1, Long.bitCount(ingot));
        assertEquals(1, Long.bitCount(dust));
        assertTrue(Resource.kindExists("ORE", "INGOT", "DUST"));
        assertFalse(Resource.kindExists("ORE", "GhostKind"));
    }

    @Test
    void registerIsIdempotent() {
        Resource.register("ORE");
        final long first = Resource.getKindOfName("ORE");
        Resource.register("ORE");
        assertEquals(first, Resource.getKindOfName("ORE"));
        assertEquals(first, Resource.registerAndGet("ORE"));
        assertEquals(
            1,
            Resource.getKinds()
                .size());
    }

    @Test
    void registerAndGetReturnsDistinctBitsForNewKinds() {
        final long a = Resource.registerAndGet("A");
        final long b = Resource.registerAndGet("B");
        assertNotEquals(a, b);
        assertEquals(a, Resource.getKindOfName("A"));
        assertEquals(b, Resource.getKindOfName("B"));
    }

    @Test
    void kindNameAndBitRoundTrip() {
        final long ore = Resource.registerAndGet("ORE");
        assertEquals("ORE", Resource.getNameOfKind(ore));
        assertEquals(ore, Resource.getKindOfName("ORE"));
    }

    @Test
    void sixtyFourKindLimitIsEnforced() {
        for (int i = 0; i < Resource.MAX_KINDS; i++) Resource.register("kind" + i);
        assertEquals(
            Resource.MAX_KINDS,
            Resource.getKinds()
                .size());
        assertTrue(Resource.kindExists("kind0", "kind" + (Resource.MAX_KINDS - 1)));
        assertThrows(IllegalStateException.class, () -> Resource.register("kind" + Resource.MAX_KINDS));
    }

    @Test
    void addChildEnforcesNameSuffixAndUniqueKind() {
        final Resource<FakeResourceContainer> resource = new Resource<>("Iron");
        final FakeResourceContainer iron = new FakeResourceContainer("ingotIron", Resource.registerAndGet("INGOT"));
        final FakeResourceContainer gold = new FakeResourceContainer("dustIron", Resource.registerAndGet("DUST"));
        final FakeResourceContainer oreCopper = new FakeResourceContainer("oreCopper", Resource.registerAndGet("ORE"));

        assertTrue(resource.addChild(iron));
        assertFalse(resource.addChild(oreCopper)); // "oreCopper" does not end with "Iron"
        assertFalse(resource.addChild(iron)); // kind already present
        assertEquals(iron.getKind(), resource.getChildren());
        assertEquals(iron, resource.getChild(iron.getKind()));
        assertEquals(iron, resource.getChild("INGOT")); // getChild(String) takes a kind name

        assertTrue(resource.addChild(gold)); // "dustIron" ends with "Iron" and DUST kind is free
        assertEquals(iron.getKind() | gold.getKind(), resource.getChildren());
        assertEquals(gold, resource.getChild("DUST")); // by kind name
    }

    @Test
    void filteredCloneKeepsOnlyRequestedKinds() {
        final long ore = Resource.registerAndGet("ORE");
        final long ingot = Resource.registerAndGet("INGOT");
        final long dust = Resource.registerAndGet("DUST");
        final Resource<FakeResourceContainer> resource = resourceWith("copper", ore, ingot, dust);

        final Resource<FakeResourceContainer> clone = resource.filteredClone(ore | dust);
        assertEquals(ore | dust, clone.getChildren());
        assertEquals(resource.getChild(ore), clone.getChild(ore));
        assertEquals(resource.getChild(dust), clone.getChild(dust));
        assertNull(clone.getChild(ingot));
        // original is untouched
        assertEquals(ore | ingot | dust, resource.getChildren());
    }

    @Test
    void updateEntriesPrunesDroppedChildren() {
        final long ore = Resource.registerAndGet("ORE");
        final long ingot = Resource.registerAndGet("INGOT");
        final FakeResourceContainer oreChild = new FakeResourceContainer("oreCopper", ore);
        final FakeResourceContainer ingotChild = new FakeResourceContainer("ingotCopper", ingot);
        final Resource<FakeResourceContainer> resource = new Resource<>("Copper");
        assertTrue(resource.addChild(oreChild));
        assertTrue(resource.addChild(ingotChild));

        ingotChild.setDropOnUpdate(true);
        resource.updateEntries();
        assertEquals(ore, resource.getChildren());
        assertEquals(oreChild, resource.getChild(ore));
        assertNull(resource.getChild(ingot));
    }

    @Test
    void getResourcesAndFiltersByAllRequestedKinds() {
        final long a = Resource.registerAndGet("A");
        final long b = Resource.registerAndGet("B");
        final long c = Resource.registerAndGet("C");
        final Resource<FakeResourceContainer> r1 = resourceWith("thing", a, b);
        final Resource<FakeResourceContainer> r2 = resourceWith("other", b, c);
        final List<Resource<?>> all = Arrays.<Resource<?>>asList(r1, r2);

        assertEquals(
            1,
            Resource.getResources(all, a, b)
                .size()); // only r1
        assertEquals(
            1,
            Resource.getResources(all, a)
                .size()); // r1
        assertEquals(
            1,
            Resource.getResources(all, c)
                .size()); // r2
        assertEquals(
            2,
            Resource.getResources(all, b)
                .size()); // r1 + r2
        assertTrue(
            Resource.getResources(all, a, b, c)
                .isEmpty()); // none has all three
        assertTrue(
            Resource.getResources(all, "GhostKind")
                .isEmpty());
        assertTrue(
            Resource.getResources(all, 0L)
                .isEmpty());
    }

    @Test
    void toStringFormatsEmptyAndPopulated() {
        final Resource<FakeResourceContainer> empty = new Resource<>("emptyThing");
        assertEquals("emptyThing = {}", empty.toString());

        Resource.register("ORE");
        Resource.register("INGOT");
        final Resource<FakeResourceContainer> populated = new Resource<>("ingotIron");
        assertTrue(populated.addChild(new FakeResourceContainer("ingotIron", Resource.getKindOfName("ORE"))));
        assertTrue(populated.addChild(new FakeResourceContainer("ingotIron", Resource.getKindOfName("INGOT"))));
        final String value = populated.toString();
        assertTrue(value.startsWith("ingotIron = {"), value);
        assertTrue(value.endsWith("}"), value);
        assertTrue(value.contains("ORE"), value);
        assertTrue(value.contains("INGOT"), value);
    }
}
