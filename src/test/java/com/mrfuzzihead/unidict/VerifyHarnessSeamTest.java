package com.mrfuzzihead.unidict;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * T1 test for {@link VerifyHarness#checkSeam} — the BUG-9 seam self-check that turns "an integration
 * silently stopped working because its mixin was not applied" into a diagnosed {@code FAIL seam=…} line.
 *
 * <p>
 * Uses JDK types as the stand-ins (a {@link Thread} <em>is</em> a {@link Runnable}; a {@link String} is
 * not), so no Minecraft classes or mixins are involved. The dev switch is enabled for the duration of the
 * test and removed afterwards.
 */
class VerifyHarnessSeamTest {

    @AfterEach
    void disableVerifySwitch() {
        System.clearProperty("unidict.devVerify");
    }

    @Test
    void appliedSeamPassesAndMissingSeamFails() {
        System.setProperty("unidict.devVerify", "true");
        final int passedBefore = VerifyHarness.passCount();
        final int failedBefore = VerifyHarness.failureCount();

        VerifyHarness.checkSeam("test/applied", Runnable.class, Thread.class);
        VerifyHarness.checkSeam("test/missing", Runnable.class, String.class);
        VerifyHarness.checkSeam("test/null-target", Runnable.class, null);

        assertEquals(passedBefore + 1, VerifyHarness.passCount(), "an applied seam records one PASS");
        assertEquals(failedBefore + 2, VerifyHarness.failureCount(), "each missing seam records one FAIL");
    }

    @Test
    void doesNothingWhenTheDevSwitchIsOff() {
        final int passedBefore = VerifyHarness.passCount();
        final int failedBefore = VerifyHarness.failureCount();

        VerifyHarness.checkSeam("test/applied", Runnable.class, Thread.class);
        VerifyHarness.checkSeam("test/missing", Runnable.class, String.class);

        assertEquals(passedBefore, VerifyHarness.passCount(), "no PASS line without the dev switch");
        assertEquals(failedBefore, VerifyHarness.failureCount(), "no FAIL line without the dev switch");
    }
}
