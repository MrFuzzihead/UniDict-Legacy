package com.mrfuzzihead.unidict.report;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.common.Util;
import com.mrfuzzihead.unidict.pure.report.ReportEntry;
import com.mrfuzzihead.unidict.pure.report.ReportFormatter;
import com.mrfuzzihead.unidict.pure.report.RewriteRecord;
import com.mrfuzzihead.unidict.resource.Resource;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.resource.UniResourceContainer;

import cpw.mods.fml.common.registry.GameData;

/**
 * Builds the BB-1 transparency report (docs/PLAN.md §BB-1): per unified resource the canonical
 * (main) entry, every variant and owner mod, plus a per-integration rewrite summary — the data
 * behind both the {@code /unidict report} command and the {@code [unidict-verify] report} lines.
 *
 * <p>
 * All decision/formatting logic lives in the pure {@link ReportEntry}/{@link ReportFormatter}
 * (T1-tested); this class is thin MC glue that only resolves {@link ItemStack}s to qualified names
 * via the item registry (MC statics — T3-only glue, docs/TestPlan.md rule 7).
 */
public final class UniDictReport {

    private UniDictReport() {}

    /** Builds every resource entry currently in the model (order follows {@code Resource} iteration). */
    public static List<ReportEntry> entries(final ResourceHandler resourceHandler) {
        final List<ReportEntry> reportEntries = new ArrayList<>();
        if (resourceHandler == null || resourceHandler.resources == null) return reportEntries;
        for (final Resource<UniResourceContainer> resource : resourceHandler.resources)
            for (final UniResourceContainer container : resource.getChildrenCollection()) reportEntries.add(
                ReportEntry
                    .from(container.name, describe(container.getMainEntry()), describeAll(container.getEntries())));
        return reportEntries;
    }

    /** @return a defensive snapshot of every recorded rewrite. */
    public static List<RewriteRecord> rewriteRecords() {
        return RewriteJournal.snapshot();
    }

    /** The full report as stable, diffable lines (rendered purely by {@link ReportFormatter}). */
    public static List<String> lines(final ResourceHandler resourceHandler) {
        return ReportFormatter.reportLines(entries(resourceHandler), RewriteJournal.snapshot());
    }

    private static String describe(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "none";
        final String registryName = GameData.getItemRegistry()
            .getNameForObject(stack.getItem());
        final String owner = Util.getModName(stack);
        // The registry name already carries its mod prefix; avoid a redundant "mod:mod:name".
        return registryName.startsWith(owner + ":") ? registryName : owner + ":" + registryName;
    }

    /** Describes each entry, de-duplicating identical qualified names for a stable variant list. */
    private static List<String> describeAll(@Nonnull final List<ItemStack> entries) {
        final Set<String> seen = new LinkedHashSet<>();
        final List<String> descriptors = new ArrayList<>();
        for (final ItemStack entry : entries) {
            final String descriptor = describe(entry);
            if (seen.add(descriptor)) descriptors.add(descriptor);
        }
        return descriptors;
    }
}
