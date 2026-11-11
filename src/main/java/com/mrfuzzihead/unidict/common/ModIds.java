package com.mrfuzzihead.unidict.common;

/**
 * The exact FML {@code @Mod} ids used by the optional integrations.
 *
 * <p>
 * Forge 1.7.10 {@code Loader.isModLoaded(String)} is a plain case-sensitive map lookup
 * ({@code namedMods.containsKey(modId)}), so a single wrong character silently disables an entire
 * integration. Keep every id here, use these constants at the call sites, and let
 * {@code ModIdsTest} pin the exact spellings.
 */
public final class ModIds {

    public static final String NOT_ENOUGH_ITEMS = "NotEnoughItems";
    public static final String IC2 = "IC2";
    public static final String APPLIED_ENERGISTICS = "appliedenergistics2";
    public static final String IMMERSIVE_ENGINEERING = "ImmersiveEngineering";
    public static final String ENDER_IO = "EnderIO";
    public static final String RAILCRAFT = "Railcraft";
    public static final String THERMAL_EXPANSION = "ThermalExpansion";
    public static final String FORESTRY = "Forestry";
    public static final String STORAGE_DRAWERS = "StorageDrawers";
    public static final String TINKERS_CONSTRUCT = "TConstruct";
    public static final String GALACTICRAFT_CORE = "GalacticraftCore";

    private ModIds() {}
}
