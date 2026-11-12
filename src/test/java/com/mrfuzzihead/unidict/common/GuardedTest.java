package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class GuardedTest {

    @Test
    void caughtExceptionIsReportedAndDoesNotEscape() {
        final List<Throwable> failures = new ArrayList<>();
        final RuntimeException boom = new RuntimeException("boom");

        Guarded.run("phase", () -> { throw boom; }, failures::add);

        assertEquals(1, failures.size());
        assertEquals(boom, failures.get(0));
    }

    @Test
    void caughtErrorIsReportedToo() {
        final List<Throwable> failures = new ArrayList<>();
        final AssertionError boom = new AssertionError("optional mod class missing");

        Guarded.run("phase", () -> { throw boom; }, failures::add);

        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof Error);
        assertEquals(boom, failures.get(0));
    }

    @Test
    void successDoesNotCallTheFailureHandler() {
        final List<Throwable> failures = new ArrayList<>();
        Guarded.run("phase", () -> {}, failures::add);
        assertTrue(failures.isEmpty());
    }
}
