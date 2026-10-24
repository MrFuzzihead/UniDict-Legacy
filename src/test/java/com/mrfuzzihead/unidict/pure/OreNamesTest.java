package com.mrfuzzihead.unidict.pure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for {@link OreNames} — the pure Ore Dictionary name classifier extracted from
 * {@code UniResourceHandler#createResources} (docs/CODE_REVIEW.md OPT-5). These pin the taxonomy rules the
 * model is built on, which previously only the in-game pipeline exercised:
 * <ul>
 * <li>metals are discovered from <b>ingot-prefixed</b> names only, capitalised, first-seen order;</li>
 * <li>the decorative Tiny/Dense/Nether/Dye/Glass/Small names are excluded;</li>
 * <li>kind extraction is the <b>longest</b> resource suffix (so {@code ingotMeteoricIron} belongs to
 * {@code MeteoricIron}, not {@code Iron}) and is case-sensitive, exactly like the regex it replaced;</li>
 * <li>the excluded-kind set (gem, orePoor, …) is dropped while real shapes survive.</li>
 * </ul>
 * Zero {@code net.minecraft*} imports.
 */
class OreNamesTest {

    // ---- metal discovery ----------------------------------------------------------------------

    @Test
    void discoversCapitalisedIngotSuffixesInFirstSeenOrder() {
        final List<String> names = Arrays.asList("oreCopper", "ingotCopper", "dustTinyCopper", "ingotIron", "gemIron");

        assertEquals(Arrays.asList("Copper", "Iron"), OreNames.metalResourceNames(names));
    }

    @Test
    void dropsTheExcludedDecorativeNames() {
        // Historical filter: .*(?i)(Dense|Nether|Dye|Glass|Tiny|Small).*
        final List<String> names = Arrays.asList(
            "ingotDenseCopper",
            "ingotNetherIron",
            "ingotDyeGold",
            "ingotGlassTin",
            "ingotTinyLead",
            "ingotSmallSilver",
            "ingotCopper");

        assertEquals(Collections.singletonList("Copper"), OreNames.metalResourceNames(names));
    }

    @Test
    void dedupesAndSkipsBareOrNonIngotNames() {
        final List<String> names = new ArrayList<>(
            Arrays.asList("ingotCopper", "ingotaluminum", "oreCopper", "ingot", "", "tinIngot"));
        names.add(null);

        // Discovery is prefix-based ("ingot..."), so "ingotaluminum" is discovered as "Aluminum" — exactly
        // as the old `^ingot` regex did. It never gets kinds, because grouping is a case-sensitive *suffix*
        // match (see the assertion below). A bare "ingot" has an empty suffix and is skipped.
        assertEquals(Arrays.asList("Copper", "Aluminum"), OreNames.metalResourceNames(names));

        // Grouping drops the capitalisation-mismatched name entirely (old behaviour: same regex, `$`-anchored).
        final Map<String, Set<String>> grouped = OreNames.kindsByResource(
            Arrays.asList("ingotCopper", "ingotaluminum"),
            Arrays.asList("Copper", "Aluminum"),
            OreNames.EXCLUDED_KINDS);
        assertEquals(1, grouped.size());
        assertEquals(new LinkedHashSet<>(Collections.singletonList("ingot")), grouped.get("Copper"));

        // Exact duplicates are emitted once.
        assertEquals(
            Collections.singletonList("Copper"),
            OreNames.metalResourceNames(Arrays.asList("ingotCopper", "ingotCopper", "ingotCopper")));
    }

    @Test
    void dedupesCaseInsensitiveDuplicatesAfterCapitalisation() {
        // Two dictionary names can strip to the same capitalised resource; the classifier must not emit it
        // twice (the old list-based version could, harmlessly — this pins the cleaner behaviour).
        assertEquals(
            Collections.singletonList("Copper"),
            OreNames.metalResourceNames(Arrays.asList("ingotCopper", "ingotcopper")));
    }

    @Test
    void emptyAndNullInputsAreSafe() {
        assertTrue(
            OreNames.metalResourceNames(null)
                .isEmpty());
        assertTrue(
            OreNames.metalResourceNames(Collections.emptyList())
                .isEmpty());
    }

    // ---- kind grouping ------------------------------------------------------------------------

    @Test
    void groupsEveryUsableKindUnderItsResource() {
        final List<String> names = Arrays.asList(
            "ingotCopper",
            "oreCopper",
            "dustTinyCopper",
            "blockCopper",
            "plateCopper",
            "gemCopper",
            "orePoorCopper");

        final Map<String, Set<String>> grouped = OreNames
            .kindsByResource(names, Collections.singletonList("Copper"), OreNames.EXCLUDED_KINDS);

        assertEquals(1, grouped.size());
        assertEquals(
            new LinkedHashSet<>(Arrays.asList("ingot", "ore", "dustTiny", "block", "plate")),
            grouped.get("Copper"));
    }

    @Test
    void longestSuffixWinsForOverlappingResourceNames() {
        // "ingotMeteoricIron" ends with both "Iron" and "MeteoricIron"; the regex alternation resolved it
        // the same way, because the pattern is anchored and the longest candidate starts earliest.
        final List<String> resources = Arrays.asList("Iron", "MeteoricIron");
        final List<String> names = Arrays.asList("ingotMeteoricIron", "oreMeteoricIron", "ingotIron", "oreIron");

        assertEquals("MeteoricIron", OreNames.resourceOf("ingotMeteoricIron", resources));
        assertEquals("Iron", OreNames.resourceOf("ingotIron", resources));

        final Map<String, Set<String>> grouped = OreNames.kindsByResource(names, resources, OreNames.EXCLUDED_KINDS);

        assertEquals(new LinkedHashSet<>(Arrays.asList("ingot", "ore")), grouped.get("MeteoricIron"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("ingot", "ore")), grouped.get("Iron"));
        assertEquals(2, grouped.size());
    }

    @Test
    void unmatchedNamesAndEmptyInputsProduceAnEmptyModel() {
        assertTrue(
            OreNames
                .kindsByResource(
                    Arrays.asList("machineBlock", "foo", null),
                    Collections.singletonList("Copper"),
                    OreNames.EXCLUDED_KINDS)
                .isEmpty());
        assertTrue(
            OreNames.kindsByResource(null, Collections.singletonList("Copper"), OreNames.EXCLUDED_KINDS)
                .isEmpty());
        assertTrue(
            OreNames.kindsByResource(Arrays.asList("ingotCopper"), null, OreNames.EXCLUDED_KINDS)
                .isEmpty());
        assertTrue(
            OreNames
                .kindsByResource(
                    Arrays.asList("ingotCopper"),
                    Collections.singletonList("Copper"),
                    OreNames.EXCLUDED_KINDS)
                .containsKey("Copper"));
    }

    @Test
    void nullExcludedKindsDropsNothing() {
        final Map<String, Set<String>> grouped = OreNames
            .kindsByResource(Collections.singletonList("gemCopper"), Collections.singletonList("Copper"), null);

        assertEquals(new LinkedHashSet<>(Collections.singletonList("gem")), grouped.get("Copper"));
    }

    @Test
    void kindAndResourceHelpersHandleEdgeCases() {
        assertEquals("ingot", OreNames.kindOf("ingotCopper", "Copper"));
        assertEquals("dustTiny", OreNames.kindOf("dustTinyCopper", "Copper"));
        assertEquals("", OreNames.kindOf("Copper", "Copper"));
        assertEquals("", OreNames.kindOf("ingotCopper", null));

        assertNull(OreNames.resourceOf(null, Collections.singletonList("Copper")));
        assertNull(OreNames.resourceOf("ingotCopper", null));
        assertNull(OreNames.resourceOf("Copper", Collections.singletonList("MeteoricIron")));
        assertEquals("Copper", OreNames.resourceOf("Copper", Collections.singletonList("Copper")));
    }

    @Test
    void additionsReportsLiveNamesMissingFromTheSnapshotSorted() {
        // BUG-8 Phase B: the model is a POST_INIT snapshot; later registrations are invisible to it.
        final List<String> snapshot = Arrays.asList("ingotCopper", "oreCopper");

        assertEquals(
            Arrays.asList("ingotZinc", "oreZinc"),
            OreNames.additions(snapshot, Arrays.asList("oreZinc", "ingotCopper", "ingotZinc", "oreCopper")));
        assertTrue(
            OreNames.additions(snapshot, snapshot)
                .isEmpty());
        // Null live names are ignored; a null snapshot means "everything is new".
        assertEquals(Arrays.asList("ingotCopper"), OreNames.additions(null, Arrays.asList(null, "ingotCopper")));
        assertTrue(
            OreNames.additions(snapshot, null)
                .isEmpty());
        assertTrue(
            OreNames.additions(snapshot, Collections.emptyList())
                .isEmpty());
    }

    @Test
    void modelledNameCountOnlyCountsConfiguredMetalKindNames() {
        final List<String> live = Arrays
            .asList("ingotTitanium", "oreTitanium", "blockAdamantine", "gearTitanium", "gemTitanium");
        final List<String> added = live;
        final Set<String> metals = new LinkedHashSet<>(Collections.singletonList("Titanium"));
        final Set<String> kinds = new LinkedHashSet<>(Arrays.asList("ingot", "ore", "gear"));

        // ingot/ore/gear Titanium count; Adamantine is not a configured metal; gem is not a configured kind.
        assertEquals(3, OreNames.modelledNameCount(added, live, metals, kinds));
        // Config matching is case-insensitive, like the rest of the kind/metal surfaces.
        assertEquals(
            3,
            OreNames.modelledNameCount(
                added,
                live,
                Collections.singletonList("titanium"),
                Arrays.asList("INGOT", "Ore", "GEAR")));
        // Degenerate inputs.
        assertEquals(0, OreNames.modelledNameCount(added, live, Collections.emptySet(), kinds));
        assertEquals(0, OreNames.modelledNameCount(Collections.emptyList(), live, metals, kinds));
        assertEquals(0, OreNames.modelledNameCount(added, Collections.emptyList(), metals, kinds));
    }

    @Test
    void capitalisationOnlyTouchesTheFirstCharacter() {
        assertEquals("Copper", OreNames.capitalizeFirst("copper"));
        assertEquals("MeteoricIron", OreNames.capitalizeFirst("meteoricIron"));
        assertEquals("X", OreNames.capitalizeFirst("X"));
        assertNull(OreNames.capitalizeFirst(null));
        assertEquals("", OreNames.capitalizeFirst(""));
    }

    @Test
    void excludedKindSetCarriesTheHistoricalEntries() {
        for (final String kind : Arrays.asList("gem", "orePoor", "stick", "blockPrismarine", "food", "hardened"))
            assertTrue(OreNames.EXCLUDED_KINDS.contains(kind), "expected " + kind + " to stay excluded");
        for (final String kind : Arrays.asList("ingot", "ore", "dustTiny", "dust", "nugget", "block", "plate", "gear"))
            assertFalse(OreNames.EXCLUDED_KINDS.contains(kind), "expected " + kind + " to remain unifiable");
    }
}
