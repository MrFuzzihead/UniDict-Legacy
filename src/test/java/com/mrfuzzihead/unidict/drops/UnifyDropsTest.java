package com.mrfuzzihead.unidict.drops;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.function.UnaryOperator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

/**
 * T2 test for the drop-time unification core {@link UnifyDrops#unifyDrop} (docs/TestPlan.md rule 2).
 * Drives the resolver seam with real {@link Item}/{@link ItemStack} <em>types</em> (no MC statics)
 * and asserts the two safety invariants the feature exists to uphold: clean stacks map to their
 * canonical entry, and NBT-tagged ("dirty") stacks are never touched. It also pins the efficiency
 * guarantee the user asked for — an already-canonical drop is returned by identity and never
 * re-written, even when the resolver hands back a freshly-allocated equivalent stack (which is what
 * {@code ResourceHandler#getMainItemStack} does via {@code UniResourceContainer#getMainEntry}).
 *
 * <p>
 * The event plumbing ({@code EntityJoinWorldEvent} filtering, world-side + config-toggle guards) is
 * intentionally not asserted here — it is thin and dev-gated; the resource lookup is exercised by the
 * other integrations' T2 seams and in-game (T3).
 */
class UnifyDropsTest {

    @Test
    void cleanStackMapsToCanonicalEntry() {
        final Item item = new Item();
        final ItemStack dropped = new ItemStack(item, 1, 2);
        final ItemStack canonical = new ItemStack(item, 1, 3);

        assertSame(canonical, UnifyDrops.unifyDrop(dropped, s -> (s == dropped) ? canonical : s));
    }

    @Test
    void alreadyCanonicalCleanStackIsLeftUntouched() {
        final Item item = new Item();
        // The resolver mirrors ResourceHandler#getMainItemStack: it ALLOCATES a fresh, equivalent
        // stack even when the drop is already the canonical entry (UniResourceContainer#getMainEntry
        // always news an ItemStack). The seam must NOT rewrite an already-preferred drop.
        final ItemStack dropped = new ItemStack(item, 4, 3);
        final ItemStack freshEquivalent = new ItemStack(item, 4, 3);

        assertSame(
            dropped,
            UnifyDrops.unifyDrop(dropped, s -> freshEquivalent),
            "an already-canonical drop must be returned by identity — no wasted re-write");
    }

    @Test
    void cleanStackMappingToDifferentDamageRewrites() {
        final Item item = new Item();
        final ItemStack dropped = new ItemStack(item, 1, 0);
        final ItemStack alternateDamage = new ItemStack(item, 1, 5);

        assertSame(alternateDamage, UnifyDrops.unifyDrop(dropped, s -> alternateDamage));
    }

    @Test
    void nbtTaggedStackIsNeverReplaced() {
        final Item item = new Item();
        final ItemStack dropped = new ItemStack(item, 1, 2);
        dropped.stackTagCompound = new NBTTagCompound(); // "dirty" — carries a tag

        // Even a resolver that would claim a canonical entry must not touch a tagged stack.
        assertSame(dropped, UnifyDrops.unifyDrop(dropped, s -> new ItemStack(item, 1, 3)));
    }

    @Test
    void cleanStackWithNoUnifiedResourceStaysUntouched() {
        final Item item = new Item();
        final ItemStack dropped = new ItemStack(item, 1, 0);

        // Identity resolver: the item belongs to no unified resource, so it maps to itself.
        assertSame(dropped, UnifyDrops.unifyDrop(dropped, UnaryOperator.identity()));
    }

    @Test
    void protectedDropIsReturnedByIdentity() {
        final Item item = new Item();
        // ResourceHandler#getMainItemStack returns protected items (e.g. EtF raw copper, matched by
        // the default "raw" OD-name substring in protectedOreDictionaryNames) unchanged, so unifyDrop
        // sees an identity resolver and keeps the mined raw metal instead of morphing it into a mod's
        // copper ore block.
        final ItemStack rawCopper = new ItemStack(item, 1, 7);
        assertSame(rawCopper, UnifyDrops.unifyDrop(rawCopper, s -> s));
    }

    @Test
    void nullStackReturnsNull() {
        assertNull(UnifyDrops.unifyDrop(null, UnaryOperator.identity()));
    }

    @Test
    void emptyStackReturnsItself() {
        final Item item = new Item();
        final ItemStack empty = new ItemStack(item, 0, 0);
        assertSame(empty, UnifyDrops.unifyDrop(empty, s -> new ItemStack(item, 1, 9)));
    }
}
