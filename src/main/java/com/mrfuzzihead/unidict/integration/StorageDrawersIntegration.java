package com.mrfuzzihead.unidict.integration;

/*
 * Storage Drawers compacting-drawer compat (the "any compat module for Storage Drawers" idea from
 * docs/TODO.md).
 * A Compacting Drawer resolves its three tiers (block / ingot / nugget) by FIRST consulting
 * StorageDrawers.compRegistry (a public CompTierRegistry) and, only if that misses, by searching the
 * live CraftingManager recipes — where it prefers a candidate whose owning mod matches the base item
 * (findMatchingModCandidate). Because the canonical copper INGOT is TF's but the canonical copper
 * BLOCK is EtF's (canonicalItemNames), the recipe-search + mod-matching path picks the TF block — the
 * exact collision reported in TODO.md.
 * This module is the safe fix: AFTER the resource pipeline it seeds CompTierRegistry with the unified
 * model's canonical block/ingot/nugget triples through the mod's OWN public register(...) API — the
 * same path Minetweaker's Compaction integration uses — so a compacting drawer honors the canonical
 * entries deterministically, independent of recipe order or the mod-matching bias. No recipe
 * mutation, no mixins, no global OreDictionary mutation (BB-3).
 * Two Storage-Drawers-internal facts this module has to survive:
 * 1. CompTierRegistry.register(...) is replace-not-add: it first unregisters any record whose upper
 * or lower matches, so a later registration of the colliding TF block→TF ingot chain DELETES the
 * canonical EtF block→TF ingot record (e.g. a pack script calling the published
 * mods.storagedrawers.Compaction API, which MineTweaker/CraftTweaker applies after post-init and
 * re-applies on script reload). The module therefore runs at LOAD_COMPLETE (after every other
 * mod's post-init) and is re-seeded at server start, so the unified model is the last writer.
 * 2. A drawer's BASE tier is always the item that keys it (TileEntityDrawersComp.populateSlots runs
 * only while convRate[0] == 0); the registry supplies only the tiers above/below that item. Key a
 * drawer with the canonical INGOT (or nugget) to get the canonical block as its top tier — a
 * drawer keyed by a non-canonical block keeps showing that block.
 * The T2-testable seams are registerChain(block, ingot, nugget, registrar) (write) and
 * verifyChain(block, ingot, nugget, lookup) (read-back); call()/runAtServerStart() are thin wiring
 * from the resource model to StorageDrawers.compRegistry.
 */

import java.util.Collection;

import net.minecraft.item.ItemStack;

import com.jaquadro.minecraft.storagedrawers.StorageDrawers;
import com.jaquadro.minecraft.storagedrawers.config.CompTierRegistry;
import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.VerifyHarness;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;
import com.mrfuzzihead.unidict.resource.Resource;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.resource.UniResourceContainer;

/**
 * Runs at {@link LoadStage#LOAD_COMPLETE} rather than the default POST_INIT: {@code
 * CompTierRegistry.register(...)} replaces records that share the new upper/lower stack, so the
 * canonical chains must be written after every other mod's post-init has had its say (and they are
 * re-seeded at server start — see {@link #runAtServerStart()}). The resource model is still available
 * ({@code UniDict.resourceHandler} is published at POST_INIT and never cleared).
 */
@SpecifiedLoadStage(stage = LoadStage.LOAD_COMPLETE)
public final class StorageDrawersIntegration extends AbstractModuleThread {

    /** Conversion rate for nugget→ingot and ingot→block compaction (9 of the lower = 1 of the upper). */
    static final int CONV_RATE = 9;

    /** Seam over {@code StorageDrawers.compRegistry} so production stays decoupled from the mod type in T2. */
    interface CompactionRegistrar {

        boolean register(ItemStack upper, ItemStack lower, int convRate);
    }

