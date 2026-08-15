package com.mrfuzzihead.unidict.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.mrfuzzihead.unidict.pure.config.ConfigData;
import com.mrfuzzihead.unidict.pure.config.ConfigPresets;
import com.mrfuzzihead.unidict.pure.config.ConfigReader;
import com.mrfuzzihead.unidict.pure.config.ConfigReader.Result;

/**
 * Thin forge adapter that moves a {@link Configuration} in/out of the pure {@link ConfigData}
 * model. It registers the known keys with their {@code defaults} (forge getters return the existing
 * value when present and create the default + mark the config changed when absent), then reads every
 * property into a flat {@code key -> rawValue} map handed to {@link ConfigReader} (single source of
 * typed interpretation).
 *
 * <p>
 * Saving is additive only: {@link #registerDefaults} fills in any missing keys (so a fresh install
 * gets a complete, readable config file) and the file is written iff something changed. Existing user
 * values are never overwritten, and the upstream "delete the whole {@code .cfg} on version mismatch"
 * behavior is explicitly NOT reproduced — so a user's settings are never wiped (docs/PLAN.md §M2).
 */
public final class ForgeConfigIO {

    private static final String CATEGORY_RESOURCES = "resources";
    private static final String CATEGORY_INTEGRATIONS = "integrations";
    private static final String KEY_PRESET = "preset";

    private ForgeConfigIO() {}

    /** Registers every known key with its effective default so absent keys are written on save. */
    public static void registerDefaults(final Configuration cfg, final ConfigData defaults) {
        final String general = Configuration.CATEGORY_GENERAL;
        cfg.getString(
            KEY_PRESET,
            general,
            ConfigPresets.DEFAULT_NAME,
            "Configuration preset that picks the default surface: minimal | standard | max-compat "
                + "(BB-2). Presets pick defaults; the explicit keys below still override. "
                + "minimal = vanilla-safe only (furnace + chest); max-compat = standard plus the "
                + "aggressive unification features (input replacement + keep-one-entry). Changing the "
                + "preset on an existing config applies to keys that don't already carry an explicit "
                + "value in the file.");
        booleanProp(
            cfg,
            general,
            "keepOneEntry",
            defaults.keepOneEntry,
            "Keep only the main entry per unified resource (selection core, M4).");
        booleanProp(
            cfg,
            general,
            "inputReplacement",
            defaults.inputReplacement,
            "Replace non-standard machine inputs (machine-only, M6/M7).");
        booleanProp(
            cfg,
            general,
            "unifyDrops",
            defaults.unifyDrops,
            "Convert dropped (ground) item entities to the canonical entry of their unified resource.");
        booleanProp(
            cfg,
            general,
            "autoHideInNEI",
            defaults.autoHideInNEI,
            "Auto-hide non-main variants (NEI gate). Only active when keepOneEntry is off.");
        booleanProp(
            cfg,
            general,
            "kindDebugMode",
            defaults.kindDebugMode,
            "Log kind information for the transparency report (BB-1).");
        listProp(
            cfg,
            general,
            "keepOneEntryModBlackList",
            defaults.keepOneEntryModBlackSet,
            "Mods blacklisted from keepOneEntry (exact mod IDs).");
        listProp(
            cfg,
            general,
            "autoHideInNEIBlackList",
            defaults.hideInNEIBlackSet,
            "Kinds that are never auto-hidden in NEI.");

        listProp(cfg, CATEGORY_RESOURCES, "metalsToUnify", defaults.metalsToUnify, "Metals to unify.");
        listProp(
            cfg,
            CATEGORY_RESOURCES,
            "childrenOfMetals",
            defaults.childrenOfMetals,
            "Child kinds to unify for each metal.");
        listProp(
            cfg,
            CATEGORY_RESOURCES,
            "resourceBlackList",
            defaults.resourceBlackList,
            "Resources excluded from unification (avoid duplicates).");
        listProp(
            cfg,
            CATEGORY_RESOURCES,
            "ownerPriorities",
            defaults.ownerPriorities,
            "Global owner-mod priority order (first = main entry).");
        defaults.ownerOfKind.forEach(
            (kind, owners) -> listProp(
                cfg,
                CATEGORY_RESOURCES,
                "ownerOfKind." + kind,
                owners,
                "Owner-mod priority for kind " + kind + "."));

        booleanProp(
            cfg,
            CATEGORY_INTEGRATIONS,
            "integration",
            defaults.integrationModuleEnabled,
            "Master switch for the integration module.");
        booleanProp(
            cfg,
            CATEGORY_INTEGRATIONS,
            "furnaceIntegration",
            defaults.furnaceIntegration,
            "Vanilla furnace integration.");
        booleanProp(
            cfg,
            CATEGORY_INTEGRATIONS,
            "appliedEnergistics2",
            defaults.ae2Integration,
            "Applied Energistics 2 grinders.");
        booleanProp(
            cfg,
            CATEGORY_INTEGRATIONS,
            "industrialCraft2",
            defaults.ic2Integration,
            "Industrial Craft 2 machines.");
        booleanProp(
            cfg,
            CATEGORY_INTEGRATIONS,
            "immersiveEngineering",
            defaults.ieIntegration,
            "Immersive Engineering machines.");
        booleanProp(
            cfg,
            CATEGORY_INTEGRATIONS,
            "chestIntegration",
            defaults.chestIntegration,
            "Chest / loot integration.");
        booleanProp(
            cfg,
            CATEGORY_INTEGRATIONS,
            "craftingIntegration",
            defaults.craftingIntegration,
            "Crafting table recipe output rewrite (M5).");
        booleanProp(cfg, CATEGORY_INTEGRATIONS, "enderIO", defaults.enderIOIntegration, "Ender IO machines.");
        booleanProp(cfg, CATEGORY_INTEGRATIONS, "forestry", defaults.forestryIntegration, "Forestry machines.");
        booleanProp(cfg, CATEGORY_INTEGRATIONS, "railcraft", defaults.railcraftIntegration, "Railcraft machines.");
        booleanProp(
            cfg,
            CATEGORY_INTEGRATIONS,
            "thermalExpansion",
            defaults.thermalExpansionIntegration,
            "Thermal Expansion machines.");
    }

