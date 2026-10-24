package com.mrfuzzihead.unidict.pure;

/**
 * Pure (Minecraft-free) decisions for resolving a stack to its canonical ("main") entry, extracted
 * from {@code ResourceHandler#getMainItemStack} so the no-op and data-preservation rules are
 * T1-testable without {@code net.minecraft*} imports (docs/TestPlan.md rule 2).
 *
 * <p>
 * Two rules live here:
 * <ol>
 * <li><b>No-op identity</b> — when the stack in hand already <em>is</em> the canonical item+damage, the
 * caller keeps its instance. Without this the resolver allocated a fresh stack for every modelled
 * item, so every integration's reference-based "did it change?" test reported a change even when
 * nothing changed: the {@code /unidict report} counts were inflated and the server-start re-runs were
 * not true no-ops (docs/CODE_REVIEW.md BUG-6 / OPT-1).</li>
 * <li><b>NBT preservation</b> — a stack carrying an NBT tag compound is never replaced. The canonical
 * entry is a bare {@code new ItemStack(item, size, meta)} (no tag), so substituting it silently
 * deleted data; this mirrors the conservative rule {@code UnifyDrops} already applied at drop time
 * (docs/CODE_REVIEW.md BUG-4).</li>
 * </ol>
 */
public final class CanonicalRules {

    private CanonicalRules() {}

    /**
     * Whether {@code input} must be replaced by the canonical entry.
     *
     * @param hasTagCompound whether the input stack carries an NBT tag compound
     * @param inputItem      the input's {@code Item} (compared by identity; {@code null} = absent)
     * @param inputDamage    the input's item damage / metadata
     * @param mainItem       the canonical entry's {@code Item} (identity)
     * @param mainDamage     the canonical entry's item damage / metadata
     * @return {@code false} when the input is tagged (never mutate it) or already has the canonical
     *         item+damage (no-op — keep the caller's instance); {@code true} only when a genuine
     *         substitution is required
     */
    public static boolean shouldReplace(final boolean hasTagCompound, final Object inputItem, final int inputDamage,
        final Object mainItem, final int mainDamage) {
        if (hasTagCompound) return false;
        if (inputItem == null || mainItem == null) return false;
        return inputItem != mainItem || inputDamage != mainDamage;
    }
}
