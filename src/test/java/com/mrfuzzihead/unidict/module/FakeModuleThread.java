package com.mrfuzzihead.unidict.module;

import java.util.List;

/**
 * T2 test fake: an {@link AbstractModuleThread} that, when run, appends a marker to a caller-owned
 * list and returns a string. Lets {@code LoadStageExecutorTest} assert that registration order is
 * preserved into execution order on the calling thread (docs/PLAN.md §M2).
 */
final class FakeModuleThread extends AbstractModuleThread {

    private final List<String> executionLog;
    private final String marker;

    FakeModuleThread(final String name, final List<String> executionLog) {
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
