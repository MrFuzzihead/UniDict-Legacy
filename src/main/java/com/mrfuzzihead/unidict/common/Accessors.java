package com.mrfuzzihead.unidict.common;

/**
 * Defensive access to a mixin-added interface ("seam") on a game object.
 *
 * <p>
 * Every accessor mixin makes its target class implement an interface; production code then casts the
 * object to that interface. If the mixin was <b>not</b> applied — another coremod rewriting the same class,
 * a version mismatch, a future Mixin rejecting the merge — the plain cast throws
 * {@link ClassCastException}, which an integration's outer {@code catch} can only turn into "this whole
 * integration silently stopped working" (docs/CODE_REVIEW.md BUG-9). Going through {@link #as} makes the
 * failure local and diagnosable: the caller skips that machine with a warning, and the dev harness emits a
 * {@code seam=…} FAIL line (see {@code VerifyHarness.checkSeam}).
 *
 * <p>
 * Pure Java (no Minecraft imports), so the decision is T1-testable.
 */
public final class Accessors {

    private Accessors() {}

    /**
     * @param candidate the game object that should implement {@code type} (may be {@code null})
     * @param type      the mixin-added interface (may be {@code null})
     * @param <T>       the interface type
     * @return {@code candidate} as {@code type} when it implements it, else {@code null}
     */
    public static <T> T as(final Object candidate, final Class<T> type) {
        if (candidate == null || type == null) return null;
        return type.isInstance(candidate) ? type.cast(candidate) : null;
    }
}
