package com.mrfuzzihead.unidict.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.LoadStage;
import com.mrfuzzihead.unidict.integration.StorageDrawersIntegration.CompactionLookup;
import com.mrfuzzihead.unidict.integration.StorageDrawersIntegration.CompactionRegistrar;
import com.mrfuzzihead.unidict.module.SpecifiedLoadStage;

/**
 * T2 test for the compacting-drawer tier-seeding core: {@link StorageDrawersIntegration#registerChain}
 * (write) and {@link StorageDrawersIntegration#verifyChain} (read-back) — docs/TestPlan.md rule 2. Drives
 * the registrar/lookup seams with real {@link Item}/{@link ItemStack} types (no MC statics, no Storage
 * Drawers class needed) and asserts the pair decision — the canonical block↔ingot and ingot↔nugget pairs
 * are registered at the compaction rate, degenerate same-item pairs are skipped, a registrar that refuses
 * a record isn't counted, and the read-back only counts pairs the registry still resolves to our canonical
 * upper tier. This pins the actual behavior that makes a compacting drawer honor the canonical copper
 * block (e.g. EtF's) even though the canonical copper ingot is TF's — including the failure the module is
 * hardened against: a later registration of the colliding chain replacing our record.
 */
class StorageDrawersIntegrationTest {

    /** Records every register(...) call for assertion. */
    private static final class RecordingRegistrar implements CompactionRegistrar {

        final List<String> records = new ArrayList<>();
        boolean accept = true;

        @Override
        public boolean register(final ItemStack upper, final ItemStack lower, final int convRate) {
            records.add("upper=" + stackName(upper) + ",lower=" + stackName(lower) + ",rate=" + convRate);
            return accept;
        }

        private static String stackName(final ItemStack s) {
            return System.identityHashCode(s.getItem()) + "@" + s.getItemDamage();
        }
    }

    /**
     * A stand-in for Storage Drawers' {@code CompTierRegistry} that mirrors its real semantics:
     * {@code register} is <em>replace-not-add</em> (it first drops any record sharing the new lower
     * stack, which is what lets a pack's {@code Compaction} script clobber our canonical record), and
     * {@code findHigherTier} answers with the upper tier registered for a lower stack. A map keyed by
     * the lower stack models exactly that (the real registry also drops by upper; the read-back seam
     * under test only looks at the lower side).
     */
    private static final class FakeRegistry implements CompactionRegistrar, CompactionLookup {

        private final Map<String, ItemStack> upperForLower = new HashMap<>();

        @Override
        public boolean register(final ItemStack upper, final ItemStack lower, final int convRate) {
            if (upper == null || lower == null) return false;
            upperForLower.put(stackKey(lower), upper);
            return true;
        }

        @Override
        public ItemStack findHigherTier(final ItemStack lower) {
            return (lower == null) ? null : upperForLower.get(stackKey(lower));
        }
    }

    /** Identity of an item+damage pair, the same thing the module's {@code sameItem} compares. */
    private static String stackKey(final ItemStack stack) {
        return System.identityHashCode(stack.getItem()) + "@" + stack.getItemDamage();
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

        final int n = StorageDrawersIntegration
            .registerChain(new ItemStack(block, 1, 0), new ItemStack(ingot, 1, 1), null, registrar);

        assertEquals(1, n);
        assertEquals(1, registrar.records.size());
    }

    @Test
    void missingBlockStillRegistersTheIngotNuggetPair() {
        final Item ingot = new Item();
        final Item nugget = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();

        final int n = StorageDrawersIntegration
            .registerChain(null, new ItemStack(ingot, 1, 0), new ItemStack(nugget, 1, 1), registrar);

        assertEquals(1, n);
        assertEquals(1, registrar.records.size());
    }

    @Test
    void ingotAloneRegistersNothing() {
        final Item ingot = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();

        final int n = StorageDrawersIntegration.registerChain(null, new ItemStack(ingot, 1, 0), null, registrar);

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

        final int n = StorageDrawersIntegration
            .registerChain(new ItemStack(same, 1, 3), new ItemStack(same, 1, 3), null, registrar);

        assertEquals(0, n);
        assertTrue(registrar.records.isEmpty());
    }

    @Test
    void refusedRecordsAreNotCounted() {
        final Item block = new Item();
        final Item ingot = new Item();
        final RecordingRegistrar registrar = new RecordingRegistrar();
        registrar.accept = false;

        final int n = StorageDrawersIntegration
            .registerChain(new ItemStack(block, 1, 0), new ItemStack(ingot, 1, 1), null, registrar);

        assertEquals(0, n, "a registrar that refuses must not inflate the reported count");
        assertEquals(1, registrar.records.size(), "the registrar was still asked");
    }

