package com.mrfuzzihead.unidict.module;

import javax.annotation.Nonnull;

import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;

/**
 * A grouping of {@link AbstractModuleThread}s that belong to one feature area (e.g. the integration
 * module). Ported from {@code wanion.unidict.module.AbstractModule} (WanionCane, MPL-2.0) with the
 * upstream nested {@code Manager} and its reflection-driven instance creation removed: threads are
 * registered explicitly via {@link #executor} and executed sequentially by {@link LoadStageExecutor}.
 *
 * <p>
 * {@link #init()} runs at most once (lazily, before the first stage is executed) to populate the
 * executor; after that each {@link LoadStage} is dispatched to {@link #executor}.
 */
public abstract class AbstractModule {

    protected final LoadStageExecutor executor;

    protected AbstractModule(@Nonnull final String moduleName) {
        this.executor = new LoadStageExecutor(moduleName);
    }

    /** Registers the module's {@link AbstractModuleThread}s; called once, lazily. */
    protected abstract void init();

    final boolean isEmpty() {
        return executor.isEmpty();
    }

    final void start(@Nonnull final LoadStage loadStage) {
        executor.run(loadStage, UniDict.LOG);
    }
}
