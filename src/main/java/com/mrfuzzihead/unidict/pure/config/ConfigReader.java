package com.mrfuzzihead.unidict.pure.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure reader: turns a flat {@code key -> rawValue} map (from a forge {@code Configuration} or a
 * hand-written fixture) into an immutable {@link ConfigData}. It starts from a preset's defaults
 * and overlays explicit keys — so "presets pick defaults; explicit keys still override" resolves to
 * deterministic last-write-wins (docs/PLAN.md §BB-2).
 *
 * <p>
 * Canonical source for unit fixtures: recognisable keys are parsed here (single source of truth
 * for the typed interpretation); the forge adapter ({@code ForgeConfigIO}) only moves data in/out of
 * a {@code Configuration}. Keys that are not recognised (removed mod toggles, deferred keys such as
 * {@code customUnifiedResources}) are recorded in {@link Result#ignored} for an INFO log — never a
 * silent wipe and never a hard failure.
 */
public final class ConfigReader {

    // general
    private static final String KEY_KEEP_ONE_ENTRY = "keepOneEntry";
    private static final String KEY_INPUT_REPLACEMENT = "inputReplacement";
    private static final String KEY_UNIFY_DROPS = "unifyDrops";
    /** Canonical now-kept key: the per-mod NEI-hide exemption. */
    private static final String KEY_AUTO_HIDE_MOD_BLACK = "autoHideInNEIModBlackList";
    /** Legacy pre-P0 name for the same mod blacklist; still accepted (autoHide owns it now, keepOneEntry deferred). */
    private static final String LEGACY_KEEP_ONE_MOD_BLACK = "keepOneEntryModBlackList";
    private static final String KEY_AUTO_HIDE_NEI = "autoHideInNEI";
    private static final String KEY_HIDE_NEI_BLACK = "autoHideInNEIBlackList";
    private static final String KEY_PROTECTED_OD_NAMES = "protectedOreDictionaryNames";
    private static final String KEY_KIND_DEBUG = "kindDebugMode";
    /** Consumed by the loader ({@code Config}) to select the default surface — not part of {@link ConfigData}. */
    private static final String KEY_PRESET = "preset";

    // resources / owners
    private static final String KEY_METALS = "metalsToUnify";
    private static final String KEY_CHILDREN = "childrenOfMetals";
    private static final String KEY_BLACKLIST = "resourceBlackList";
    private static final String KEY_OWNER_PRIORITIES = "ownerPriorities";
    private static final String OWNER_OF_KIND_PREFIX = "ownerOfKind.";
    // legacy owner aliases
    private static final String LEGACY_OWNER_EVERYTHING = "ownerOfEveryThing";
    private static final String LEGACY_OWNER_EVERY_PREFIX = "ownerOfEvery";

    // integrations
    private static final String KEY_INTEGRATION_MASTER = "integration";
    private static final String KEY_FURNACE = "furnaceIntegration";
    private static final String KEY_AE2 = "appliedEnergistics2";
    private static final String KEY_IC2 = "industrialCraft2";
    private static final String KEY_IE = "immersiveEngineering";
    private static final String KEY_CHEST = "chestIntegration";
    private static final String KEY_CRAFTING = "craftingIntegration";
    private static final String KEY_EIO = "enderIO";
    private static final String KEY_FORESTRY = "forestry";
    private static final String KEY_RAILCRAFT = "railcraft";
    private static final String KEY_THERMAL = "thermalExpansion";
    private static final String KEY_GALACTICRAFT = "galacticraft";

    private ConfigReader() {}

    /** Result of a parse: the typed config plus any non-empty / legacy / unknown keys we ignored. */
    public static final class Result {

        public final ConfigData config;
        public final List<String> ignored;

        Result(final ConfigData config, final List<String> ignored) {
            this.config = config;
            this.ignored = ignored;
        }
    }

    public static Result parse(final ConfigData defaults, final Map<String, String> raw) {
        final ConfigData.Builder b = ConfigData.builder()
            .keepOneEntry(defaults.keepOneEntry)
            .inputReplacement(defaults.inputReplacement)
            .unifyDrops(defaults.unifyDrops)
            .autoHideInNEI(defaults.autoHideInNEI)
            .kindDebugMode(defaults.kindDebugMode)
            .autoHideInNEIModBlackSet(defaults.autoHideInNEIModBlackSet)
            .hideInNEIBlackSet(defaults.hideInNEIBlackSet)
            .protectedOreDictionaryNames(defaults.protectedOreDictionaryNames)
            .metalsToUnify(defaults.metalsToUnify)
            .childrenOfMetals(defaults.childrenOfMetals)
            .resourceBlackList(defaults.resourceBlackList)
            .ownerPriorities(defaults.ownerPriorities)
            .ownerOfKind(defaults.ownerOfKind)
            .integrationModuleEnabled(defaults.integrationModuleEnabled)
            .furnaceIntegration(defaults.furnaceIntegration)
            .ae2Integration(defaults.ae2Integration)
            .ic2Integration(defaults.ic2Integration)
            .ieIntegration(defaults.ieIntegration)
            .chestIntegration(defaults.chestIntegration)
            .enderIOIntegration(defaults.enderIOIntegration)
            .railcraftIntegration(defaults.railcraftIntegration)
            .thermalExpansionIntegration(defaults.thermalExpansionIntegration)
            .galacticraftIntegration(defaults.galacticraftIntegration);

        final List<String> ignored = new ArrayList<>();
        final Map<String, List<String>> ownerOfKind = new LinkedHashMap<>(defaults.ownerOfKind);

        // Canonicalize owner aliases so a legacy key always beats the auto-written modern default:
        // ownerOfEveryThing -> ownerPriorities, ownerOfEvery<KIND> -> ownerOfKind.<KIND>.
        final Map<String, String> working = new LinkedHashMap<>(raw);
        if (working.containsKey(LEGACY_OWNER_EVERYTHING)) {
            working.put(KEY_OWNER_PRIORITIES, working.get(LEGACY_OWNER_EVERYTHING));
            working.remove(LEGACY_OWNER_EVERYTHING);
        }
        for (final String key : new ArrayList<>(working.keySet())) {
            if (key.startsWith(LEGACY_OWNER_EVERY_PREFIX)) {
                final String kindName = key.substring(LEGACY_OWNER_EVERY_PREFIX.length());
                final String canonical = OWNER_OF_KIND_PREFIX + kindName;
                working.put(canonical, working.get(key));
                working.remove(key);
            }
        }

        for (final Map.Entry<String, String> entry : working.entrySet()) {
            applyKey(b, entry.getKey(), entry.getValue(), defaults, ownerOfKind, ignored);
        }

        b.ownerOfKind(ownerOfKind);
        return new Result(b.build(), ignored);
    }

    private static void applyKey(final ConfigData.Builder b, final String key, final String value,
        final ConfigData defaults, final Map<String, List<String>> ownerOfKind, final List<String> ignored) {
        switch (key) {
            case KEY_KEEP_ONE_ENTRY:
                b.keepOneEntry(bool(value, defaults.keepOneEntry));
                return;
            case KEY_INPUT_REPLACEMENT:
                b.inputReplacement(bool(value, defaults.inputReplacement));
                return;
            case KEY_UNIFY_DROPS:
                b.unifyDrops(bool(value, defaults.unifyDrops));
                return;
            case KEY_AUTO_HIDE_NEI:
                b.autoHideInNEI(bool(value, defaults.autoHideInNEI));
                return;
            case KEY_KIND_DEBUG:
                b.kindDebugMode(bool(value, defaults.kindDebugMode));
                return;
            case KEY_PRESET:
                // Not a ConfigData field: the loader selects the default surface with it (BB-2).
                return;
            case LEGACY_KEEP_ONE_MOD_BLACK:
            case KEY_AUTO_HIDE_MOD_BLACK:
                b.autoHideInNEIModBlackSet(set(value));
                return;
            case KEY_HIDE_NEI_BLACK:
                b.hideInNEIBlackSet(set(value));
                return;
            case KEY_PROTECTED_OD_NAMES:
                b.protectedOreDictionaryNames(set(value));
                return;
            case KEY_METALS:
                b.metalsToUnify(set(value));
                return;
            case KEY_CHILDREN:
                b.childrenOfMetals(set(value));
                return;
            case KEY_BLACKLIST:
                b.resourceBlackList(list(value));
                return;
            case KEY_OWNER_PRIORITIES:
                b.ownerPriorities(list(value));
                return;
            case KEY_INTEGRATION_MASTER:
                b.integrationModuleEnabled(bool(value, defaults.integrationModuleEnabled));
                return;
            case KEY_FURNACE:
                b.furnaceIntegration(bool(value, defaults.furnaceIntegration));
                return;
            case KEY_AE2:
                b.ae2Integration(bool(value, defaults.ae2Integration));
                return;
            case KEY_IC2:
                b.ic2Integration(bool(value, defaults.ic2Integration));
                return;
            case KEY_IE:
                b.ieIntegration(bool(value, defaults.ieIntegration));
                return;
            case KEY_CHEST:
                b.chestIntegration(bool(value, defaults.chestIntegration));
                return;
            case KEY_CRAFTING:
                b.craftingIntegration(bool(value, defaults.craftingIntegration));
                return;
            case KEY_EIO:
                b.enderIOIntegration(bool(value, defaults.enderIOIntegration));
                return;
            case KEY_FORESTRY:
                b.forestryIntegration(bool(value, defaults.forestryIntegration));
                return;
            case KEY_RAILCRAFT:
                b.railcraftIntegration(bool(value, defaults.railcraftIntegration));
                return;
            case KEY_THERMAL:
                b.thermalExpansionIntegration(bool(value, defaults.thermalExpansionIntegration));
                return;
            case KEY_GALACTICRAFT:
                b.galacticraftIntegration(bool(value, defaults.galacticraftIntegration));
                return;
            case LEGACY_OWNER_EVERYTHING:
                b.ownerPriorities(list(value)); // modern alias of ownerPriorities
                return;
            default:
                break;
        }

        if (key.startsWith(OWNER_OF_KIND_PREFIX)) {
            ownerOfKind.put(key.substring(OWNER_OF_KIND_PREFIX.length()), list(value));
            return;
        }
        if (key.startsWith(LEGACY_OWNER_EVERY_PREFIX)) {
            ownerOfKind.put(key.substring(LEGACY_OWNER_EVERY_PREFIX.length()), list(value));
            return;
        }
        // Anything else (removed mod toggles, deferred keys, unknown) is accepted-but-ignored.
        ignored.add(key);
    }

    // ---- value parsing -------------------------------------------------

    private static boolean bool(final String raw, final boolean fallback) {
        final String v = raw == null ? "" : raw.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return fallback;
    }

    private static List<String> list(final String raw) {
        final List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String v = raw.trim();
        if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length() - 1);
        if (v.isEmpty()) return out;
        for (final String item : v.split(",")) {
            final String trimmed = item.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static Set<String> set(final String raw) {
        return new LinkedHashSet<>(list(raw));
    }
}
