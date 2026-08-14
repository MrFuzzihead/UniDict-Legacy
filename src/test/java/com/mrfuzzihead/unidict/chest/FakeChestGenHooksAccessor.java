package com.mrfuzzihead.unidict.chest;

import java.util.List;
import java.util.Map;

import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

/**
 * Deterministic, in-memory {@link IChestGenHooksAccessor} for T2 tests — no MC statics, so tests can
 * drive {@code ChestIntegration} (M6) without a live game or the applied mixin.
 *
 * <p>
 * Test-only fake, kept out of the mixin packages (docs/TestPlan.md rule 6). {@link #getChestInfo()} is
 * not exercised by {@code rewriteCategory} (that seam reads {@link #getContents()} only) and returns
 * {@code null} here. {@code ChestGenHooks} appears only as a type reference in the signature (T2 allows
 * MC <em>types</em>, not MC <em>statics</em>).
 */
public final class FakeChestGenHooksAccessor implements IChestGenHooksAccessor {

    private final List<WeightedRandomChestContent> contents;

    public FakeChestGenHooksAccessor(final List<WeightedRandomChestContent> contents) {
        this.contents = contents;
    }

    @Override
    public Map<String, ChestGenHooks> getChestInfo() {
        return null; // not needed for the per-category rewrite seam under test
    }

    @Override
    public List<WeightedRandomChestContent> getContents() {
        return contents;
    }
}
