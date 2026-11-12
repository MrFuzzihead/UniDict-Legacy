package com.mrfuzzihead.unidict.gametest;

/**
 * Minimal shared assertion counter for the non-shipped in-game test mod.
 *
 * <p>
 * Deliberately tiny and dependency-free: this class runs inside a Forge 1.7.10 dev launch, where JUnit
 * is not on the game classpath. The markers are grepped by the {@code gameTestServer}/{@code clientTest}
 * Gradle tasks, so a crashed or partially-run test is an explicit failure rather than a silent pass.
 */
final class GameTestAssert {

    static final String PREFIX = "[UNIDICT-GAMETEST] ";
    static final String OK = "UNIDICT_GAMETEST_OK";
    static final String FAILED = "UNIDICT_GAMETEST_FAILED";
    static final String CLIENT_OK = "UNIDICT_CLIENT_TEST_OK";
    static final String CLIENT_FAILED = "UNIDICT_CLIENT_TEST_FAILED";

    private static int failures;
    private static int checks;

    private GameTestAssert() {}

    static void reset() {
        failures = 0;
        checks = 0;
    }

    static void check(final String description, final boolean condition) {
        checks++;
        if (condition) {
            System.out.println(PREFIX + "ok   " + description);
        } else {
            failures++;
            System.out.println(PREFIX + "FAIL " + description);
        }
    }

    static int failures() {
        return failures;
    }

    static int checks() {
        return checks;
    }

    static int passed() {
        return checks - failures;
    }
}
