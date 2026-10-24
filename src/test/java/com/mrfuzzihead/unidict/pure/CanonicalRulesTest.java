package com.mrfuzzihead.unidict.pure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for {@link CanonicalRules} — the pure "replace or keep the caller's stack?" decision behind
 * {@code ResourceHandler#getMainItemStack} (docs/CODE_REVIEW.md BUG-4/BUG-6, OPT-1). Zero
 * {@code net.minecraft*} imports: the two item identities are plain {@link Object}s.
 *
 * <p>
 * Pins both guarantees the resolver makes:
 * <ul>
 * <li><b>No-op identity</b> — an already-canonical stack is kept (so every integration's reference
 * check, the report counts and the server-start re-runs are correct);</li>
 * <li><b>NBT preservation</b> — a tagged stack is never replaced (the canonical entry is a bare stack,
 * so substituting it would delete the tag).</li>
 * </ul>
 */
class CanonicalRulesTest {

    /** Stand-in for an {@code Item} — only identity matters here. */
    private static final Object ITEM_A = new Object();
    private static final Object ITEM_B = new Object();

    @Test
    void alreadyCanonicalStackIsKeptByIdentity() {
        assertFalse(CanonicalRules.shouldReplace(false, ITEM_A, 3, ITEM_A, 3));
    }

    @Test
    void taggedStacksAreNeverReplaced() {
        // Even when item+damage differ, a tagged stack must survive untouched.
        assertFalse(CanonicalRules.shouldReplace(true, ITEM_B, 0, ITEM_A, 0));
        // ...and of course when they match too.
        assertFalse(CanonicalRules.shouldReplace(true, ITEM_A, 0, ITEM_A, 0));
    }

    @Test
    void differentItemRequiresReplacement() {
        assertTrue(CanonicalRules.shouldReplace(false, ITEM_B, 0, ITEM_A, 0));
    }

    @Test
    void differentDamageRequiresReplacement() {
        assertTrue(CanonicalRules.shouldReplace(false, ITEM_A, 0, ITEM_A, 3));
    }

    @Test
    void nullItemsAreNeverReplaced() {
        assertFalse(CanonicalRules.shouldReplace(false, null, 0, ITEM_A, 0));
        assertFalse(CanonicalRules.shouldReplace(false, ITEM_A, 0, null, 0));
    }
}
