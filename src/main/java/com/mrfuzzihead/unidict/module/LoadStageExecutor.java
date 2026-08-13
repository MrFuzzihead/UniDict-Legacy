package com.mrfuzzihead.unidict.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.unidict.LoadStage;

/**
 * Sequential, deterministic executor for a single module's threads — M2's replacement for the
 * original thread-pool + nested-{@code Manager} machinery. Upstream ran every stage's threads
 * concurrently on a fixed pool (docs/PLAN.md §M2, non-deterministic ordering). Here:
 *
 * <ul>
 * <li>Registration order <em>is</em> execution order: threads are kept in per-stage insertion
 * order ({@link ArrayList}), never a {@code Set}, so runs are diffable run-to-run.</li>
 * <li>Execution is a plain {@code for} loop on the calling (main) thread — never a pool — so no
 * integration can race NEI or the Ore Dictionary (the historical crash source).</li>
 * <li>{@code run} returns each thread's {@code call()} result in execution order so tests can
 * assert registration order == execution order.</li>
 * </ul>
 */
public final class LoadStageExecutor {

    private final String moduleName;
    private final Map<LoadStage, List<AbstractModuleThread>> threadsByStage = new EnumMap<>(LoadStage.class);

    public LoadStageExecutor(final String moduleName) {
        this.moduleName = moduleName;
        for (final LoadStage loadStage : LoadStage.values()) threadsByStage.put(loadStage, new ArrayList<>());
    }

    /**
     * Registers a thread to run at its {@link SpecifiedLoadStage} (default {@link LoadStage#POST_INIT}).
     *
     * @return {@code true} if newly registered; {@code false} if it was already present for its stage.
     */
    public boolean add(final AbstractModuleThread thread) {
        final LoadStage stage = thread.getClass()
            .isAnnotationPresent(SpecifiedLoadStage.class)
                ? thread.getClass()
                    .getAnnotation(SpecifiedLoadStage.class)
                    .stage()
                : LoadStage.POST_INIT;
        final List<AbstractModuleThread> stageThreads = threadsByStage.get(stage);
        if (stageThreads.contains(thread)) return false;
        return stageThreads.add(thread);
    }

    /** @return {@code true} when no threads are registered for any stage. */
    public boolean isEmpty() {
        return threadsByStage.values()
            .stream()
            .allMatch(List::isEmpty);
    }

    /** @return {@code true} when no threads are registered for the given stage. */
    public boolean isEmpty(final LoadStage stage) {
        return threadsByStage.get(stage)
            .isEmpty();
    }

    /**
     * Runs every thread registered for the given stage, in registration order, on the calling
     * thread. Logs each result and a total-time line (same shape as upstream) for parity with prior
     * logs. A {@code null} logger (tests) skips logging but still executes.
     *
     * @return the {@code call()} results in execution order (empty when nothing is registered).
     */
    public List<String> run(final LoadStage stage, final Logger logger) {
        final List<AbstractModuleThread> stageThreads = threadsByStage.get(stage);
        final List<String> results = new ArrayList<>(stageThreads.size());
        if (stageThreads.isEmpty()) return results;
        final long initialTime = System.nanoTime();
        for (final AbstractModuleThread thread : stageThreads) {
            try {
                final String result = thread.call();
                results.add(result);
                if (logger != null) logger.info(result);
            } catch (final Exception e) {
                if (logger != null) {
                    logger.error("Something really bad happened on " + thread + " at load stage " + stage.name());
                    logger.error(e);
                }
            }
        }
        if (logger != null) {
            final long took = System.nanoTime() - initialTime;
            logger.info(
                "All " + stageThreads.size()
                    + " "
                    + moduleName
                    + "s took "
                    + took / 1000000
                    + "ms to finish at load stage "
                    + stage.name());
        }
        return results;
    }
}
