package com.mrfuzzihead.unidict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * First JVM unit test (T1) — proves the {@code ./gradlew test} harness is wired (M0 gate).
 * Pure logic only: this file must contain NO net.minecraft / net.minecraftforge imports.
 */
class SmokeTest {

    @Test
    void demoAssertionRuns() {
        assertEquals(4, 2 + 2);
        assertTrue("unidict".startsWith("uni"));
    }

    @Test
    void throwsDemo() {
        assertThrows(IllegalArgumentException.class, () -> { throw new IllegalArgumentException("boom"); });
    }
}
