package com.mrfuzzihead.unidict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.unidict.common.Util;
import com.mrfuzzihead.unidict.oredict.OreDictionaryBridge;
import com.mrfuzzihead.unidict.pure.SelectionRules;
import com.mrfuzzihead.unidict.report.UniDictReport;
import com.mrfuzzihead.unidict.resource.Resource;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.resource.UniResourceContainer;
import com.mrfuzzihead.unidict.resource.UniResourceHandler;

import cpw.mods.fml.common.registry.GameData;

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
        runResourceReport();
        runTransparencyReport();
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

    /** M4 transparency-report output (BB-1 seed): one PASS line per unified resource entry. */
    private static void runResourceReport() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        if (resourceHandler == null) {
            record(false, "resource report", "no ResourceHandler — resource pipeline did not run?");
            return;
        }
        final List<String> lines = new ArrayList<>();
        final boolean autoHideInNEI = Config.get().autoHideInNEI;
        final Set<Long> kindBlackSet = UniResourceHandler.getKindBlackSet();
        for (final Resource<UniResourceContainer> resource : resourceHandler.resources)
            for (final UniResourceContainer container : resource.getChildrenCollection()) {
                final List<ItemStack> entries = container.getEntries();
                final int hidden = SelectionRules
                    .hiddenIndices(
                        entries,
                        autoHideInNEI,
                        container.kind,
                        kindBlackSet,
                        ResourceHandler::isKeepOneEntryBlacklisted)
                    .size();
                lines.add(
                    "resource=" + container.name
                        + " main="
                        + describe(container.getMainEntry())
                        + " variants="
                        + entries.size()
                        + " hidden="
                        + hidden);
            }
        // The underlying maps iterate in hash order; sort so the report is stable and diffable run-to-run.
        Collections.sort(lines);
        for (final String line : lines) record(true, line);
        record(!lines.isEmpty(), "resource report", "resources=" + lines.size());
    }

    /** BB-1 transparency report (docs/PLAN.md §BB-1): one PASS line per unified resource + per rewrite. */
    private static void runTransparencyReport() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        if (resourceHandler == null) return; // already reported the missing-handler condition above
        final List<String> lines = UniDictReport.lines(resourceHandler);
        for (final String line : lines) record(true, line);
        if (lines.isEmpty()) record(false, "report", "empty report — no unified resources");
    }

    private static String describe(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "none";
        final String registryName = GameData.getItemRegistry()
            .getNameForObject(stack.getItem());
        final String owner = Util.getModName(stack);
        // The registry name already carries its mod prefix; avoid a redundant "mod:mod:name".
        return registryName.startsWith(owner + ":") ? registryName : owner + ":" + registryName;
    }
}
