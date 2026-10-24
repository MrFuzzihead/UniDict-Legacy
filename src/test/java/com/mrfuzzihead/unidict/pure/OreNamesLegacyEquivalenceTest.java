package com.mrfuzzihead.unidict.pure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Differential test for the OPT-5 refactor: {@link OreNames} must classify Ore Dictionary names
 * <b>identically</b> to the regex pipeline it replaced in {@code UniResourceHandler#createResources}.
 *
 * <p>
 * The old algorithm is reproduced here verbatim as a <b>test oracle</b> (two regex passes with the giant
 * alternation {@code (Copper|Iron|…)$}), so a behavioural regression in the pure helper shows up as a map
 * mismatch without needing an in-game run. Only the algorithm is compared; the excluded-kind constant is
 * shared so the test stays focused.
 *
 * <p>
 * One intentional difference is covered separately: with <em>no</em> ingot-prefixed names the old code's
 * empty alternation {@code ()} matched every name and produced an unused {@code ""} bucket; the new helper
 * returns an empty model, which is what the pipeline actually consumes.
 */
class OreNamesLegacyEquivalenceTest {

    /** The exact classification the old {@code UniResourceHandler#createResources} performed. */
    private static Map<String, Set<String>> legacyClassify(final List<String> oreNames) {
        final List<String> resourceNames = new ArrayList<>();
        final Pattern excludedResource = Pattern.compile(".*(?i)(Dense|Nether|Dye|Glass|Tiny|Small).*");
        final Pattern ingot = Pattern.compile("^ingot");
        for (final String name : oreNames) {
            if (name == null) continue;
            final Matcher matcher = ingot.matcher(name);
            if (!matcher.find()) continue;
            final String stripped = matcher.replaceFirst("");
            if (excludedResource.matcher(stripped)
                .find()) continue;
            resourceNames.add(capitalize(stripped));
        }

        final StringBuilder patternBuilder = new StringBuilder("(");
        for (int i = 0; i < resourceNames.size(); i++) {
            patternBuilder.append(resourceNames.get(i));
            patternBuilder.append(i + 1 < resourceNames.size() ? "|" : ")$");
        }
        final Pattern resourceSuffix = Pattern.compile(patternBuilder.toString());

        final Map<String, Set<String>> grouped = new LinkedHashMap<>();
        for (final String name : oreNames) {
            if (name == null) continue;
            final Matcher matcher = resourceSuffix.matcher(name);
            if (!matcher.find()) continue;
            final String kindName = matcher.replaceFirst("");
            if (OreNames.EXCLUDED_KINDS.contains(kindName)) continue;
            final String resourceName = matcher.group();
            grouped.computeIfAbsent(resourceName, k -> new LinkedHashSet<>())
                .add(kindName);
        }
        return grouped;
    }

    private static String capitalize(final String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A realistic mixed dictionary: normal kinds, overlapping metal names, exclusions, junk and nulls. */
    private static List<String> fixture() {
        final List<String> names = new ArrayList<>(
            Arrays.asList(
                // normal families
                "ingotCopper",
                "oreCopper",
                "dustCopper",
                "dustTinyCopper",
                "nuggetCopper",
                "blockCopper",
                "plateCopper",
                "gearCopper",
                "ingotIron",
                "oreIron",
                "dustIron",
                "ingotGold",
                "nuggetGold",
                "blockGold",
                "ingotTin",
                "oreTin",
                "dustTin",
                "ingotSilver",
                "ingotLead",
                "ingotSteel",
                "blockSteel",
                // overlapping resource names (longest suffix must win)
                "ingotMeteoricIron",
                "oreMeteoricIron",
                "dustMeteoricIron",
                "ingotArdite",
                "ingotCobalt",
                // two spellings of aluminium
                "ingotAluminum",
                "ingotAluminium",
                // excluded resources (Tiny/Dense/…)
                "ingotDenseCopper",
                "ingotTinyIron",
                "ingotSmallGold",
                "ingotNetherTin",
                "ingotDyeSilver",
                "ingotGlassLead",
                // excluded kinds
                "gemCopper",
                "orePoorCopper",
                "stickCopper",
                "foodCopper",
                // name that is exactly a resource, and a case-mismatched one
                "Copper",
                "ingotcopper",
                // non-metal / unrelated names
                "blockStone",
                "logWood",
                "dustSulfur",
                "machineBlock",
                // junk
                ""));
        names.add(null);
        return names;
    }

    @Test
    void newClassifierMatchesTheLegacyRegexPipelineExactly() {
        final List<String> oreNames = fixture();

        final List<String> newResources = OreNames.metalResourceNames(oreNames);
        final Map<String, Set<String>> newGrouped = OreNames
            .kindsByResource(oreNames, newResources, OreNames.EXCLUDED_KINDS);
        final Map<String, Set<String>> legacyGrouped = legacyClassify(oreNames);

        assertEquals(legacyGrouped, newGrouped, "classified resources/kinds must be identical to the old regex");

        // Spot-check the cases that make the equivalence meaningful.
        // Longest suffix: MeteoricIron owns its names, Iron does not absorb them.
        assertEquals(new LinkedHashSet<>(Arrays.asList("ingot", "ore", "dust")), newGrouped.get("MeteoricIron"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("ingot", "ore", "dust", "ingotTiny")), newGrouped.get("Iron"));
        // The excluded-resource filter only affects *discovery*; such names still group under an existing
        // resource exactly like the old regex did ("ingotDenseCopper" -> Copper/ingotDense).
        assertTrue(
            newGrouped.get("Copper")
                .contains("ingotDense"));
        assertTrue(
            newGrouped.get("Copper")
                .contains("dustTiny"));
        assertTrue(
            newGrouped.get("Copper")
                .contains("gear"));
        // The exact "Copper" name (equal to the resource, no prefix) groups with an empty kind, as before.
        assertTrue(
            newGrouped.get("Copper")
                .contains(""));
        // "ingotcopper" (case mismatch) is discovered but never grouped; "ingotDenseCopper" and friends are.
        assertFalse(
            newGrouped.get("Copper")
                .contains("ingotcopper"));
    }

    @Test
    void aBareIngotNameOnlyCreatedAnUnusedLegacyBucket() {
        // A lone "ingot" OD name stripped to "", and the old pipeline happily registered "" as a resource.
        // The empty alternation then matched every name into that bucket with kind = the whole name. It was
        // never consumed (metalsToUnify cannot contain ""), so the clean "skip empty suffixes" rule is
        // equivalent — but it is deliberately NOT part of the differential fixture above.
        final List<String> names = Arrays.asList("ingot", "blockStone", "oreCopper");

        assertTrue(
            OreNames.metalResourceNames(names)
                .isEmpty());
        assertTrue(
            OreNames.kindsByResource(names, OreNames.metalResourceNames(names), OreNames.EXCLUDED_KINDS)
                .isEmpty());
    }

    @Test
    void noIngotNamesYieldsAnEmptyModelUnlikeTheLegacyEmptyAlternation() {
        final List<String> oreNames = Arrays.asList("oreCopper", "dustCopper", "blockStone");

        // The old code compiled "()" here and matched every name into an unused "" bucket; the pipeline
        // never consumed it (metalsToUnify cannot contain ""), so the clean empty model is equivalent.
        assertTrue(
            OreNames.metalResourceNames(oreNames)
                .isEmpty());
        assertTrue(
            OreNames.kindsByResource(oreNames, OreNames.metalResourceNames(oreNames), OreNames.EXCLUDED_KINDS)
                .isEmpty());
    }

    @Test
    void duplicateDiscoveryKeepsLegacyGrouping() {
        // Duplicate resource names in the legacy alternation were harmless; the grouped result must not care.
        final List<String> oreNames = Arrays.asList("ingotCopper", "oreCopper", "ingotCopper");
        assertEquals(
            legacyClassify(oreNames),
            OreNames.kindsByResource(oreNames, OreNames.metalResourceNames(oreNames), OreNames.EXCLUDED_KINDS));
    }
}
