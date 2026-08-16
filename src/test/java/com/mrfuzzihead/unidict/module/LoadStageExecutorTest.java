package com.mrfuzzihead.unidict.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.LoadStage;

/**
 * T2 tests for {@link LoadStageExecutor} — the sequential replacement for upstream's thread pool.
 * Asserts that execution is single-threaded (registration order surfaces in the results), that
 * empty stages produce nothing, that duplicates are rejected, and that {@link SpecifiedLoadStage}
 * routes a thread to the right stage. Zero MC statics are touched (docs/TestPlan.md tier 2).
 */
class LoadStageExecutorTest {

    @Test
    void registrationOrderIsExecutionOrder() {
        final List<String> log = new ArrayList<>();
        final LoadStageExecutor executor = new LoadStageExecutor("TestModule");
        assertTrue(executor.add(new FakeModuleThread("first", log)));
        assertTrue(executor.add(new FakeModuleThread("second", log)));
        assertTrue(executor.add(new FakeModuleThread("third", log)));

        // Plain fakes default to POST_INIT; run there.
        final List<String> results = executor.run(LoadStage.POST_INIT, null);

        assertEquals(java.util.Arrays.asList("first", "second", "third"), log);
        assertEquals(java.util.Arrays.asList("ran first", "ran second", "ran third"), results);
    }

    @Test
    void emptyStagesProduceNoResults() {
        final List<String> log = new ArrayList<>();
        final LoadStageExecutor executor = new LoadStageExecutor("TestModule");

        assertTrue(executor.isEmpty());
        assertTrue(executor.isEmpty(LoadStage.POST_INIT));
        assertTrue(
            executor.run(LoadStage.POST_INIT, null)
                .isEmpty());
        assertTrue(log.isEmpty());
        // Running an empty executor does not change its state.
        assertTrue(executor.isEmpty());
    }

    @Test
    void duplicateAddIsRejected() {
        final List<String> log = new ArrayList<>();
        final LoadStageExecutor executor = new LoadStageExecutor("TestModule");
        final FakeModuleThread thread = new FakeModuleThread("dup", log);

        assertTrue(executor.add(thread));
        assertFalse(executor.add(thread));
        assertEquals(
            1,
            executor.run(LoadStage.POST_INIT, null)
                .size());
    }

    @Test
    void specifiedLoadStageRoutesThreadToDeclaredStage() {
        final List<String> log = new ArrayList<>();
        final LoadStageExecutor executor = new LoadStageExecutor("TestModule");
        executor.add(new FakeLoadCompleteThread("late", log));

        assertTrue(executor.isEmpty(LoadStage.POST_INIT));
        assertFalse(executor.isEmpty(LoadStage.LOAD_COMPLETE));

        // Only runs at its declared stage.
        executor.run(LoadStage.POST_INIT, null);
        assertEquals(0, log.size());
        executor.run(LoadStage.LOAD_COMPLETE, null);
        assertEquals(java.util.Arrays.asList("late"), log);
    }

    @Test
    void stageBooleansReflectRegistration() {
        final List<String> log = new ArrayList<>();
        final LoadStageExecutor executor = new LoadStageExecutor("TestModule");
        executor.add(new FakeModuleThread("job", log));

        assertFalse(executor.isEmpty());
        assertFalse(executor.isEmpty(LoadStage.POST_INIT));
        assertTrue(executor.isEmpty(LoadStage.LOAD_COMPLETE));
    }

    /** A thread pinned to the LOAD_COMPLETE stage via {@link SpecifiedLoadStage}. */
    @SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
    static final class FakeLoadCompleteThread extends AbstractModuleThread {

        private final List<String> executionLog;
        private final String marker;

        FakeLoadCompleteThread(final String name, final List<String> executionLog) {
            super(name, "Module");
            this.executionLog = executionLog;
            this.marker = name;
        }

        @Override
        public String call() {
            executionLog.add(marker);
            return "ran " + marker;
        }
    }
}
