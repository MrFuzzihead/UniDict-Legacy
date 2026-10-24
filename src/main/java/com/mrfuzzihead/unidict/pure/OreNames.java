package com.mrfuzzihead.unidict.pure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure classification of Ore Dictionary names into the resource taxonomy: a metal resource name
 * ({@code Copper}) plus the kind prefix in front of it ({@code ingotCopper} -> kind {@code ingot}).
 *
 * <p>
 * Extracted from {@code UniResourceHandler#createResources} (docs/CODE_REVIEW.md OPT-5) for two reasons:
 * <ul>
 * <li><b>Testability</b> — the taxonomy rules (ingot-prefixed metal discovery, the Tiny/Dense/… exclusion,
 * the excluded-kind set, longest-suffix matching) are now T1-tested instead of only observable in-game.</li>
 * <li><b>Cost</b> — the old implementation compiled a giant alternation {@code (Copper|Tin|…)$} and ran a
 * {@link java.util.regex.Matcher} (plus {@code replaceFirst}) for every name in the dictionary, twice.
 * On a GTNH-sized dictionary that is ~100k matcher allocations at load; the suffix walk here allocates
 * nothing per name and only compares candidates that share the name's last character.</li>
 * </ul>
 *
 * <p>
 * No {@code net.minecraft*} imports: the input is just the live Ore Dictionary name set.
 */
public final class OreNames {

    /** Prefix that marks a metal in the Ore Dictionary taxonomy ({@code ingotCopper} -> {@code Copper}). */
    public static final String METAL_PREFIX = "ingot";

    /**
     * Substrings that disqualify a metal name from the default resource set (case-insensitive), matching
     * the historical {@code .*(?i)(Dense|Nether|Dye|Glass|Tiny|Small).*} filter.
     */
    private static final List<String> EXCLUDED_RESOURCE_TOKENS = Collections
        .unmodifiableList(Arrays.asList("dense", "nether", "dye", "glass", "tiny", "small"));

    /**
     * Kind prefix names that are never unified. Verbatim from the M4 taxonomy: these forms either have no
     * canonical equivalent across mods or are not metal shapes (decorative, vanilla-specific, …).
     */
    public static final Set<String> EXCLUDED_KINDS;
    static {
        final Set<String> kinds = new LinkedHashSet<>(
            Arrays.asList(
                "stair",
                "bars",
                "fence",
                "trapdoor",
                "stairs",
                "bucketLiquid",
                "slab",
                "crystal",
                "stick",
                "orePoor",
                "oreChargedCertus",
                "slabNether",
                "bucketDust",
                "oreCoralium",
                "gem",
                "sapling",
                "pulp",
                "item",
                "stone",
                "wood",
                "crop",
                "bottleLiquid",
                "quartz",
                "log",
                "mana",
                "chest",
                "crafter",
                "material",
                "leaves",
                "oreCertus",
                "crystalSHard",
                "eternalLife",
                "blockPrismarine",
                "door",
                "bells",
                "arrow",
                "itemCompressed",
                "enlightenedFused",
                "darkFused",
                "crystalShard",
                "food",
                "hardened"));
        EXCLUDED_KINDS = Collections.unmodifiableSet(kinds);
    }

    private OreNames() {}

    /**
     * Discovers the metal resource names from every {@code ingot}-prefixed Ore Dictionary name
     * ({@code ingotCopper} -> {@code Copper}), preserving first-seen order and dropping the excluded
     * decorative forms and duplicates.
     *
     * @param oreNames the live Ore Dictionary names (any order)
     * @return the capitalised resource names, e.g. {@code [Copper, Iron, …]}
     */
    public static List<String> metalResourceNames(final Collection<String> oreNames) {
        final List<String> resources = new ArrayList<>();
        if (oreNames == null || oreNames.isEmpty()) return resources;
        final Set<String> seen = new HashSet<>();
        for (final String oreName : oreNames) {
            if (oreName == null || !oreName.startsWith(METAL_PREFIX)) continue;
            final String stripped = oreName.substring(METAL_PREFIX.length());
            if (stripped.isEmpty() || isExcludedResourceName(stripped)) continue;
            final String resource = capitalizeFirst(stripped);
            if (seen.add(resource)) resources.add(resource);
        }
        return resources;
    }

    /**
     * @param strippedResource the metal name without its {@code ingot} prefix (e.g. {@code DenseCopper})
     * @return {@code true} when the name contains one of the excluded tokens ({@code dense}, {@code tiny}, …)
     */
    public static boolean isExcludedResourceName(final String strippedResource) {
        if (strippedResource == null) return true;
        final String lower = strippedResource.toLowerCase(Locale.ROOT);
        for (final String token : EXCLUDED_RESOURCE_TOKENS) if (lower.contains(token)) return true;
        return false;
    }

    /**
     * Finds the resource name that {@code oreName} ends with, preferring the <b>longest</b> match (the old
     * regex {@code (Iron|MeteoricIron)$} resolves {@code ingotMeteoricIron} to {@code MeteoricIron}, not
     * {@code Iron}). Matching is case-sensitive, exactly like the regex it replaces.
     *
     * @param oreName       the Ore Dictionary name (e.g. {@code dustTinyCopper})
     * @param resourceNames the discovered resource names
     * @return the matching suffix, or {@code null} when no resource name is a suffix of {@code oreName}
     */
    public static String resourceOf(final String oreName, final Collection<String> resourceNames) {
        if (oreName == null || oreName.isEmpty() || resourceNames == null || resourceNames.isEmpty()) return null;
        String match = null;
        for (final String resource : resourceNames) {
            if (resource == null || resource.isEmpty() || resource.length() > oreName.length()) continue;
            if (oreName.endsWith(resource) && (match == null || resource.length() > match.length())) match = resource;
        }
        return match;
    }

    /**
     * @param oreName      the full Ore Dictionary name
     * @param resourceName a suffix of {@code oreName} (see {@link #resourceOf})
     * @return the kind prefix ({@code ingotCopper} - {@code Copper} -> {@code ingot}), possibly empty
     */
    public static String kindOf(final String oreName, final String resourceName) {
        if (oreName == null) return "";
        if (resourceName == null || resourceName.isEmpty() || resourceName.length() > oreName.length()) return "";
        return oreName.substring(0, oreName.length() - resourceName.length());
    }

    /**
     * Groups every Ore Dictionary name under its resource, skipping excluded kinds. The result keeps the
     * first-seen order of both resources and kinds (so the model and its reports are deterministic for a
     * given dictionary iteration order), and only contains resources that actually have a usable kind.
     *
     * @param oreNames      the live Ore Dictionary names
     * @param resourceNames the discovered resource names ({@link #metalResourceNames})
     * @param excludedKinds kind prefixes to drop ({@link #EXCLUDED_KINDS}); {@code null} drops nothing
     * @return resource name -> set of kind names present in the dictionary
     */
    public static Map<String, Set<String>> kindsByResource(final Collection<String> oreNames,
        final Collection<String> resourceNames, final Set<String> excludedKinds) {
        final Map<String, Set<String>> basicResourceMap = new LinkedHashMap<>();
        if (oreNames == null || oreNames.isEmpty() || resourceNames == null || resourceNames.isEmpty())
            return basicResourceMap;

        // Only resource names sharing the last character can be a suffix; longest first, so the first hit
        // is the longest match the regex used to prefer.
        final Map<Character, List<String>> candidatesByLastChar = new HashMap<>();
        for (final String resource : new LinkedHashSet<>(resourceNames)) {
            if (resource == null || resource.isEmpty()) continue;
            final List<String> bucket = candidatesByLastChar
                .computeIfAbsent(resource.charAt(resource.length() - 1), c -> new ArrayList<>());
            int insertAt = 0;
            while (insertAt < bucket.size() && bucket.get(insertAt)
                .length() >= resource.length()) insertAt++;
            bucket.add(insertAt, resource);
        }

        for (final String oreName : oreNames) {
            if (oreName == null || oreName.isEmpty()) continue;
            final List<String> candidates = candidatesByLastChar.get(oreName.charAt(oreName.length() - 1));
            if (candidates == null) continue;
            String resourceName = null;
            for (final String candidate : candidates) {
                if (oreName.endsWith(candidate)) {
                    resourceName = candidate;
                    break;
                }
            }
            if (resourceName == null) continue;
            final String kindName = kindOf(oreName, resourceName);
            if (excludedKinds != null && excludedKinds.contains(kindName)) continue;
            basicResourceMap.computeIfAbsent(resourceName, k -> new LinkedHashSet<>())
                .add(kindName);
        }
        return basicResourceMap;
    }

    /**
     * Uppercases the first character only ({@code copper} -> {@code Copper}), matching the model's historic
     * resource naming. Locale-independent for the ASCII names Ore Dictionary prefixes use.
     *
     * @param value the value to capitalise (may be {@code null})
     * @return the capitalised value, or {@code null}
     */
    public static String capitalizeFirst(final String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
