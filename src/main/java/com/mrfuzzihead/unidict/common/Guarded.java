package com.mrfuzzihead.unidict.common;

import java.util.function.Consumer;

/**
 * Runs one lifecycle action without letting a failure escape into FML's event dispatcher.
 *
 * <p>
 * A load-complete or server-start hook must not abort the game because one optional integration hit a
 * version mismatch, a missing Mixin, or a bad recipe map. The caller supplies the logging policy, which
 * keeps this decision pure and T1-testable for both {@link Exception} and {@link Error} (for example a
 * {@code NoClassDefFoundError} from an optional mod).
 */
public final class Guarded {

    private Guarded() {}

    /**
     * @param name      short label for diagnostics (the caller logs it)
     * @param action    the work to run
     * @param onFailure receives the caught {@link Throwable}; never called on success
     */
    public static void run(final String name, final Runnable action, final Consumer<Throwable> onFailure) {
        try {
            action.run();
        } catch (final Throwable t) {
            if (onFailure != null) onFailure.accept(t);
        }
    }
}
