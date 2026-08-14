package com.mrfuzzihead.unidict.pure.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for the pure {@link ReportEntry}/{@link ReportFormatter} — the BB-1 transparency report
 * model (docs/PLAN.md §BB-1). Zero {@code net.minecraft*} imports; operates on plain strings so it
 * runs on a bare JVM. The MC-resolving glue ({@code UniDictReport}) is separate and T3-relevant only.
 */
class ReportFormatterTest {

    @Test
    void entrySortsVariantsAndOwners() {
        final ReportEntry entry = ReportEntry.from(
            "ingotIron",
            "ThermalFoundation:ThermalFoundation:ingotIron",
            Arrays.asList("minecraft:iron_ingot", "IC2:ingotIron", "ThermalFoundation:ThermalFoundation:ingotIron"));
        // Variants sorted; main not singled out (it is just the first of the sorted variants here).
        assertEquals(3, entry.variants.size());
        assertEquals("IC2:ingotIron", entry.variants.get(0));
        assertEquals("ThermalFoundation:ThermalFoundation:ingotIron", entry.variants.get(1));
        assertEquals("minecraft:iron_ingot", entry.variants.get(2));
        // Owners derived from descriptor namespaces, deduped + sorted.
        assertEquals(Arrays.asList("IC2", "ThermalFoundation", "minecraft"), entry.owners);
    }

    @Test
    void resourceLineIsStable() {
        final ReportEntry entry = ReportEntry
            .from("ingotIron", "minecraft:iron_ingot", Arrays.asList("TC:ingotIron", "minecraft:iron_ingot"));
        assertEquals(
            "report resource=ingotIron main=minecraft:iron_ingot variants=2 owners=TC,minecraft",
            ReportFormatter.resourceLine(entry));
    }

    @Test
    void rewriteLineAndSummaryLineAreStable() {
        assertEquals("report rewrite=ic2 macerator rewritten=16", ReportFormatter.rewriteLine("ic2", "macerator", 16));
        assertEquals("report summary=resources=3 rewrites=2", ReportFormatter.summaryLine(3, 2));
    }

    @Test
    void fullReportIsSortedAndEndsWithSummary() {
        final ReportEntry a = ReportEntry
            .from("ingotGold", "minecraft:gold_ingot", Arrays.asList("minecraft:gold_ingot"));
        final ReportEntry b = ReportEntry
            .from("ingotIron", "minecraft:iron_ingot", Arrays.asList("minecraft:iron_ingot"));
        final List<RewriteRecord> rewrites = Arrays
            .asList(new RewriteRecord("ic2", "macerator", 1), new RewriteRecord("ae2", "grinder", 2));
        final List<String> lines = ReportFormatter.reportLines(Arrays.asList(b, a), rewrites);

        assertEquals(5, lines.size());
        // Resources sorted before rewrites; rewrites sorted by (source, machine); summary last.
        assertTrue(
            lines.get(0)
                .startsWith("report resource=ingotGold"));
        assertTrue(
            lines.get(1)
                .startsWith("report resource=ingotIron"));
        assertTrue(
            lines.get(2)
                .equals("report rewrite=ae2 grinder rewritten=2"));
        assertTrue(
            lines.get(3)
                .equals("report rewrite=ic2 macerator rewritten=1"));
        assertTrue(
            lines.get(4)
                .equals("report summary=resources=2 rewrites=2"));
    }
}
