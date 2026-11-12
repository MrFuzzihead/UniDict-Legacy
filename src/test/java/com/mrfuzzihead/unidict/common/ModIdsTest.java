package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Regression guard for the exact FML mod ids. {@code Loader.isModLoaded} is case-sensitive in 1.7.10,
 * so this is an observable behaviour test, not a cosmetic string test: an id mismatch disables the
 * whole integration.
 */
class ModIdsTest {

    @Test
    void immersiveEngineeringUsesTheCapitalisedFmlId() {
        // ImmersiveEngineering 0.7.7 declares @Mod(modid = "ImmersiveEngineering") and mcmod.info
        // "modid": "ImmersiveEngineering". The integration used to check "immersiveengineering", so
        // Loader.isModLoaded returned false even when IE was installed.
        assertEquals("ImmersiveEngineering", ModIds.IMMERSIVE_ENGINEERING);
    }

    @Test
    void allOptionalModIdsMatchTheirDeclaredFmlIds() {
        assertEquals("NotEnoughItems", ModIds.NOT_ENOUGH_ITEMS);
        assertEquals("IC2", ModIds.IC2);
        assertEquals("appliedenergistics2", ModIds.APPLIED_ENERGISTICS);
        assertEquals("EnderIO", ModIds.ENDER_IO);
        assertEquals("Railcraft", ModIds.RAILCRAFT);
        assertEquals("ThermalExpansion", ModIds.THERMAL_EXPANSION);
        assertEquals("Forestry", ModIds.FORESTRY);
        assertEquals("StorageDrawers", ModIds.STORAGE_DRAWERS);
        assertEquals("TConstruct", ModIds.TINKERS_CONSTRUCT);
        assertEquals("GalacticraftCore", ModIds.GALACTICRAFT_CORE);
    }

    @Test
    void optionalModIdsAreDistinct() {
        final Set<String> ids = new HashSet<>();
        ids.add(ModIds.NOT_ENOUGH_ITEMS);
        ids.add(ModIds.IC2);
        ids.add(ModIds.APPLIED_ENERGISTICS);
        ids.add(ModIds.IMMERSIVE_ENGINEERING);
        ids.add(ModIds.ENDER_IO);
        ids.add(ModIds.RAILCRAFT);
        ids.add(ModIds.THERMAL_EXPANSION);
        ids.add(ModIds.FORESTRY);
        ids.add(ModIds.STORAGE_DRAWERS);
        ids.add(ModIds.TINKERS_CONSTRUCT);
        ids.add(ModIds.GALACTICRAFT_CORE);
        assertEquals(11, ids.size());
        // The exact regression: IE's FML id is not the all-lowercase spelling the integration used.
        assertNotEquals("immersiveengineering", ModIds.IMMERSIVE_ENGINEERING);
    }
}
