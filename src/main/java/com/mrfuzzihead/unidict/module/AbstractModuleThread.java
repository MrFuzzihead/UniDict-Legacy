package com.mrfuzzihead.unidict.module;

import java.util.concurrent.Callable;

/**
 * One unit of integration work. Ported from {@code wanion.unidict.module.AbstractModuleThread}
 * (WanionCane, MPL-2.0). It stays a {@link Callable<String>} so the {@code call()} convention from
 * upstream is preserved, but it is now executed sequentially and deterministically by
 * {@link LoadStageExecutor} rather than on a thread pool.
 */
public abstract class AbstractModuleThread implements Callable<String> {

    protected final String threadName;

    public AbstractModuleThread(final String threadName, final String moduleName) {
        this.threadName = threadName + " " + moduleName + ": ";
    }
}
