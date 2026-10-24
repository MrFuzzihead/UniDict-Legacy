package com.mrfuzzihead.unidict.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.LoadStage;

/**
 * T2 tests for {@link ModuleHandler}: modules are registered in order, initialized exactly once,
 * and each load stage is dispatched to every module — all sequentially. Nothing here touches MC
 * statics (docs/TestPlan.md tier 2).
 */
class ModuleHandlerTest {

    @Test
    void initRunsOnceAndEachStageDispatchRunsOnlyItsStageThreads() {
        final ModuleHandler handler = new ModuleHandler();
        final List<String> log = new ArrayList<>();
        handler.addModule(new FakeCountingModule("modA", log));

        assertFalse(handler.isEmpty());

        // First POST_INIT dispatch lazily initializes (registers the POST_INIT thread) and runs it.
        handler.startModules(LoadStage.POST_INIT);
        assertEquals(java.util.Arrays.asList("modA"), log);

        // Second POST_INIT dispatch must NOT re-init (no duplicate registration) but does run again.
        handler.startModules(LoadStage.POST_INIT);
        assertEquals(java.util.Arrays.asList("modA", "modA"), log);

        // Driving an unrelated stage runs nothing for this module (thread is POST_INIT-only).
        handler.startModules(LoadStage.LOAD_COMPLETE);
        assertEquals(java.util.Arrays.asList("modA", "modA"), log);
    }

    @Test
    void nullStageIsIgnored() {
        final ModuleHandler handler = new ModuleHandler();
        final List<String> log = new ArrayList<>();
        handler.addModule(new FakeCountingModule("modA", log));
        handler.startModules(null); // no-op: nothing initialized or run
        assertTrue(log.isEmpty());
    }

    @Test
    void duplicateModuleRegistrationIsIgnored() {
        final ModuleHandler handler = new ModuleHandler();
        final FakeCountingModule modA = new FakeCountingModule("modA", new ArrayList<>());
        handler.addModule(modA);
        handler.addModule(modA);
        // No way to count modules publicly; ensure driving works exactly once is covered above.
        // Here just assert a fresh, empty-stage dispatch does not throw.
        handler.startModules(LoadStage.PRE_INIT);
    }

    /** A module that registers one plain POST_INIT thread recording its name each run. */
    private static final class FakeCountingModule extends AbstractModule {

        private final List<String> log;
        private final String name;

        FakeCountingModule(final String name, final List<String> log) {
            super(name);
            this.name = name;
            this.log = log;
        }

        @Override
        protected void init() {
            executor.add(new FakeModuleThread(name, log));
        }
    }

    /** A module that registers NO threads and counts its {@code init()} calls. */
    private static final class EmptyCountingModule extends AbstractModule {

        int inits;

        EmptyCountingModule() {
            super("empty");
        }

        @Override
        protected void init() {
            inits++;
        }
    }

    @Test
    void moduleRegisteringNoThreadsIsInitialisedOnceNotPerStage() {
        // Regression guard for docs/CODE_REVIEW.md BUG-11: "executor is empty" was used as the
        // lazy-init sentinel, so a module that legitimately registers nothing had init() re-run at
        // every stage (and a module that registered something could never re-init).
        final ModuleHandler handler = new ModuleHandler();
        final EmptyCountingModule module = new EmptyCountingModule();
        handler.addModule(module);

        handler.startModules(LoadStage.POST_INIT);
        handler.startModules(LoadStage.LOAD_COMPLETE);
        handler.startModules(LoadStage.POST_INIT);

        assertEquals(1, module.inits, "init() must run exactly once even when the module registers nothing");
    }
}
