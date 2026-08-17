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
 * This module is the safe, non-destructive fix: AFTER the resource pipeline (default POST_INIT) it
 * seeds CompTierRegistry with the unified model's canonical block/ingot/nugget triples through the
 * mod's OWN public register(...) API — the same path Minetweaker's Compaction integration uses — so a
 * compacting drawer honors the canonical entries deterministically, independent of recipe order or
 * the mod-matching bias. No recipe mutation, no mixins, no global OreDictionary mutation (BB-3).
 * The T2-testable seam is registerChain(block, ingot, nugget, registrar); call() is thin wiring from
 * the resource model to StorageDrawers.compRegistry.
 */

import java.util.Collection;

import net.minecraft.item.ItemStack;

import com.jaquadro.minecraft.storagedrawers.StorageDrawers;
import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.module.AbstractModuleThread;
import com.mrfuzzihead.unidict.resource.Resource;
import com.mrfuzzihead.unidict.resource.ResourceHandler;
import com.mrfuzzihead.unidict.resource.UniResourceContainer;

public final class StorageDrawersIntegration extends AbstractModuleThread {

    /** Conversion rate for nugget→ingot and ingot→block compaction (9 of the lower = 1 of the upper). */
    static final int CONV_RATE = 9;

    /** Seam over {@code StorageDrawers.compRegistry} so production stays decoupled from the mod type in T2. */
    interface CompactionRegistrar {

        boolean register(ItemStack upper, ItemStack lower, int convRate);
    }

    StorageDrawersIntegration() {
        super("StorageDrawers", "Integration");
    }

    @Override
    public String call() {
        try {
            final ResourceHandler resourceHandler = UniDict.resourceHandler;
            if (Config.storageDrawers() && resourceHandler != null) {
                final int registered = registerCanonicalChains(
                    resourceHandler.resources,
                    StorageDrawers.compRegistry::register);
                UniDict.LOG.info(
                    threadName + "seeded " + registered + " compacting-drawer tier mappings from the unified model.");
            }
        } catch (final Exception e) {
            UniDict.LOG.error(threadName, e);
        }
        return threadName + "Compacting drawers now honor the canonical block/ingot/nugget entries.";
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
            final UniResourceContainer block = (blockKind != 0) ? resource.getChild(blockKind) : null;
            final UniResourceContainer ingot = (ingotKind != 0) ? resource.getChild(ingotKind) : null;
            final UniResourceContainer nugget = (nuggetKind != 0) ? resource.getChild(nuggetKind) : null;
            total += registerChain(
                (block != null) ? block.getMainEntry() : null,
                (ingot != null) ? ingot.getMainEntry() : null,
                (nugget != null) ? nugget.getMainEntry() : null,
                registrar);
        }
        return total;
    }

    /**
     * Registers the canonical compaction pairs for one resource: block↔ingot and ingot↔nugget. A pair
     * is only registered when both ends are present and are genuinely different items (a degenerate
     * block→block / ingot→ingot record is never written). Returns the number of records added.
     */
    static int registerChain(final ItemStack block, final ItemStack ingot, final ItemStack nugget,
        final CompactionRegistrar registrar) {
        int n = 0;
        if (block != null && ingot != null && !sameItem(block, ingot) && registrar.register(block, ingot, CONV_RATE)) {
            n++;
        }
        if (ingot != null && nugget != null
            && !sameItem(ingot, nugget)
            && registrar.register(ingot, nugget, CONV_RATE)) {
            n++;
        }
        return n;
    }

    private static boolean sameItem(final ItemStack a, final ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage();
    }
}
