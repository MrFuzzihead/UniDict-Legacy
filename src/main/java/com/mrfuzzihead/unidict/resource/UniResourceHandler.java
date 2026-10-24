package com.mrfuzzihead.unidict.resource;

/*
 * Rebuilt from wanion.unidict.resource.UniResourceHandler (WanionCane, MPL-2.0).
 * M4 fixes (docs/PLAN.md §M4 + scope rework 2026-08-12): createResources()/postInit() are
 * SEQUENTIAL (never parallelStream — the source of the NEI crash); no removeFromElsewhere /
 * customUnifiedResources / global OD mutation (deferred). The DI container is gone; the
 * ResourceHandler is built directly and published to UniDict.resourceHandler.
 */

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.oredict.UniOreDictionary;
import com.mrfuzzihead.unidict.pure.OreNames;
import com.mrfuzzihead.unidict.pure.config.KindNames;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

public final class UniResourceHandler {

    private static final Set<Long> kindBlackSet = new LinkedHashSet<>();
    private static boolean hasInit;

    private final Map<String, Resource<UniResourceContainer>> resourceMap = new LinkedHashMap<>();
    private final Map<String, Resource<UniResourceContainer>> apiResourceMap = new LinkedHashMap<>();
    /** The dictionary names this model was built from; published with the handler for the late-OD check. */
    private final Set<String> dictionaryNames = new LinkedHashSet<>();

    private UniResourceHandler() {}

    public static UniResourceHandler create() {
        if (hasInit) return null;
        hasInit = true;
        return new UniResourceHandler();
    }

    /** Builds the resource model from the live Ore Dictionary names. Sequential. */
    public void createResources() {
        // One snapshot of the live dictionary names feeds both classification passes. The taxonomy rules
        // (ingot-prefixed metals, Tiny/Dense/... exclusion, excluded kinds, longest-suffix matching) live
        // in the pure, T1-tested OreNames helper instead of a per-name regex walk (docs/CODE_REVIEW.md
        // OPT-5): no Matcher allocation per dictionary entry, and the same classification the T1 tests pin.
        final List<String> oreNames = UniOreDictionary.instance()
            .getOreNames();
        dictionaryNames.clear();
        dictionaryNames.addAll(oreNames);
        final List<String> allTheResourceNames = OreNames.metalResourceNames(oreNames);
        final Map<String, Set<String>> basicResourceMap = OreNames
            .kindsByResource(oreNames, allTheResourceNames, OreNames.EXCLUDED_KINDS);

        // Only the configured child kinds (ConfigData.childrenOfMetals) are ever unified: the kind
        // taxonomy is a global long bitfield capped at Resource.MAX_KINDS (64) kinds, and any kind
        // outside childrenOfMetals is dropped by filteredClone(...) below anyway. So we register bits /
        // build containers ONLY for those children — registering every OD prefix as a kind would exceed
        // the 64-kind cap on a large pack (the original "Cannot register more than 64 resource kinds"
        // crash).
        //
        // Matching is case-insensitive (KindNames) so `childrenOfMetals=[Ingot, DustTiny]` works, while
        // the bit is registered under the OD-derived spelling (`ingot`, `dustTiny`) that the rest of the
        // model compares against. The mask is accumulated HERE from the kinds actually registered — the
        // old constructor pre-registered configured spellings, which allocated different bits from the
        // OD-derived names and silently dropped every child when the casings differed (BUG-3).
        final Set<String> unifiedKinds = Config.get().childrenOfMetals;
        long unifiedKindMask = 0;
        for (final Map.Entry<String, Set<String>> entry : basicResourceMap.entrySet()) {
            final String resourceName = entry.getKey();
            final TLongObjectMap<UniResourceContainer> kindMap = new TLongObjectHashMap<>();
            for (final String kindName : entry.getValue()) {
                if (!KindNames.containsIgnoreCase(unifiedKinds, kindName)) continue;
                final long kind = Resource.registerAndGet(kindName);
                kindMap.put(kind, new UniResourceContainer(kindName + resourceName, kind));
                unifiedKindMask |= kind;
            }
            apiResourceMap.put(resourceName, new Resource<>(resourceName, kindMap));
        }
        final long childrenOfMetals = unifiedKindMask;

        Config.get().metalsToUnify.stream()
            .filter(apiResourceMap::containsKey)
            .forEach(
                resourceName -> resourceMap.put(
                    resourceName,
                    apiResourceMap.get(resourceName)
                        .filteredClone(childrenOfMetals)
                        .setSortOfChildren(true)));

        logKindDiagnosticsIfEnabled();
    }