    /**
     * Read-back seam over {@code StorageDrawers.compRegistry}: the upper tier the registry would resolve
     * for {@code lower}, or {@code null} when it has no record for it. Lets the module (and T2) check
     * that the canonical chains it seeded are still the ones a compacting drawer would honor — a later
     * {@code register(...)} (a pack's {@code mods.storagedrawers.Compaction} script) replaces them.
     */
    interface CompactionLookup {

        ItemStack findHigherTier(ItemStack lower);
    }

    StorageDrawersIntegration() {
        super("StorageDrawers", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            if (Config.storageDrawers() && resourceHandler != null) {
                final Collection<Resource<UniResourceContainer>> resources = resourceHandler.resources;
                final int seeded = registerCanonicalChains(resources, StorageDrawers.compRegistry::register);
                final int verified = countVerifiedChains(resources, StorageDrawersIntegration::registryUpper);
                UniDict.LOG.info(
                    threadName + "seeded "
                        + seeded
                        + " compacting-drawer tier mappings from the unified model ("
                        + verified
                        + " read back from the registry).");
                // T3 oracle: report what the registry actually resolves, not just that we ran. A shortfall
                // means a canonical pair is not resolvable (the registry was rebuilt, or Storage Drawers
                // rejected/replaced the record) — the compacting drawer would silently fall back to the
                // colliding recipe-search chain (e.g. TF's block). Grep "unidict-verify.*FAIL" must be empty.
                if (VerifyHarness.isEnabled()) {
                    VerifyHarness.record(
                        verified >= seeded,
                        "integration=storageDrawers",
                        "seeded=" + seeded,
                        "verified=" + verified);
                }
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "Compacting drawers now honor the canonical block/ingot/nugget entries.";
    }

    /**
     * Server-started re-seed, invoked from {@code UniDict.serverStarted} via
     * {@link IntegrationModule#runStorageDrawersAtServerStart()} (same shape as
     * {@link IntegrationModule#runCraftingAtServerStart()}). {@code CompTierRegistry.register(...)}
     * <b>replaces</b> any record sharing the new upper or lower stack, and MineTweaker/CraftTweaker
     * apply (and re-apply, e.g. on script reload) {@code mods.storagedrawers.Compaction.add(...)} after
     * post-init — which silently deletes the canonical block→ingot record and hands the top tier back to
     * the colliding recipe-search chain. Re-seeding here keeps the unified model the last writer; it is
     * idempotent (register replaces the same-target record), so re-running is safe.
     *
     * @return the number of tier records (re)written on this pass.
     */
    static int runAtServerStart() {
        final ResourceHandler resourceHandler = UniDict.resourceHandler;
        if (!Config.storageDrawers() || resourceHandler == null) return 0;
        final Collection<Resource<UniResourceContainer>> resources = resourceHandler.resources;
        final int before = countVerifiedChains(resources, StorageDrawersIntegration::registryUpper);
        final int seeded = registerCanonicalChains(resources, StorageDrawers.compRegistry::register);
        final int verified = countVerifiedChains(resources, StorageDrawersIntegration::registryUpper);
        if (before < seeded) {
            UniDict.LOG.warn(
                "[storageDrawers-server-start] " + before
                    + " of "
                    + seeded
                    + " canonical compacting-drawer chains had been replaced before server start "
                    + "(a script/mod re-registered them); re-seeded the unified model.");
        }
        UniDict.LOG.info(
            "[storageDrawers-server-start] re-seeded " + seeded
                + " compacting-drawer tier mappings ("
                + verified
                + " verified).");
        return seeded;
    }

    /**
     * Production wiring for {@link CompactionLookup}: the upper tier Storage Drawers' public registry
     * resolves for {@code lower}, or {@code null}. Kept here so {@link #call()} stays thin wiring.
     */
    private static ItemStack registryUpper(final ItemStack lower) {
        final CompTierRegistry.Record record = StorageDrawers.compRegistry.findHigherTier(lower);
        return (record != null) ? record.upper : null;
    }

    /**
     * Walks every unified resource and seeds the {@code CompactionRegistrar} with the canonical
     * block/ingot/nugget chains present in the model.
     *
     * @return the total number of tier records registered.
     */
    static int registerCanonicalChains(final Collection<Resource<UniResourceContainer>> resources,
        final CompactionRegistrar registrar) {
        int total = 0;
        final long blockKind = Resource.getKindOfName("block");
        final long ingotKind = Resource.getKindOfName("ingot");
        final long nuggetKind = Resource.getKindOfName("nugget");
        for (final Resource<UniResourceContainer> resource : resources) {
            total += registerChain(
                childEntry(resource, blockKind),
                childEntry(resource, ingotKind),
                childEntry(resource, nuggetKind),
                registrar);
        }
        return total;
    }

    /**
     * The read-back twin of {@link #registerCanonicalChains}: the same walk with the same pair rules,
     * counting the canonical pairs the {@link CompactionLookup} resolves <em>right now</em>. Equal counts
     * mean every chain we intended to write is the one the registry (and therefore a compacting drawer)
     * would use.
     *
     * @return the total number of intact tier records found.
     */
    static int countVerifiedChains(final Collection<Resource<UniResourceContainer>> resources,
        final CompactionLookup lookup) {
        int total = 0;
        final long blockKind = Resource.getKindOfName("block");
        final long ingotKind = Resource.getKindOfName("ingot");
        final long nuggetKind = Resource.getKindOfName("nugget");
        for (final Resource<UniResourceContainer> resource : resources) {
            total += verifyChain(
                childEntry(resource, blockKind),
                childEntry(resource, ingotKind),
                childEntry(resource, nuggetKind),
                lookup);
        }
        return total;
    }

    /** The canonical entry of one kind on one resource, or {@code null} when that kind is absent. */
    private static ItemStack childEntry(final Resource<UniResourceContainer> resource, final long kind) {
        if (kind == 0) return null;
        final UniResourceContainer child = resource.getChild(kind);
        return (child != null) ? child.getMainEntry() : null;
    }

    /**
     * Registers the canonical compaction pairs for one resource: block↔ingot and ingot↔nugget. A pair
     * is only registered when both ends are present and are genuinely different items (a degenerate
     * block→block / ingot→ingot record is never written). Returns the number of records added.
     */
    static int registerChain(final ItemStack block, final ItemStack ingot, final ItemStack nugget,
        final CompactionRegistrar registrar) {
        int n = 0;
        if (pairWanted(block, ingot) && registrar.register(block, ingot, CONV_RATE)) n++;
        if (pairWanted(ingot, nugget) && registrar.register(ingot, nugget, CONV_RATE)) n++;
        return n;
    }

    /**
     * Read-back twin of {@link #registerChain}: counts, of the pairs that function would register, how
     * many the {@link CompactionLookup} currently resolves back to our canonical upper tier. A pair the
     * registrar refused/skipped is never counted as verified (the two seams agree by construction).
     */
    static int verifyChain(final ItemStack block, final ItemStack ingot, final ItemStack nugget,
        final CompactionLookup lookup) {
        int n = 0;
        if (pairWanted(block, ingot) && resolvesTo(block, ingot, lookup)) n++;
        if (pairWanted(ingot, nugget) && resolvesTo(ingot, nugget, lookup)) n++;
        return n;
    }

    /**
     * Whether a compaction pair is wanted: both ends present and genuinely different items. Shared by the
     * write ({@link #registerChain}) and read-back ({@link #verifyChain}) walks so their pair decisions
     * cannot drift apart.
     */
    private static boolean pairWanted(final ItemStack upper, final ItemStack lower) {
        return upper != null && lower != null && !sameItem(upper, lower);
    }

    /** Whether the registry resolves {@code upper} as the higher tier of {@code lower}. */
    private static boolean resolvesTo(final ItemStack upper, final ItemStack lower, final CompactionLookup lookup) {
        final ItemStack found = lookup.findHigherTier(lower);
        return found != null && sameItem(found, upper);
    }

    private static boolean sameItem(final ItemStack a, final ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage();
    }
}
