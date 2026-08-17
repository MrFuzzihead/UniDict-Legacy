package com.mrfuzzihead.unidict.pure.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, pure (Minecraft-free) configuration value — BB-2's "cleaner config" and the substrate
 * M4/{@code SelectionRules}, the BB-1 report and every kept integration read. It is "clean" versus
 * upstream (wanion.unidict.Config) in three ways:
 *
 * <ol>
 * <li><b>Grouped, fewer overlapping knobs.</b> Upstream's three-way owner model
 * ({@code enableSpecificKindSort} + {@code ownerOfEveryThing} + per-kind {@code ownerOfEvery&lt;Kind&gt;})
 * is collapsed into {@link #ownerPriorities} (global default order) + {@link #ownerOfKind} (per-kind
 * override). A per-kind override simply applies when present; there is no boolean to toggle the
 * feature on/off.</li>
 * <li><b>Only kept integrations.</b> Toggles exist for the 10 kept integrations; the removed mods
 * are gone.</li>
 * <li><b>Preset-friendly.</b> {@link ConfigPresets} supplies deterministic defaults and
 * {@link ConfigReader} overlays explicit keys (last-write-wins), so a preset is just a default
 * surface explicit keys override.</li>
 * </ol>
 *
 * <p>
 * Immutability keeps T1 tests race-free: every {@link Set}/{@link List}/{@link Map} is
 * defensively copied and exposed unmodifiable. Build via {@link #builder()}.
 */
public final class ConfigData {

    // ---- general ------------------------------------------------------
    /**
     * Upstream-kept key: strict one-entry collapse. Deferred/stretch (TODO.md P0 #2); parsed for back-compat but not
     * wired.
     */
    public final boolean keepOneEntry;
    /** Replace non-standard machine inputs (machine-only, M6/M7). */
    public final boolean inputReplacement;
    /** Convert dropped (ground) item entities to the canonical entry of their unified resource. */
    public final boolean unifyDrops;
    /** Auto-hide non-main variants in NEI (the single NEI-hide mechanism; kind + mod blacklists below). */
    public final boolean autoHideInNEI;
    /** Log kind information for the transparency report (BB-1). */
    public final boolean kindDebugMode;
    /** Mod-level NEI-hide exemption (per owner mod). */
    public final Set<String> autoHideInNEIModBlackSet;
    /** Kind-level NEI-hide exemption (e.g. {@code ore}). */
    public final Set<String> hideInNEIBlackSet;
    /**
     * OD-name substrings protecting an item from canonicalization and NEI hiding. An item that is a
     * member of any OreDictionary entry whose name contains one of these is never collapsed to a
     * canonical block etc. — e.g. default {@code "raw"} keeps EtF raw metals (tagged {@code rawCopper}/…)
     * as the mined/processing form instead of morphing them into a mod's copper ore block.
     */
    public final Set<String> protectedOreDictionaryNames;
    /**
     * Qualified item-name substrings protecting a specific item (or its variants) from canonicalization
     * and NEI hiding. Matched against the registered {@code modid:path} name — unlike
     * {@link #protectedOreDictionaryNames}, which matches OD tags. This is for cases where the item has
     * no carve-out OD tag: e.g. {@code "EtFuturum:block_copper"} keeps a mod's decorative copper block
     * (incl. aged/oxidized variants of that name) obtainable and visible instead of folding it into the
     * canonical copper block.
     */
    public final Set<String> protectedItemNames;
    /**
     * Qualified item-name substrings that make the matching variant the <b>canonical (main)</b> entry of
     * its unified container — an ownership override on top of the owner-priority order. This is the
     * mechanism for shared-OD-tag craft conflicts: e.g. EtF and TF both register 9-ingot → block recipes
     * from {@code ingotCopper}, so they collide on the same crafting pattern; making
     * {@code "EtFuturum:block_copper"} canonical means every copper-block recipe's output is rewritten to
     * that block, so only one block is craftable (and the colliding TF recipe effectively produces the
     * same block).
     */
    public final Set<String> canonicalItemNames;

    // ---- resources / owners ------------------------------------------
    public final Set<String> metalsToUnify;
    public final Set<String> childrenOfMetals;
    public final List<String> resourceBlackList;
    /** Global owner priority (dedupes upstream {@code ownerOfEveryThing}); unmodifiable ordered list. */
    public final List<String> ownerPriorities;
    /** Per-kind owner override, keyed by kind name (dedupes {@code ownerOfEvery<Kind>} + the sort toggle). */
    public final Map<String, List<String>> ownerOfKind;

    // ---- integrations -------------------------------------------------
    /** Master switch for the integration module (all integrations off when false). */
    public final boolean integrationModuleEnabled;
    public final boolean furnaceIntegration;
    public final boolean ae2Integration;
    public final boolean ic2Integration;
    public final boolean ieIntegration;
    public final boolean chestIntegration;
    public final boolean enderIOIntegration;
    /**
     * Forestry machines: carpenter grid-recipe outputs + squeezer container-recipe remnants
     * (non-destructive, in place — never the crate registration, never fluid outputs).
     */
    public final boolean forestryIntegration;
    /** Crafting table recipe output rewrite (M5). */
    public final boolean craftingIntegration;
    public final boolean railcraftIntegration;
    public final boolean thermalExpansionIntegration;
    /**
     * Galacticraft machines: the Ingot Compressor / Electric Ingot Compressor outputs, rewritten
     * non-destructively through the public {@code CompressorRecipes} list (never the circuit
     * fabricator — GC-wafer outputs are GC-specific with no cross-mod equivalents, and never fluid
     * outputs). GC's electric / arc furnace reuse the vanilla {@code FurnaceRecipes} map, so they are
     * already covered by the furnace integration.
     */
    public final boolean galacticraftIntegration;
    /**
     * Storage Drawers compacting drawers: seed {@code StorageDrawers.compRegistry} with the unified
     * model's canonical block/ingot/nugget chains so a compacting drawer honors the canonical entries
     * (e.g. EtF's copper block as the top tier of a drawer seeded with a TF copper ingot) instead of
     * whichever colliding 9-ingot → block recipe the recipe search + mod-matching bias happens to pick.
     */
    public final boolean storageDrawersIntegration;

    private ConfigData(final Builder b) {
        this.keepOneEntry = b.keepOneEntry;
        this.inputReplacement = b.inputReplacement;
        this.unifyDrops = b.unifyDrops;
        this.autoHideInNEI = b.autoHideInNEI;
        this.kindDebugMode = b.kindDebugMode;
        this.autoHideInNEIModBlackSet = Collections.unmodifiableSet(new LinkedHashSet<>(b.autoHideInNEIModBlackSet));
        this.hideInNEIBlackSet = Collections.unmodifiableSet(new LinkedHashSet<>(b.hideInNEIBlackSet));
        this.protectedOreDictionaryNames = Collections
            .unmodifiableSet(new LinkedHashSet<>(b.protectedOreDictionaryNames));
        this.protectedItemNames = Collections.unmodifiableSet(new LinkedHashSet<>(b.protectedItemNames));
        this.canonicalItemNames = Collections.unmodifiableSet(new LinkedHashSet<>(b.canonicalItemNames));

        this.metalsToUnify = Collections.unmodifiableSet(new LinkedHashSet<>(b.metalsToUnify));
        this.childrenOfMetals = Collections.unmodifiableSet(new LinkedHashSet<>(b.childrenOfMetals));
        this.resourceBlackList = Collections.unmodifiableList(new ArrayList<>(b.resourceBlackList));
        this.ownerPriorities = Collections.unmodifiableList(new ArrayList<>(b.ownerPriorities));
        this.ownerOfKind = copyOwnerOfKind(b.ownerOfKind);

        this.integrationModuleEnabled = b.integrationModuleEnabled;
        this.furnaceIntegration = b.furnaceIntegration;
        this.ae2Integration = b.ae2Integration;
        this.ic2Integration = b.ic2Integration;
        this.ieIntegration = b.ieIntegration;
        this.chestIntegration = b.chestIntegration;
        this.enderIOIntegration = b.enderIOIntegration;
        this.forestryIntegration = b.forestryIntegration;
        this.craftingIntegration = b.craftingIntegration;
        this.railcraftIntegration = b.railcraftIntegration;
        this.thermalExpansionIntegration = b.thermalExpansionIntegration;
        this.galacticraftIntegration = b.galacticraftIntegration;
        this.storageDrawersIntegration = b.storageDrawersIntegration;
    }

    private static Map<String, List<String>> copyOwnerOfKind(final Map<String, List<String>> source) {
        final Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((kind, owners) -> copy.put(kind, Collections.unmodifiableList(new ArrayList<>(owners))));
        return Collections.unmodifiableMap(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link ConfigData}. Every field has a safe default matching the standard
     * preset where sensible, so tests and callers can override just the fields they care about.
     */
    public static final class Builder {

        private boolean keepOneEntry = false;
        private boolean inputReplacement = false;
        private boolean unifyDrops = true;
        private boolean autoHideInNEI = true;
        private boolean kindDebugMode = false;
        private final Set<String> autoHideInNEIModBlackSet = new LinkedHashSet<>();
        private final Set<String> hideInNEIBlackSet = new LinkedHashSet<>();
        private final Set<String> protectedOreDictionaryNames = new LinkedHashSet<>();
        private final Set<String> protectedItemNames = new LinkedHashSet<>();
        private final Set<String> canonicalItemNames = new LinkedHashSet<>();

        private final Set<String> metalsToUnify = new LinkedHashSet<>();
        private final Set<String> childrenOfMetals = new LinkedHashSet<>();
        private final List<String> resourceBlackList = new ArrayList<>();
        private final List<String> ownerPriorities = new ArrayList<>();
        private final Map<String, List<String>> ownerOfKind = new LinkedHashMap<>();

        private boolean integrationModuleEnabled = true;
        private boolean furnaceIntegration = true;
        private boolean ae2Integration = true;
        private boolean ic2Integration = true;
        private boolean ieIntegration = true;
        private boolean chestIntegration = true;
        private boolean enderIOIntegration = true;
        private boolean forestryIntegration = true;
        private boolean craftingIntegration = true;
        private boolean railcraftIntegration = true;
        private boolean thermalExpansionIntegration = true;
        private boolean galacticraftIntegration = true;
        private boolean storageDrawersIntegration = true;

        public Builder keepOneEntry(final boolean value) {
            this.keepOneEntry = value;
            return this;
        }

        public Builder inputReplacement(final boolean value) {
            this.inputReplacement = value;
            return this;
        }

        public Builder unifyDrops(final boolean value) {
            this.unifyDrops = value;
            return this;
        }

        public Builder autoHideInNEI(final boolean value) {
            this.autoHideInNEI = value;
            return this;
        }

        public Builder kindDebugMode(final boolean value) {
            this.kindDebugMode = value;
            return this;
        }

        public Builder autoHideInNEIModBlackSet(final Set<String> value) {
            this.autoHideInNEIModBlackSet.clear();
            this.autoHideInNEIModBlackSet.addAll(value);
            return this;
        }

        public Builder hideInNEIBlackSet(final Set<String> value) {
            this.hideInNEIBlackSet.clear();
            this.hideInNEIBlackSet.addAll(value);
            return this;
        }

        public Builder protectedOreDictionaryNames(final Set<String> value) {
            this.protectedOreDictionaryNames.clear();
            this.protectedOreDictionaryNames.addAll(value);
            return this;
        }

        public Builder protectedItemNames(final Set<String> value) {
            this.protectedItemNames.clear();
            this.protectedItemNames.addAll(value);
            return this;
        }

        public Builder canonicalItemNames(final Set<String> value) {
            this.canonicalItemNames.clear();
            this.canonicalItemNames.addAll(value);
            return this;
        }

        public Builder metalsToUnify(final Set<String> value) {
            this.metalsToUnify.clear();
            this.metalsToUnify.addAll(value);
            return this;
        }

        public Builder childrenOfMetals(final Set<String> value) {
            this.childrenOfMetals.clear();
            this.childrenOfMetals.addAll(value);
            return this;
        }

        public Builder resourceBlackList(final List<String> value) {
            this.resourceBlackList.clear();
            this.resourceBlackList.addAll(value);
            return this;
        }

        public Builder ownerPriorities(final List<String> value) {
            this.ownerPriorities.clear();
            this.ownerPriorities.addAll(value);
            return this;
        }

        public Builder ownerOfKind(final Map<String, List<String>> value) {
            this.ownerOfKind.clear();
            value.forEach((kind, owners) -> this.ownerOfKind.put(kind, new ArrayList<>(owners)));
            return this;
        }

        public Builder integrationModuleEnabled(final boolean value) {
            this.integrationModuleEnabled = value;
            return this;
        }

        public Builder furnaceIntegration(final boolean value) {
            this.furnaceIntegration = value;
            return this;
        }

        public Builder ae2Integration(final boolean value) {
            this.ae2Integration = value;
            return this;
        }

        public Builder ic2Integration(final boolean value) {
            this.ic2Integration = value;
            return this;
        }

        public Builder ieIntegration(final boolean value) {
            this.ieIntegration = value;
            return this;
        }

        public Builder chestIntegration(final boolean value) {
            this.chestIntegration = value;
            return this;
        }

        public Builder enderIOIntegration(final boolean value) {
            this.enderIOIntegration = value;
            return this;
        }

        public Builder forestryIntegration(final boolean value) {
            this.forestryIntegration = value;
            return this;
        }

        public Builder craftingIntegration(final boolean value) {
            this.craftingIntegration = value;
            return this;
        }

        public Builder railcraftIntegration(final boolean value) {
            this.railcraftIntegration = value;
            return this;
        }

        public Builder thermalExpansionIntegration(final boolean value) {
            this.thermalExpansionIntegration = value;
            return this;
        }

        public Builder galacticraftIntegration(final boolean value) {
            this.galacticraftIntegration = value;
            return this;
        }

        public Builder storageDrawersIntegration(final boolean value) {
            this.storageDrawersIntegration = value;
            return this;
        }

        public ConfigData build() {
            return new ConfigData(this);
        }
    }

    @Override
    public String toString() {
        return "ConfigData{keepOneEntry=" + keepOneEntry
            + ", unifyDrops="
            + unifyDrops
            + ", ownerPriorities="
            + ownerPriorities
            + ", ownerOfKind="
            + ownerOfKind
            + ", furnace="
            + furnaceIntegration
            + ", ae2="
            + ae2Integration
            + ", ic2="
            + ic2Integration
            + ", ie="
            + ieIntegration
            + ", chest="
            + chestIntegration
            + ", enderIO="
            + enderIOIntegration
            + ", forestry="
            + forestryIntegration
            + ", crafting="
            + craftingIntegration
            + ", railcraft="
            + railcraftIntegration
            + ", thermalExpansion="
            + thermalExpansionIntegration
            + ", galacticraft="
            + galacticraftIntegration
            + ", storageDrawers="
            + storageDrawersIntegration
            + '}';
    }
}