    @Test
    void readBackAgreesWithTheWriteSeamOnAHealthyRegistry() {
        final Item block = new Item();
        final Item ingot = new Item();
        final Item nugget = new Item();
        final FakeRegistry registry = new FakeRegistry();

        final int seeded = StorageDrawersIntegration.registerChain(
            new ItemStack(block, 1, 0),
            new ItemStack(ingot, 1, 1),
            new ItemStack(nugget, 1, 2),
            registry);
        final int verified = StorageDrawersIntegration
            .verifyChain(new ItemStack(block, 1, 0), new ItemStack(ingot, 1, 1), new ItemStack(nugget, 1, 2), registry);

        assertEquals(2, seeded);
        assertEquals(seeded, verified, "every chain we wrote must read back as the canonical one");
    }

    @Test
    void aLaterRegistrationOfTheCollidingChainReadsBackUnverified() {
        // The production failure mode: a pack script (mods.storagedrawers.Compaction.add) re-registers the
        // colliding block→ingot chain after we seeded. CompTierRegistry.register is replace-not-add, so OUR
        // record is the one that disappears and the drawer silently falls back to the colliding chain. The
        // read-back must show 2 of 2 -> 1 of 2 so the T3 gate can fail loudly instead of the drawer quietly
        // showing/dispensing the wrong block.
        final Item canonicalBlock = new Item();
        final Item collidingBlock = new Item();
        final Item ingot = new Item();
        final Item nugget = new Item();
        final FakeRegistry registry = new FakeRegistry();

        final int seeded = StorageDrawersIntegration.registerChain(
            new ItemStack(canonicalBlock, 1, 0),
            new ItemStack(ingot, 1, 1),
            new ItemStack(nugget, 1, 2),
            registry);
        registry.register(new ItemStack(collidingBlock, 1, 0), new ItemStack(ingot, 1, 1), 9);

        final int verified = StorageDrawersIntegration.verifyChain(
            new ItemStack(canonicalBlock, 1, 0),
            new ItemStack(ingot, 1, 1),
            new ItemStack(nugget, 1, 2),
            registry);

        assertEquals(2, seeded);
        assertEquals(1, verified, "only the ingot↔nugget pair survives the scripted re-registration");
    }

    @Test
    void missingOrForeignUpperTierIsNotVerified() {
        final Item block = new Item();
        final Item foreignBlock = new Item();
        final Item ingot = new Item();
        final Item nugget = new Item();
        final FakeRegistry registry = new FakeRegistry();

        assertEquals(
            0,
            StorageDrawersIntegration.verifyChain(
                new ItemStack(block, 1, 0),
                new ItemStack(ingot, 1, 1),
                new ItemStack(nugget, 1, 2),
                registry),
            "an empty registry resolves nothing, so nothing is verified");

        registry.register(new ItemStack(foreignBlock, 1, 0), new ItemStack(ingot, 1, 1), 9);

        assertEquals(
            0,
            StorageDrawersIntegration
                .verifyChain(new ItemStack(block, 1, 0), new ItemStack(ingot, 1, 1), null, registry),
            "a record pointing at a different block than the canonical one is not a verified chain");
    }

    @Test
    void verifyChainSkipsThePairsRegisterChainSkips() {
        final Item same = new Item();
        final Item ingot = new Item();
        final FakeRegistry registry = new FakeRegistry();

        assertEquals(0, StorageDrawersIntegration.verifyChain(null, null, null, registry));
        assertEquals(0, StorageDrawersIntegration.verifyChain(null, new ItemStack(ingot, 1, 0), null, registry));
        assertEquals(
            0,
            StorageDrawersIntegration.verifyChain(new ItemStack(same, 1, 3), new ItemStack(same, 1, 3), null, registry),
            "a degenerate same-item pair is never a chain, in both directions");
    }

    @Test
    void runsAtLoadCompleteSoTheCanonicalChainIsTheLastWriter() {
        // Pins the stage choice: CompTierRegistry.register replaces conflicting records, so this module
        // must not run at the default POST_INIT (a later registration would delete the canonical chain).
        final SpecifiedLoadStage stage = StorageDrawersIntegration.class.getAnnotation(SpecifiedLoadStage.class);

        assertNotNull(stage, "the module must pin its load stage explicitly");
        assertEquals(LoadStage.LOAD_COMPLETE, stage.stage());
    }
}
