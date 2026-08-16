package com.mrfuzzihead.unidict.report;

import java.util.ArrayList;
import java.util.List;

import com.mrfuzzihead.unidict.pure.report.RewriteRecord;

/**
 * Runtime capture of every kept integration's rewrite counts for the BB-1 transparency report
 * (docs/PLAN.md §BB-1). Integrations record their machine rewrite totals here as they run (the
 * {@code rewriteOutputs}/{@code rewriteList} counts each integration already computes), independent
 * of the dev-only verify harness, so the user-facing {@code /unidict report} command works in a
 * normal build too.
 *
 * <p>
 * Recording order is integration-execution order (deterministic — execution is sequential, M2); the
 * report renders records sorted by {@link RewriteRecord#compareTo} for run-to-run diffability.
 */
public final class RewriteJournal {

    private static final List<RewriteRecord> records = new ArrayList<>();

    private RewriteJournal() {}

    /**
     * Records one machine rewrite total. Ordering is deterministic (integration run order).
     * Idempotent for re-runs: a re-record of the same {@code source}/{@code machine} (e.g. the crafting
     * rewrite at LOAD_COMPLETE and again at server start) updates the last matching entry instead of
     * appending a duplicate, so the report always shows the authoritative final count once.
     */
    public static void record(final String source, final String machine, final int count) {
        for (int i = records.size() - 1; i >= 0; i--) {
            final RewriteRecord existing = records.get(i);
            if (existing.source.equals(source) && existing.machine.equals(machine)) {
                records.set(i, new RewriteRecord(source, machine, count));
                return;
            }
        }
        records.add(new RewriteRecord(source, machine, count));
    }

    /** @return a defensive snapshot of every recorded rewrite, in recording order. */
    public static List<RewriteRecord> snapshot() {
        return new ArrayList<>(records);
    }

    /** @return {@code true} when nothing has been recorded. */
    public static boolean isEmpty() {
        return records.isEmpty();
    }

    /** Test-only reset. */
    public static void clear() {
        records.clear();
    }
}
