package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.pure.config.ConfigData;

/**
 * T1 tests for {@link SpecificKindItemStackComparator} — specifically the BUG-12 guarantee that a cached
 * comparator follows the <b>current</b> config instead of a captured snapshot (docs/CODE_REVIEW.md BUG-12).
 *
 * <p>
 * The production path injects {@code Config::get} and {@code Util::getModName}; these tests inject a mutable
 * config holder and a registry-free owner map, so no Minecraft statics (or config file) are involved.
 */
class SpecificKindItemStackComparatorTest {

    private static ConfigData config(final List<String> priorities) {
        return ConfigData.builder()
            .ownerPriorities(priorities)
            .build();
    }

    private static ConfigData configWithKind(final List<String> priorities, final List<String> ingotOrder) {
        final Map<String, List<String>> perKind = new LinkedHashMap<>();
        perKind.put("ingot", ingotOrder);
        return ConfigData.builder()
            .ownerPriorities(priorities)
            .ownerOfKind(perKind)
            .build();
    }

    @Test
    void followsTheLiveConfigAcrossAReload() {
        final Item item = new Item();
        final ItemStack thermal = new ItemStack(item, 1, 0);
        final ItemStack vanilla = new ItemStack(item, 1, 1);
        final Map<ItemStack, String> owners = new IdentityHashMap<>();
        owners.put(thermal, "ThermalFoundation");
        owners.put(vanilla, "minecraft");

        final ConfigData[] live = { config(Arrays.asList("ThermalFoundation", "minecraft")) };
        final SpecificKindItemStackComparator comparator = SpecificKindItemStackComparator
            .forTesting("ingot", () -> live[0], owners::get);

        assertTrue(comparator.compare(thermal, vanilla) < 0, "TF is first in the initial order");

        // Simulate Config.load(...) replacing the ConfigData: the SAME comparator instance must follow it.
        live[0] = config(Arrays.asList("minecraft", "ThermalFoundation"));
        assertTrue(comparator.compare(thermal, vanilla) > 0, "the cached comparator followed the new order");
    }

    @Test
    void perKindOverrideAppliesAndIsReadLive() {
        final Item item = new Item();
        final ItemStack thermal = new ItemStack(item, 1, 0);
        final ItemStack vanilla = new ItemStack(item, 1, 1);
        final Map<ItemStack, String> owners = new IdentityHashMap<>();
        owners.put(thermal, "ThermalFoundation");
        owners.put(vanilla, "minecraft");

        // Global order puts TF first; the per-kind override puts vanilla first.
        final ConfigData[] live = { configWithKind(
            Arrays.asList("ThermalFoundation", "minecraft"),
            Arrays.asList("minecraft", "ThermalFoundation")) };
        final SpecificKindItemStackComparator comparator = SpecificKindItemStackComparator
            .forTesting("ingot", () -> live[0], owners::get);

        assertTrue(comparator.compare(vanilla, thermal) < 0, "per-kind override wins over the global order");

        live[0] = config(Arrays.asList("ThermalFoundation", "minecraft"));
        assertTrue(comparator.compare(vanilla, thermal) > 0, "dropping the override falls back to the global order");
    }

    @Test
    void unlistedOwnersTiebreakDeterministically() {
        final Item item = new Item();
        final ItemStack aaa = new ItemStack(item, 1, 0);
        final ItemStack zzz = new ItemStack(item, 1, 1);
        final Map<ItemStack, String> owners = new IdentityHashMap<>();
        owners.put(aaa, "aaaMod");
        owners.put(zzz, "zzzMod");

        final SpecificKindItemStackComparator comparator = SpecificKindItemStackComparator
            .forTesting("ingot", () -> config(Collections.emptyList()), owners::get);

        assertTrue(comparator.compare(aaa, zzz) < 0, "lexical tiebreak");
        assertTrue(comparator.compare(zzz, aaa) > 0);
        assertEquals(0, comparator.compare(aaa, aaa));
    }

    @Test
    void nullOwnerIsSafe() {
        final Item item = new Item();
        final ItemStack unknown = new ItemStack(item, 1, 0);
        final ItemStack known = new ItemStack(item, 1, 1);
        final Map<ItemStack, String> owners = new IdentityHashMap<>();
        owners.put(known, "minecraft");

        final SpecificKindItemStackComparator comparator = SpecificKindItemStackComparator
            .forTesting("ingot", () -> config(Arrays.asList("minecraft")), owners::get);

        // Unlisted/null sorts after a listed owner; the tiebreak itself is null-safe.
        assertTrue(comparator.compare(known, unknown) < 0);
        assertTrue(comparator.compare(unknown, known) > 0);
        assertEquals(0, comparator.compare(unknown, unknown));
    }
}
