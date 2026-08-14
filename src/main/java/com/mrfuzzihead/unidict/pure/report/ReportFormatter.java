package com.mrfuzzihead.unidict.pure.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure line formatting for the BB-1 transparency report (docs/PLAN.md §BB-1). Formatting lives here
 * — free of {@code net.minecraft*} — so the exact report lines are stable and diffable run-to-run and
 * are T1-testable without a game instance (docs/TestPlan.md rule 2).
 *
 * <p>
 * Every produced line is a single, self-contained, sorted/unordered key-value string so grepping
 * {@code report} in the {@code [unidict-verify]} output exposes exactly one fact per line.
 */
public final class ReportFormatter {

    private ReportFormatter() {}

    /** One stable, diffable line per unified resource (canonical + variant count + owner mods). */
    public static String resourceLine(final ReportEntry entry) {
        return "report resource=" + entry.resourceName
            + " main="
            + entry.main
            + " variants="
            + entry.variants.size()
            + " owners="
            + String.join(",", entry.owners);
    }

    /** One stable line per recorded integration rewrite (every kept rewrite gets a matching line). */
    public static String rewriteLine(final String source, final String machine, final int count) {
        return "report rewrite=" + source + " " + machine + " rewritten=" + count;
    }

    /** One summary line covering the whole report. */
    public static String summaryLine(final int resources, final int rewrites) {
        return "report summary=resources=" + resources + " rewrites=" + rewrites;
    }

    /** Convenience: renders a full report as one ordered list — resource body, then rewrite lines, then summary. */
    public static List<String> reportLines(final List<ReportEntry> entries, final List<RewriteRecord> rewriteRecords) {
        final List<String> lines = new ArrayList<>();
        final List<String> resources = new ArrayList<>();
        for (final ReportEntry entry : entries) resources.add(resourceLine(entry));
        Collections.sort(resources);
        lines.addAll(resources);
        final List<String> rewrites = new ArrayList<>();
        final List<RewriteRecord> sorted = new ArrayList<>(rewriteRecords);
        Collections.sort(sorted);
        for (final RewriteRecord record : sorted)
            rewrites.add(rewriteLine(record.source, record.machine, record.count));
        lines.addAll(rewrites);
        lines.add(summaryLine(resources.size(), rewrites.size()));
        return lines;
    }
}
