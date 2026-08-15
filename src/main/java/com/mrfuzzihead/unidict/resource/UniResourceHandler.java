package com.mrfuzzihead.unidict.resource;

/*
 * Rebuilt from wanion.unidict.resource.UniResourceHandler (WanionCane, MPL-2.0).
 * M4 fixes (docs/PLAN.md §M4 + scope rework 2026-08-12): createResources()/postInit() are
 * SEQUENTIAL (never parallelStream — the source of the NEI crash); no removeFromElsewhere /
 * customUnifiedResources / global OD mutation (deferred). The DI container is gone; the
 * ResourceHandler is built directly and published to UniDict.resourceHandler.
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.collect.Sets;
import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.oredict.UniOreDictionary;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

public final class UniResourceHandler {

    private static final Set<Long> kindBlackSet = new LinkedHashSet<>();
    private static boolean hasInit;

    private final Map<String, Resource<UniResourceContainer>> resourceMap = new LinkedHashMap<>();
    private final Map<String, Resource<UniResourceContainer>> apiResourceMap = new LinkedHashMap<>();
    private final long childrenOfMetals;

    private UniResourceHandler() {
        long childrenOfMetals = 0;
        for (final String child : Config.get().childrenOfMetals) childrenOfMetals += Resource.registerAndGet(child);
        this.childrenOfMetals = childrenOfMetals;
    }

    public static UniResourceHandler create() {
        if (hasInit) return null;
        hasInit = true;
        return new UniResourceHandler();
    }

    /** Builds the resource model from the live Ore Dictionary names. Sequential. */
    public void createResources() {
        final List<String> allTheResourceNames = new ArrayList<>();
        final Pattern resourceBlackTagsPattern = Pattern.compile(".*(?i)(Dense|Nether|Dye|Glass|Tiny|Small).*");
        UniOreDictionary.instance()
            .getThoseThatMatches("^ingot")
            .stream()
            .filter(
                matcher -> !resourceBlackTagsPattern.matcher(matcher.replaceFirst(""))
                    .find())
            .forEach(matcher -> allTheResourceNames.add(capitalize(matcher.replaceFirst(""))));

        final StringBuilder patternBuilder = new StringBuilder("(");
        for (final Iterator<String> it = allTheResourceNames.iterator(); it.hasNext();) patternBuilder.append(it.next())
            .append(it.hasNext() ? "|" : ")$");

        final Map<String, Set<String>> basicResourceMap = new LinkedHashMap<>();
        final Set<String> allTheKindsBlackSet = Sets.newHashSet(
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
            "hardened");
        UniOreDictionary.instance()
            .getThoseThatMatches(Pattern.compile(patternBuilder.toString()))
            .forEach(matcher -> {
                final String kindName = matcher.replaceFirst("");
                if (!allTheKindsBlackSet.contains(kindName)) {
                    final String resourceName = matcher.group();
                    if (!basicResourceMap.containsKey(resourceName))
                        basicResourceMap.put(resourceName, new LinkedHashSet<>());
                    basicResourceMap.get(resourceName)
                        .add(kindName);
                }
            });

        // Only the configured child kinds (ConfigData.childrenOfMetals) are ever unified: the kind
        // taxonomy is a global long bitfield capped at Resource.MAX_KINDS (64) kinds, and any kind
        // outside childrenOfMetals is dropped by filteredClone(childrenOfMetals) below anyway. So we
        // register bits / build containers ONLY for those children — registering every OD prefix as a
        // kind would exceed the 64-kind cap on a large pack (the original
        // "Cannot register more than 64 resource kinds" crash).
        final Set<String> unifiedKinds = Config.get().childrenOfMetals;
        basicResourceMap.forEach((resourceName, kinds) -> {
            final TLongObjectMap<UniResourceContainer> kindMap = new TLongObjectHashMap<>();
            for (final String kindName : kinds) {
                if (!unifiedKinds.contains(kindName)) continue;
                final long kind = Resource.registerAndGet(kindName);
                kindMap.put(kind, new UniResourceContainer(kindName + resourceName, kind));
            }
            apiResourceMap.put(resourceName, new Resource<>(resourceName, kindMap));
        });

        Config.get().metalsToUnify.stream()
            .filter(apiResourceMap::containsKey)
            .forEach(
                resourceName -> resourceMap.put(
                    resourceName,
                    apiResourceMap.get(resourceName)
                        .filteredClone(childrenOfMetals)
                        .setSortOfChildren(true)));
    }

    /** Reconciles entries and builds + publishes the {@link ResourceHandler}. Sequential. */
    public void postInit() {
        apiResourceMap.values()
            .forEach(Resource::updateEntries);
        resourceMap.values()
            .forEach(Resource::updateEntries);
        final ResourceHandler resourceHandler = new ResourceHandler(resourceMap);
        resourceHandler.populateIndividualStackAttributes();
        for (final String blackListedResource : Config.get().resourceBlackList) {
            resourceMap.remove(blackListedResource);
            apiResourceMap.remove(blackListedResource);
        }
        UniDict.resourceHandler = resourceHandler;
    }

    /** The {@link Resource} kinds blacklisted from NEI hiding (derived from {@code hideInNEIBlackSet}). */
    public static Set<Long> getKindBlackSet() {
        if (kindBlackSet.isEmpty())
            Config.get().hideInNEIBlackSet.forEach(blackKind -> kindBlackSet.add(Resource.getKindOfName(blackKind)));
        return kindBlackSet;
    }

    private static String capitalize(final String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
