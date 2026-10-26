package com.mrfuzzihead.unidict.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.pure.report.RewriteRecord;

/**
 * T1 test for {@link RewriteJournal} — the runtime capture behind {@code /unidict report} and the
 * {@code report rewrite=…} verify lines. Zero {@code net.minecraft*} imports.
 *
 * <p>
 * Pins the accumulation contract that the canonical-resolution fix requires: an idempotent re-run
 * (crafting at {@code LOAD_COMPLETE} and again at server start) must add its newly-rewritten entries to
 * the existing total instead of erasing the earlier pass, while never duplicating a
 * {@code source}/{@code machine} line (docs/CODE_REVIEW.md BUG-6).
 */
class RewriteJournalTest {

    @AfterEach
    void clear() {
        RewriteJournal.clear();
    }

    @Test
    void startsEmpty() {
        assertTrue(RewriteJournal.isEmpty());
        assertTrue(
            RewriteJournal.snapshot()
                .isEmpty());
    }

    @Test
    void reRunsAccumulateInsteadOfDuplicatingOrReplacing() {
        RewriteJournal.record("crafting", "table", 187);
        RewriteJournal.record("ic2", "macerator", 16);

        // A second pass (server start) catches late-registered recipes: only the delta is reported.
        RewriteJournal.record("crafting", "table", 13);
        // A third pass with nothing new must not change the total.
        RewriteJournal.record("crafting", "table", 0);

        final List<RewriteRecord> records = RewriteJournal.snapshot();
        assertEquals(2, records.size(), "one line per source/machine, never a duplicate");
        assertEquals(200, count(records, "crafting", "table"), "passes add up to the authoritative total");
        assertEquals(16, count(records, "ic2", "macerator"));
    }

    @Test
    void firstRecordingOfANewMachineAppends() {
        RewriteJournal.record("te", "redstoneFurnace", 5);
        RewriteJournal.record("te", "pulverizer", 7);

        assertEquals(
            java.util.Arrays.asList("pulverizer", "redstoneFurnace"),
            RewriteJournal.snapshot()
                .stream()
                .map(r -> r.machine)
                .sorted()
                .collect(java.util.stream.Collectors.toList()));
        assertFalse(RewriteJournal.isEmpty());
    }

    @Test
    void snapshotIsDefensive() {
        RewriteJournal.record("furnace", "furnace", 3);
        final List<RewriteRecord> snapshot = RewriteJournal.snapshot();
        snapshot.clear();
        assertEquals(
            1,
            RewriteJournal.snapshot()
                .size(),
            "callers cannot clear the journal through a snapshot");
    }

    private static int count(final List<RewriteRecord> records, final String source, final String machine) {
        return records.stream()
            .filter(r -> source.equals(r.source) && machine.equals(r.machine))
            .mapToInt(r -> r.count)
            .sum();
    }
}
