package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.integration.StorageDrawersIntegration.CompactionRegistrar;

/**
 * T2 test for the compacting-drawer tier seeding core {@link StorageDrawersIntegration#registerChain}
 * (docs/TestPlan.md rule 2). Drives the registrar seam with real {@link Item}/{@link ItemStack} types
 * (no MC statics, no Storage Drawers class needed) and asserts the pair decision: the canonical
 * block↔ingot and ingot↔nugget pairs are registered at the compaction rate, degenerate same-item
 * pairs are skipped, and a registrar that refuses a record isn't counted. This pins the actual
 * behavior that makes a compacting drawer honor the canonical copper block (e.g. EtF's) even though
 * the canonical copper ingot is TF's.
 */
class StorageDrawersIntegrationTest {

    /** Records every register(...) call for assertion. */
    private static final class RecordingRegistrar implements CompactionRegistrar {

        final List<String> records = new ArrayList<>();
        boolean accept = true;

        @Override
        public boolean register(final ItemStack upper, final ItemStack lower, final int convRate) {
            records.add(
                "upper=" + stackName(upper) + ",lower=" + stackName(lower) + ",rate=" + convRate);
            return accept;
        }

        private static String stackName(final ItemStack s) {
            return System.identityHashCode(s.getItem()) + "@" + s.getItemDamage();
        }
    }

    @Test
    void fullChainRegistersBlockIngotAndIngotNuggetAtCompactionRate() {
        final Item block = new Item();
        final Item ingot = new Item();
        final Item nugget = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();

        final int n = StorageDrawersIntegration.registerChain(
            new ItemStack(block, 1, 0),
            new ItemStack(ingot, 1, 1),
            new ItemStack(nugget, 1, 2),
            registrar);

        assertEquals(2, n, "block↔ingot and ingot↔nugget are both registered");
        assertEquals(2, registrar.records.size());
        assertEquals(
            "upper=" + System.identityHashCode(block) + "@0,lower=" + System.identityHashCode(ingot) + "@1,rate=9",
            registrar.records.get(0));
        assertEquals(
            "upper=" + System.identityHashCode(ingot) + "@1,lower=" + System.identityHashCode(nugget) + "@2,rate=9",
            registrar.records.get(1));
    }

    @Test
    void missingNuggetStillRegistersTheBlockIngotPair() {
        final Item block = new Item();
        final Item ingot = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();

        final int n = StorageDrawersIntegration.registerChain(
            new ItemStack(block, 1, 0),
            new ItemStack(ingot, 1, 1),
            null,
            registrar);

        assertEquals(1, n);
        assertEquals(1, registrar.records.size());
    }

    @Test
    void missingBlockStillRegistersTheIngotNuggetPair() {
        final Item ingot = new Item();
        final Item nugget = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();

        final int n = StorageDrawersIntegration.registerChain(
            null,
            new ItemStack(ingot, 1, 0),
            new ItemStack(nugget, 1, 1),
            registrar);

        assertEquals(1, n);
        assertEquals(1, registrar.records.size());
    }

    @Test
    void ingotAloneRegistersNothing() {
        final Item ingot = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();

        final int n = StorageDrawersIntegration.registerChain(
            null,
            new ItemStack(ingot, 1, 0),
            null,
            registrar);

        assertEquals(0, n);
        assertTrue(registrar.records.isEmpty(), "a lone canonical ingot is not a compaction chain");
    }

    @Test
    void everythingNullRegistersNothing() {
        final RecordingRegistrar registrar = new RecordingRegistrar();
        assertEquals(0, StorageDrawersIntegration.registerChain(null, null, null, registrar));
        assertTrue(registrar.records.isEmpty());
    }

    @Test
    void degenerateSameItemPairIsSkipped() {
        // A "block" that is the same item+damage as the "ingot" would be a nonsense compaction record
        // (e.g. if a resource ever resolved two kinds to the same canonical stack) — never write it.
        final Item same = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();

        final int n = StorageDrawersIntegration.registerChain(
            new ItemStack(same, 1, 3),
            new ItemStack(same, 1, 3),
            null,
            registrar);

        assertEquals(0, n);
        assertTrue(registrar.records.isEmpty());
    }

    @Test
    void refusedRecordsAreNotCounted() {
        final Item block = new Item();
        final Item ingot = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();
        registrar.accept = false;

        final int n = StorageDrawersIntegration.registerChain(
            new ItemStack(block, 1, 0),
            new ItemStack(ingot, 1, 1),
            null,
            registrar);

        assertEquals(0, n, "a registrar that refuses must not inflate the reported count");
        assertEquals(1, registrar.records.size(), "the registrar was still asked");
    }
}