    /**
     * Reads the {@code preset} key (case-preserving raw string) from the config. Registers the key
     * with the default name when absent so it is written on save. The caller resolves it to a
     * {@link ConfigData} via {@link ConfigPresets#byName(String)} before registering/parsing the
     * rest of the surface (BB-2).
     */
    public static String readPreset(final Configuration cfg) {
        return cfg.getString(
            KEY_PRESET,
            Configuration.CATEGORY_GENERAL,
            ConfigPresets.DEFAULT_NAME,
            "Configuration preset: minimal | standard | max-compat.");
    }

    /** Reads the configuration and parses it against {@code defaults}. Call {@link #registerDefaults} first. */
    public static ConfigData load(final Configuration cfg, final ConfigData defaults) {
        final Map<String, String> raw = flatten(cfg);
        final Result result = ConfigReader.parse(defaults, raw);
        return result.config;
    }

    /** Writes the configuration to disk only if keys changed since load (additive). */
    public static void saveIfChanged(final Configuration cfg) {
        if (cfg.hasChanged()) cfg.save();
    }

    private static void booleanProp(final Configuration cfg, final String category, final String key,
        final boolean defaultValue, final String comment) {
        cfg.getBoolean(key, category, defaultValue, comment);
    }

    private static void listProp(final Configuration cfg, final String category, final String key,
        final java.util.Collection<String> defaultValues, final String comment) {
        cfg.getStringList(key, category, new ArrayList<>(defaultValues).toArray(new String[0]), comment);
    }

    private static Map<String, String> flatten(final Configuration cfg) {
        final Map<String, String> raw = new LinkedHashMap<>();
        for (final String catName : cfg.getCategoryNames()) {
            final ConfigCategory cat = cfg.getCategory(catName);
            for (final Map.Entry<String, Property> entry : cat.getValues()
                .entrySet()) {
                final Property property = entry.getValue();
                raw.put(property.getName(), rawValue(property));
            }
        }
        return raw;
    }

    private static String rawValue(final Property property) {
        if (property.isList()) return String.join(", ", property.getStringList());
        return property.getString();
    }
}
