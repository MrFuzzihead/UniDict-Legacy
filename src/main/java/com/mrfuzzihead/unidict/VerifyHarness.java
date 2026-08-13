package com.mrfuzzihead.unidict;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.unidict.oredict.OreDictionaryBridge;

/**
 * M0 dev-only verify harness — the T3 oracle (docs/PLAN.md §0, docs/TestPlan.md). When enabled it
 * runs self-checks at FMLPostInit and logs deterministic, greppable lines:
 *
 * <pre>
 * [unidict-verify] PASS &lt;feature&gt;
 * [unidict-verify] FAIL &lt;feature&gt; &lt;detail...&gt;
 * </pre>
 *
 * Grep "unidict-verify.*FAIL" must always be empty. This is also the seed of the BB-1 transparency
 * report. Dev-gated only: a no-op unless enabled, so it never affects a normal build.
 *
 * <p>
 * Enable via {@code -PunidictDevVerify} (gradle, forwarded by addon.gradle.kts) or the
 * {@code UNIDICT_DEV_VERIFY} environment variable.
 */
public final class VerifyHarness {

    private static final Logger LOG = LogManager.getLogger("UniDict");
    private static final String SYS_PROP = "unidict.devVerify";
    private static final String ENV_VAR = "UNIDICT_DEV_VERIFY";

    private static int passed;
    private static int failed;

    private VerifyHarness() {}

    /**
     * True only when the dev verify switch is on. Never guards non-dev runs/builds. Enabled by either
     * the JVM system property {@code unidict.devVerify} (forwarded from the Gradle
     * {@code -PunidictDevVerify} in {@code addon.gradle.kts}) or the {@code UNIDICT_DEV_VERIFY}
     * environment variable — so it works regardless of how the client is launched.
     */
    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(SYS_PROP)) || Boolean.parseBoolean(System.getenv(ENV_VAR));
    }

    /** Log one PASS/FAIL verify line and update the counters. */
    public static void record(boolean pass, @Nonnull String feature, String... detail) {
        final String suffix = detail.length == 0 ? "" : " " + String.join(" ", detail);
        if (pass) {
            passed++;
            LOG.info("[unidict-verify] PASS " + feature + suffix);
        } else {
            failed++;
            LOG.error("[unidict-verify] FAIL " + feature + suffix);
        }
    }

    /** Run the self-checks. Called from the post-init hook when enabled. */
    public static void runChecks() {
        LOG.info("[unidict-verify] harness enabled — running self-checks");
        checkOreDictionaryBridge();
        // TODO(BB-1 / M1+): add per-resource report checks once the resource model lands.
        LOG.info("[unidict-verify] summary: {} passed, {} failed", passed, failed);
        if (failed > 0) LOG.warn("[unidict-verify] FAILURES PRESENT — \"unidict-verify.*FAIL\" matches exist");
    }

    /** Seed check for M0 Spike A: the Ore Dictionary accessor bridge reads the live maps lazily. */
    private static void checkOreDictionaryBridge() {
        final OreDictionaryBridge bridge = OreDictionaryBridge.instance();
        final boolean populated = bridge.getNameToId() != null && !bridge.getNameToId()
            .isEmpty();
        record(
            populated,
            "spikeA oredict-bridge",
            populated ? "nameToId=" + bridge.getNameToId()
                .size() : "bridge empty (lazy OreDictionaryMixin accessors not applied?)");
    }
}
