package com.mrfuzzihead.unidict.gametest;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

/**
 * Non-shipped in-game test mod. It lives in {@code src/gameTest} and is only put on the runtime classpath
 * by {@code ./gradlew gameTestServer} (dedicated server) or {@code ./gradlew clientTest} (client smoke).
 *
 * <p>
 * The marker lines are the contract with the Gradle tasks: {@code UNIDICT_GAMETEST_OK} /
 * {@code UNIDICT_CLIENT_TEST_OK} on success, {@code *_FAILED} on any failure. The JVM exits with 0/1 so a
 * crash or a hung server cannot look like a pass.
 */
@Mod(
    modid = GameTestMod.MOD_ID,
    name = "UniDict Game Tests",
    version = "1.0",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:unidict")
public final class GameTestMod {

    public static final String MOD_ID = "unidictgametest";

    private boolean serverRan;

    /**
     * Client smoke path: there is no world yet at load-complete, but the resource model and report must be
     * published. Runs after UniDict's own load-complete handler because of the `required-after:unidict`
     * dependency.
     */
    @Mod.EventHandler
    public void loadComplete(final FMLLoadCompleteEvent event) {
        if (!Boolean.getBoolean("unidict.clientTest")) return;

        GameTestAssert.reset();
        try {
            ModelGameTests.runAll();
        } catch (final Throwable t) {
            t.printStackTrace();
            System.out.println(GameTestAssert.CLIENT_FAILED + ": " + t);
            exit(1);
            return;
        }
        finish(true);
    }

    /** Dedicated-server path: runs the model checks plus the live-world checks. */
    @Mod.EventHandler
    public void serverStarted(final FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("unidict.gameTest") || serverRan) return;
        serverRan = true;

        GameTestAssert.reset();
        try {
            ServerGameTests.runAll();
        } catch (final Throwable t) {
            t.printStackTrace();
            System.out.println(GameTestAssert.FAILED + ": " + t);
            exit(1);
            return;
        }
        finish(false);
    }

    private static void finish(final boolean client) {
        final int checks = GameTestAssert.checks();
        final int failures = GameTestAssert.failures();
        System.out.println(
            GameTestAssert.PREFIX + GameTestAssert
                .passed() + "/" + checks + " checks passed" + (client ? " (client)" : ""));
        if (failures > 0) {
            System.out
                .println((client ? GameTestAssert.CLIENT_FAILED : GameTestAssert.FAILED) + ": " + failures + " failed");
            exit(1);
        } else {
            System.out.println(client ? GameTestAssert.CLIENT_OK : GameTestAssert.OK);
            exit(0);
        }
    }

    private static void exit(final int status) {
        FMLCommonHandler.instance()
            .exitJava(status, false);
    }
}