    /**
     * {@code kindDebugMode} (docs/CODE_REVIEW.md BUG-18): log the registered kinds plus any configured kind
     * name that resolved to nothing — a typo in {@code childrenOfMetals}/{@code hideInNEIBlackSet}/
     * {@code ownerOfKind} used to be silently inert. The same diagnostic is always emitted as a
     * {@code kindDiagnostics} verify line, so the harness covers the computation.
     */
    private void logKindDiagnosticsIfEnabled() {
        if (!Config.get().kindDebugMode) return;
        final List<String> registered = KindNames.sorted(Resource.getKinds());
        UniDict.LOG.info(
            "[unidict] kindDebug registered={} unknown={}",
            registered,
            KindNames.unknownKindNames(KindNames.configuredKindNames(Config.get()), registered));
    }

    /** Reconciles entries and builds + publishes the {@link ResourceHandler}. Sequential. */
    public void postInit() {
        apiResourceMap.values()
            .forEach(Resource::updateEntries);
        resourceMap.values()
            .forEach(Resource::updateEntries);
        // Filter BEFORE the handler indexes the model. Previously the blacklist was applied after
        // populateIndividualStackAttributes(), so an "excluded" resource still resolved (and was still
        // rewritten) through the canonical index and the container lookup — only the report/NEI view
        // lost it (docs/CODE_REVIEW.md BUG-1).
        applyResourceBlackList(resourceMap, apiResourceMap, Config.get().resourceBlackList);
        final ResourceHandler resourceHandler = new ResourceHandler(resourceMap, dictionaryNames);
        resourceHandler.populateIndividualStackAttributes();
        UniDict.resourceHandler = resourceHandler;
    }

    /**
     * Removes the configured {@code resourceBlackList} entries from both model maps. Package-private T2
     * seam; the ordering contract is the point — this must run <em>before</em>
     * {@link ResourceHandler#populateIndividualStackAttributes()} so a blacklisted resource is absent
     * from the canonical index and {@code containerExists}, not merely from report/NEI iteration.
     *
     * @param resourceMap    the unified resource map (what {@code ResourceHandler.resources} views)
     * @param apiResourceMap the full pre-filter map
     * @param blackList      configured resource names to drop; {@code null}/empty is a no-op
     */
    static void applyResourceBlackList(final Map<String, Resource<UniResourceContainer>> resourceMap,
        final Map<String, Resource<UniResourceContainer>> apiResourceMap, final Collection<String> blackList) {
        if (blackList == null || blackList.isEmpty()) return;
        for (final String blackListedResource : blackList) {
            resourceMap.remove(blackListedResource);
            apiResourceMap.remove(blackListedResource);
        }
    }

    /**
     * The {@link Resource} kinds blacklisted from NEI hiding (derived from {@code hideInNEIBlackSet}).
     *
     * <p>
     * Configured names are resolved against the <em>registered</em> kind names ignoring case, so
     * {@code autoHideInNEIBlackList=Ore} matches the registered {@code ore} (and {@code DustTiny} the
     * camelCase {@code dustTiny}); unknown names are skipped.
     */
    public static Set<Long> getKindBlackSet() {
        if (kindBlackSet.isEmpty()) {
            for (final String configured : Config.get().hideInNEIBlackSet) {
                final String registered = KindNames.findIgnoreCase(Resource.getKinds(), configured);
                if (registered != null) kindBlackSet.add(Resource.getKindOfName(registered));
            }
        }
        return kindBlackSet;
    }
}
