package com.mrfuzzihead.unidict.pure.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure paging for the {@code /unidict report} chat output. The full report is deliberately long, so
 * sending it one chat packet per line can flood the client; the command slices the already-filtered
 * lines here instead. Kept MC-free so the bounds behaviour is T1-testable.
 */
public final class ReportPager {

    private ReportPager() {}

    /** @return the number of pages needed for {@code lineCount} at {@code pageSize}; 0 for an empty report. */
    public static int pageCount(final int lineCount, final int pageSize) {
        if (lineCount <= 0 || pageSize <= 0) return 0;
        return (lineCount + pageSize - 1) / pageSize;
    }

    /**
     * @return the lines on the requested 1-based page, clamped to the available range; empty when the
     *         report or page size is empty.
     */
    public static List<String> page(final List<String> lines, final int page, final int pageSize) {
        if (lines == null || lines.isEmpty() || pageSize <= 0) return Collections.emptyList();
        final int pages = pageCount(lines.size(), pageSize);
        final int safePage = Math.min(Math.max(page, 1), pages);
        final int from = (safePage - 1) * pageSize;
        final int to = Math.min(from + pageSize, lines.size());
        return new ArrayList<>(lines.subList(from, to));
    }
}
