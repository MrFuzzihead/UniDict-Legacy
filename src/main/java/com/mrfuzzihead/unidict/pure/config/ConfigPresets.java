package com.mrfuzzihead.unidict.pure.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BB-2 config presets — deterministic, user-facing default surfaces. A preset is just a
 * {@link ConfigData} used as the fallback in {@link ConfigReader}, so "presets pick defaults;
 * explicit keys still override" (docs/PLAN.md §BB-2) falls out naturally: any key present in the
 * raw source wins over the preset's value.
 *
 * <p>
 * Three documented presets:
 * <ul>
 * <li>{@link #minimal()} — minimal metal set, only the two vanilla-safe integrations (furnace, chest).</li>
 * <li>{@link #standard()} — the default {@link #defaults()}: all 9 kept integrations, full metal list.</li>
 * <li>{@link #maxCompat()} — standard plus aggressive features (input replacement + keep-one-entry).</li>
 * </ul>
 */
public final class ConfigPresets {

    private static final List<String> DEFAULT_OWNERS = Arrays
        .asList("ThermalFoundation", "minecraft", "IC2", "TConstruct");

    private static final List<String> DEFAULT_METALS = Arrays.asList(
        "Iron",
        "Gold",
        "Copper",
        "Tin",
        "Silver",
        "Lead",
        "Nickel",
        "Platinum",
        "Aluminum",
        "Aluminium",
        "Ardite",
        "Cobalt",
        "Osmium",
        "Mithril",
        "Zinc",
        "Invar",
        "Steel",
        "Bronze",
        "Electrum",
        "Brass",
        "Titanium",
        "Desh",
        "MeteoricIron",
        // ExtraPlanets (target pack, libs/extraplanets-3.0.0-dev.jar) — metals whose EP ore/ingot/block
        // variants overlap the canonical cross-mod entry and so should unify via the kept furnace /
        // galacticraft-compressor / crafting rewrites. Nickel/Zinc/Lead/Platinum are already above.
        "Tungsten", // GT5 registers ingotTungsten/dustTungsten/blockTungsten/oreTungsten
        "Uranium", // IC2 + GT5 register ingotUranium
        "Magnesium"); // GT5 registers ingotMagnesium

    private static final List<String> DEFAULT_CHILDREN = Arrays
        .asList("ore", "dustTiny", "chunk", "dust", "nugget", "ingot", "block", "plate", "gear");

    /** Default preset name used when the config file carries no {@code preset} key (BB-2). */
    public static final String DEFAULT_NAME = "standard";

    private ConfigPresets() {}

    /** The standard defaults every {@link ConfigReader} and the runtime {@code Config} start from. */
    public static ConfigData defaults() {
        return standard();
    }

    /**
     * Resolves a preset name to its deterministic {@link ConfigData} (BB-2). Accepts
     * case-insensitive {@code minimal} / {@code standard} / {@code max-compat}; unknown or blank
     * names fall back to {@link #standard()} so a typo never yields an empty default surface
     * (docs/PLAN.md §BB-2 gate). Pure and T1-testable — the runtime config front-end ({@code Config})
     * calls this after reading the {@code preset} key from the forge {@code .cfg}.
     */
    public static ConfigData byName(final String name) {
        if (name == null || name.trim()
            .isEmpty()) {
            return standard();
        }
        switch (name.trim()
            .toLowerCase(Locale.ROOT)) {
            case "minimal":
                return minimal();
            case "max-compat":
                return maxCompat();
            case "standard":
            default:
                return standard();
        }
    }

    /** Minimal: smallest safe surface — vanilla furnace + chest, a few metals. */
    public static ConfigData minimal() {
        return ConfigData.builder()
            .unifyDrops(true)
            .protectedOreDictionaryNames(new LinkedHashSet<>(Arrays.asList("raw")))
            .metalsToUnify(new LinkedHashSet<>(Arrays.asList("Iron", "Gold", "Copper", "Tin")))
            .childrenOfMetals(new LinkedHashSet<>(Arrays.asList("ingot", "ore", "dust", "nugget")))
            .resourceBlackList(Arrays.asList("Aluminium"))
            .ownerPriorities(DEFAULT_OWNERS)
            .ownerOfKind(new LinkedHashMap<>())
            .integrationModuleEnabled(true)
            .furnaceIntegration(true)
            .chestIntegration(true)
            .ae2Integration(false)
            .ic2Integration(false)
            .ieIntegration(false)
            .enderIOIntegration(false)
            .forestryIntegration(false)
            .craftingIntegration(false)
            .railcraftIntegration(false)
            .thermalExpansionIntegration(false)
            .galacticraftIntegration(false)
            .build();
    }

    /** Standard: the default surface — all 9 kept integrations, full metal list. */
    public static ConfigData standard() {
        return ConfigData.builder()
            .keepOneEntry(false)
            .inputReplacement(false)
            .unifyDrops(true)
            .autoHideInNEI(true)
            .kindDebugMode(false)
            .autoHideInNEIModBlackSet(new LinkedHashSet<>())
            .hideInNEIBlackSet(new LinkedHashSet<>())
            .protectedOreDictionaryNames(new LinkedHashSet<>(Arrays.asList("raw")))
            // EtF's copper block as the canonical copper block resolves its 9-ingot->block recipe
            // colliding with TF's (one block craftable, TF's rewritten to EtF's). No-op when EtF is
            // absent — the match is against live registry names, and no registered item then matches.
            .canonicalItemNames(new LinkedHashSet<>(Arrays.asList("etfuturum:copper_block")))
            .metalsToUnify(new LinkedHashSet<>(DEFAULT_METALS))
            .childrenOfMetals(new LinkedHashSet<>(DEFAULT_CHILDREN))
            .resourceBlackList(Arrays.asList("Aluminium"))
            .ownerPriorities(DEFAULT_OWNERS)
            .ownerOfKind(new LinkedHashMap<>())
            .integrationModuleEnabled(true)
            .furnaceIntegration(true)
            .ae2Integration(true)
            .ic2Integration(true)
            .ieIntegration(true)
            .chestIntegration(true)
            .enderIOIntegration(true)
            .forestryIntegration(true)
            .craftingIntegration(true)
            .railcraftIntegration(true)
            .thermalExpansionIntegration(true)
            .build();
    }

    /** Max-compat: standard plus the aggressive unification features. */
    public static ConfigData maxCompat() {
        final ConfigData standard = standard();
        final Map<String, List<String>> maxOwners = new LinkedHashMap<>();
        standard.ownerOfKind.forEach(maxOwners::put);
        return ConfigData.builder()
            .keepOneEntry(false) // keepOneEntry is deferred (TODO.md P0 #2, stretch) — max-compat uses
                                 // autoHideInNEI + both blacklists instead; the key is kept for back-compat.
            .inputReplacement(true)
            .unifyDrops(true)
            .autoHideInNEI(true)
            .kindDebugMode(true)
            .autoHideInNEIModBlackSet(standard.autoHideInNEIModBlackSet)
            .hideInNEIBlackSet(standard.hideInNEIBlackSet)
            .protectedOreDictionaryNames(standard.protectedOreDictionaryNames)
            .canonicalItemNames(standard.canonicalItemNames)
            .metalsToUnify(standard.metalsToUnify)
            .childrenOfMetals(standard.childrenOfMetals)
            .resourceBlackList(standard.resourceBlackList)
            .ownerPriorities(standard.ownerPriorities)
            .ownerOfKind(maxOwners)
            .integrationModuleEnabled(true)
            .furnaceIntegration(true)
            .ae2Integration(true)
            .ic2Integration(true)
            .ieIntegration(true)
            .chestIntegration(true)
            .enderIOIntegration(true)
            .forestryIntegration(true)
            .craftingIntegration(true)
            .railcraftIntegration(true)
            .thermalExpansionIntegration(true)
            .galacticraftIntegration(true)
            .build();
    }
}
