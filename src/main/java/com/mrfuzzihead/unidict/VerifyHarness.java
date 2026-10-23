package com.mrfuzzihead.unidict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

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
 * M0 dev-only verify harness — the T3 oracle (docs/PLAN.md §0, docs/TestPlan.md). When enabled it runs
 * self-checks in two stages and logs deterministic, greppable lines:
 *
 * <pre>
 * [unidict-verify] PASS &lt;feature&gt;
 * [unidict-verify] FAIL &lt;feature&gt; &lt;detail...&gt;
 * </pre>
 *
 * <ul>
 * <li><b>Stage 1</b> — {@link #runChecks()}, at FMLPostInit: Ore-Dictionary bridge, resource report,
 * transparency report.</li>
 * <li><b>Stage 2</b> — {@link #runPostRemapChecks()}, at FMLServerStarted: the resource index, checked
 * <em>after</em> Forge has applied the world's block/item id map. A post-init-only harness is
 * structurally blind to id staleness (see that method).</li>
 * </ul>
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

    /** How many offending stacks a FAIL line names before it just reports the count. */
    private static final int SAMPLE_LIMIT = 5;

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
                        ResourceHandler::isModBlacklisted,
                        ResourceHandler::isProtected)
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

    /**
     * Stage 2 — the post-remap oracle. Called at {@code FMLServerStartedEvent}, i.e. <b>after</b> Forge has
     * applied the world's block/item id map.
     *
     * <p>
     * Forge fires {@code FMLModIdMappingEvent} while the world's Forge data is read (between
     * {@code FMLServerAboutToStartEvent} and this event — {@code FMLContainer.readData →
     * GameData.injectWorldIDMap}), and reverts the map at {@code serverStopped}. Stage 1 runs at POST_INIT,
     * <em>before</em> any of that, so it is structurally unable to observe id staleness — which is exactly
     * how the drop-time cross-wiring bug (an Amethyst Cluster dropping aluminum ore) shipped: the resource
     * index was keyed by registry ids, went stale on the remap, and a stale key landed on a <em>different</em>
     * item's entry instead of merely missing. See docs/TODO.md #3b.
     *
     * <p>
     * The three checks, in order of increasing directness:
     * <ol>
     * <li><b>coverage</b> — every variant still resolves at all (an id-keyed index loses every shifted id);</li>
     * <li><b>no-cross-wiring</b> — the container the index assigns to a variant must be an Ore Dictionary
     * name that variant is actually registered under. Forge rebakes the real dictionary on the same event
     * ({@code ForgeModContainer.mappingChanged → OreDictionary.rebakeMap}), so live OD is independent ground
     * truth for "which groups does this stack belong to" — the index cannot launder its own mistake here;</li>
     * <li><b>no-spurious-rewrite</b> — sweeping the whole item registry, a stack that belongs to no modeled
     * group must come back from {@code getMainItemStack} by identity. This is the field symptom stated as an
     * invariant: an item with no unified resource is never swapped.</li>
     * </ol>
     */
    public static void runPostRemapChecks() {
        if (!isEnabled()) return;
        LOG.info("[unidict-verify] stage 2 — post-remap resource-index checks (FMLModIdMappingEvent has fired)");
        final int passedBefore = passed;
        final int failedBefore = failed;
        try {
            checkIndexCoverage();
            checkNoCrossWiring();
            checkNoSpuriousRewrite();
        } catch (final Throwable t) {
            // A dev harness must never take the server down with it; a throw is itself a finding.
            record(false, "resourceIndex", "stage 2 aborted:", String.valueOf(t));
        }
        LOG.info(
            "[unidict-verify] stage-2 summary: {} passed, {} failed",
            passed - passedBefore,
            failed - failedBefore);
        if (failed > 0) LOG.warn("[unidict-verify] FAILURES PRESENT — \"unidict-verify.*FAIL\" matches exist");
    }

    /** Every variant must still be resolvable after the remap — the cheapest id-staleness detector. */
    private static void checkIndexCoverage() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        if (resourceHandler == null) {
            record(false, "resourceIndex coverage", "no ResourceHandler — resource pipeline did not run?");
            return;
        }
        int entries = 0;
        int missed = 0;
        final List<String> sample = new ArrayList<>();
        for (final Resource<UniResourceContainer> resource : resourceHandler.resources)
            for (final UniResourceContainer container : resource.getChildrenCollection())
                for (final ItemStack entry : container.getEntries()) {
                    entries++;
                    if (resourceHandler.exists(entry)) continue;
                    missed++;
                    if (sample.size() < SAMPLE_LIMIT) sample.add(describeEntry(entry) + " in " + container.name);
                }
        recordWithSample(missed == 0, "resourceIndex coverage", "entries=" + entries, missed, sample);
    }

    /**
     * A variant may only be assigned to a container that is one of its <em>own</em> Ore Dictionary groups.
     * This is the check that fails loudly on the reported bug: with an id-keyed index, the amethyst shard's
     * post-remap id equalled {@code modded_raw_ore}'s load-time id, so it was assigned to {@code oreAluminum}
     * — a group it is not a member of.
     */
    private static void checkNoCrossWiring() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        if (resourceHandler == null) return; // coverage already reported the missing handler
        int checked = 0;
        int crossWired = 0;
        int unverifiable = 0;
        final List<String> sample = new ArrayList<>();
        for (final Resource<UniResourceContainer> resource : resourceHandler.resources)
            for (final UniResourceContainer container : resource.getChildrenCollection())
                for (final ItemStack entry : container.getEntries()) {
                    final UniResourceContainer assigned = resourceHandler.getContainer(entry);
                    if (assigned == null) continue; // a miss, not a mis-assignment — coverage reports it
                    final Set<String> groups = oreNamesOf(entry);
                    if (groups.isEmpty()) {
                        // No live OD membership to compare against (e.g. OD was not rebaked) — say so
                        // instead of silently passing.
                        unverifiable++;
                        continue;
                    }
                    checked++;
                    if (groups.contains(assigned.name)) continue;
                    crossWired++;
                    if (sample.size() < SAMPLE_LIMIT)
                        sample.add(describeEntry(entry) + " -> " + assigned.name + " (member of " + groups + ")");
                }
        recordWithSample(
            crossWired == 0,
            "resourceIndex no-cross-wiring",
            "checked=" + checked + " unverifiable=" + unverifiable,
            crossWired,
            sample);
    }

    /**
     * The field symptom as a global invariant: a stack that belongs to no modeled group must be returned
     * <em>by identity</em>, never swapped for another item's canonical stack.
     */
    private static void checkNoSpuriousRewrite() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        if (resourceHandler == null) return;
        int checked = 0;
        int spurious = 0;
        final List<String> sample = new ArrayList<>();
        for (final Object candidate : GameData.getItemRegistry()) {
            // The registry's iterator is raw-typed once its raw supertype erases the generic signature,
            // so the element type arrives as Object; skip non-Items rather than unchecked-casting.
            if (!(candidate instanceof Item)) continue;
            final Item item = (Item) candidate;
            final ItemStack stack = new ItemStack(item, 1, 0);
            checked++;
            final ItemStack resolved = resourceHandler.getMainItemStack(stack);
            if (resolved == stack) continue; // untouched — the expected outcome for an unmodeled item
            if (hasModeledGroup(resourceHandler, stack)) continue; // legitimately unified
            spurious++;
            if (sample.size() < SAMPLE_LIMIT) sample.add(describeEntry(stack) + " -> " + describe(resolved));
        }
        recordWithSample(spurious == 0, "resourceIndex no-spurious-rewrite", "stacks=" + checked, spurious, sample);
    }

    /** @return the live Ore Dictionary group names of {@code stack}; empty when it has none. */
    private static Set<String> oreNamesOf(final ItemStack stack) {
        final Set<String> names = new LinkedHashSet<>();
        try {
            for (final int oreId : OreDictionary.getOreIDs(stack)) names.add(OreDictionary.getOreName(oreId));
        } catch (final Throwable ignored) {
            // A malformed registration must not abort the harness; callers report an empty set.
        }
        return names;
    }

    /** @return {@code true} when {@code stack} belongs to at least one container UniDict models. */
    private static boolean hasModeledGroup(final ResourceHandler resourceHandler, final ItemStack stack) {
        for (final String name : oreNamesOf(stack)) if (resourceHandler.containerExists(name)) return true;
        return false;
    }

    /** Records a PASS/FAIL line, naming up to {@link #SAMPLE_LIMIT} offenders when it fails. */
    private static void recordWithSample(final boolean pass, @Nonnull final String feature, final String summary,
        final int offenders, final List<String> sample) {
        final List<String> detail = new ArrayList<>(3);
        detail.add(summary);
        if (offenders > 0) {
            detail.add("offenders=" + offenders);
            detail.add("sample=[" + String.join(", ", sample) + "]");
        }
        record(pass, feature, detail.toArray(new String[0]));
    }

    /** {@code modid:name@damage} — enough to identify a variant in a log line without NEI. */
    private static String describeEntry(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "none";
        return GameData.getItemRegistry()
            .getNameForObject(stack.getItem()) + "@"
            + stack.getItemDamage();
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
