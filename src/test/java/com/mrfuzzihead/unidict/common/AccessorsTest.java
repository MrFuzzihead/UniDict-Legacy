package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * T1 tests for {@link Accessors} — the defensive mixin-seam access used at every integration cast site
 * (docs/CODE_REVIEW.md BUG-9). Zero Minecraft imports: a fake interface stands in for a mixin-added one.
 */
class AccessorsTest {

    /** Stand-in for a mixin-added accessor interface. */
    private interface Seam {
    }

    private static final class SeamImpl implements Seam {
    }

    @Test
    void returnsTheCandidateWhenItImplementsTheSeam() {
        final Seam seam = new SeamImpl();
        assertSame(seam, Accessors.as(seam, Seam.class));
    }

    @Test
    void returnsNullInsteadOfThrowingWhenTheSeamIsNotApplied() {
        // This is exactly the "mixin not applied" case: the object exists but does not implement the seam.
        assertNull(Accessors.as(new Object(), Seam.class));
        assertNull(Accessors.as("not a seam", Seam.class));
    }

    @Test
    void nullInputsAreSafe() {
        assertNull(Accessors.as(null, Seam.class));
        assertNull(Accessors.as(new SeamImpl(), null));
        assertNull(Accessors.as(null, null));
    }
}
