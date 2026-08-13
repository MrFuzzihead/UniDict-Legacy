package com.mrfuzzihead.unidict.pure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for {@link OwnerOrder} — the pure owner-priority model behind the entry comparators.
 * Per-kind override wins when present; otherwise the global priorities apply; unlisted mods sort
 * last via the {@link OwnerOrder#NOT_LISTED} sentinel.
 */
class OwnerOrderTest {

    private static ConfigData config(final Map<String, java.util.List<String>> ownerOfKind) {
        return ConfigData.builder()
            .ownerPriorities(Arrays.asList("ThermalFoundation", "minecraft", "IC2"))
            .ownerOfKind(ownerOfKind)
            .build();
    }

    @Test
    void globalPrioritiesApplyWhenNoKindOverride() {
        final ConfigData c = config(new LinkedHashMap<>());
        assertEquals(0, OwnerOrder.indexOf(c, "INGOT", "ThermalFoundation"));
        assertEquals(1, OwnerOrder.indexOf(c, "INGOT", "minecraft"));
        assertEquals(2, OwnerOrder.indexOf(c, "INGOT", "IC2"));
        assertEquals(OwnerOrder.NOT_LISTED, OwnerOrder.indexOf(c, "INGOT", "UnknownMod"));
    }

    @Test
    void perKindOverrideWinsOverGlobal() {
        final Map<String, java.util.List<String>> perKind = new LinkedHashMap<>();
        perKind.put("INGOT", Arrays.asList("minecraft", "IC2"));
        final ConfigData c = config(perKind);
        assertEquals(0, OwnerOrder.indexOf(c, "INGOT", "minecraft"));
        assertEquals(1, OwnerOrder.indexOf(c, "INGOT", "IC2"));
        // ThermalFoundation is first globally but NOT in the INGOT override -> sorts last-ish.
        assertEquals(OwnerOrder.NOT_LISTED, OwnerOrder.indexOf(c, "INGOT", "ThermalFoundation"));
        // A kind with no override still uses the global list.
        assertEquals(0, OwnerOrder.indexOf(c, "DUST", "ThermalFoundation"));
    }

    @Test
    void compareOrdersByEffectiveOwnerIndex() {
        final ConfigData c = config(new LinkedHashMap<>());
        assertTrue(OwnerOrder.compare(c, "INGOT", "minecraft", "ThermalFoundation") > 0); // minecraft after TF
        assertTrue(OwnerOrder.compare(c, "INGOT", "ThermalFoundation", "minecraft") < 0);
        assertEquals(0, OwnerOrder.compare(c, "INGOT", "minecraft", "minecraft"));
    }

    @Test
    void globalIndexOfUsesTheGlobalOrderIgnoringKind() {
        final Map<String, java.util.List<String>> perKind = new LinkedHashMap<>();
        perKind.put("INGOT", Arrays.asList("minecraft", "IC2"));
        final ConfigData c = config(perKind);
        // globalIndexOf ignores per-kind overrides entirely.
        assertEquals(0, OwnerOrder.globalIndexOf(c, "ThermalFoundation"));
        assertEquals(2, OwnerOrder.globalIndexOf(c, "IC2"));
    }

    @Test
    void emptyOwnersFallBackToGlobal() {
        final Map<String, java.util.List<String>> perKind = new LinkedHashMap<>();
        perKind.put("INGOT", Collections.emptyList()); // empty override -> fallback
        final ConfigData c = config(perKind);
        assertEquals(Arrays.asList("ThermalFoundation", "minecraft", "IC2"), OwnerOrder.orderedOwners(c, "INGOT"));
        assertEquals(1, OwnerOrder.indexOf(c, "INGOT", "minecraft"));
    }
}
