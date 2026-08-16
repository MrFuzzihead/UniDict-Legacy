package com.mrfuzzihead.unidict.pure.report;

import javax.annotation.Nonnull;

/**
 * Pure (Minecraft-free) value describing one recorded integration rewrite for the BB-1 report
 * (docs/PLAN.md §BB-1). Immutable; ordering is deterministic via {@link #compareTo} so the rewrite
 * section of the report is stable and diffable run-to-run.
 */
public final class RewriteRecord implements Comparable<RewriteRecord> {

    /** Integration source id, e.g. {@code furnace}, {@code ic2}, {@code te}. */
    public final String source;

    /** Machine/label within the source, e.g. {@code macerator}, {@code grinder}. */
    public final String machine;

    /** Number of outputs actually rewritten by this record. */
    public final int count;

    public RewriteRecord(@Nonnull final String source, @Nonnull final String machine, final int count) {
        this.source = source;
        this.machine = machine;
        this.count = count;
    }

    @Override
    public int compareTo(final RewriteRecord o) {
        final int bySource = source.compareTo(o.source);
        return bySource != 0 ? bySource : machine.compareTo(o.machine);
    }
}
