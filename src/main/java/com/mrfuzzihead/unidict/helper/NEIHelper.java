package com.mrfuzzihead.unidict.helper;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.VerifyHarness;

import codechicken.nei.api.API;

/**
 * The single NEI item-hiding call site (v1 step 44). Every kept integration that needs to hide an
 * item must go through {@link #hide}; nothing else may call {@code API.hideItem}.
 *
 * <p>
 * <b>M4 status (2026-08-12 scope rework):</b> NEI hiding is deferred from M4 ("No NEI hiding") and
 * is only reintroduced when a kept rewrite requires it, so this method is not invoked yet. It is
 * kept as the guarded single site so the future wiring stays one call deep. The dev-mode main-thread
 * guard is the enforceable form of the M4 rule "NEI calls only on the main thread" — the original
 * crash was {@code updateEntries} → {@code removeBadEntriesFromNEI} → {@code API.hideItem} running
 * on fork-join threads.
 */
public final class NEIHelper {

    private NEIHelper() {}

    public static void hide(final ItemStack itemStack) {
        assertOnMainThread();
        API.hideItem(itemStack);
    }

    /**
     * Dev-only main-thread guard. {@code API.hideItem} must only run on the Minecraft client thread;
     * with the verify switch on we refuse to hide off-thread instead of crashing NEI later.
     */
    private static void assertOnMainThread() {
        if (VerifyHarness.isEnabled()) {
            final String thread = Thread.currentThread()
                .getName();
            final boolean mainLike = thread.startsWith("Client thread") || thread.startsWith("Server thread")
                || "main".equals(thread);
            if (!mainLike)
                throw new IllegalStateException("NEIHelper.hide called off the main thread on thread " + thread);
        }
    }
}
