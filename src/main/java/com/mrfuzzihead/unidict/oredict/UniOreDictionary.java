package com.mrfuzzihead.unidict.oredict;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * Read-only query surface over Forge's Ore Dictionary, reading every map through the
 * {@link IOreDictionaryAccessor} seam (no reflection, no Minecraft statics).
 *
 * <p>
 * Scope: <b>read-only and minimal</b> — exactly what the resource model needs: name/id lookups, the
 * processed/unprocessed entry lists and an ordered name snapshot. The upstream mutation surface
 * ({@code removeFromElsewhere} / {@code keepOneEntry}) and the name-hashing helpers
 * ({@code getName}, {@code getFirstEntry}, {@code getLastEntry}, {@code get(ItemStack)},
 * {@code get(Collection)}, {@code getThoseThatMatches}) were removed as dead code
 * (docs/CODE_REVIEW.md BUG-19); the model builds its own taxonomy via {@code pure.OreNames}.
 *
 * <p>
 * Deviation note (strictly safer): id bounds are {@code [0, size)}, so a bad id returns {@code null}
 * instead of throwing.
 */
public final class UniOreDictionary {

    private static volatile UniOreDictionary instance;

    private final IOreDictionaryAccessor accessor;

    /**
     * @param accessor the live Ore Dictionary maps (production: {@link OreDictionaryBridge#instance()};
     *                 tests: {@code FakeOreDictionaryAccessor})
     */
    public UniOreDictionary(final IOreDictionaryAccessor accessor) {
        this.accessor = accessor;
    }

    /** @return the shared instance wired to the live bridge (lazy). */
    public static UniOreDictionary instance() {
        if (instance == null) {
            synchronized (UniOreDictionary.class) {
                if (instance == null) instance = new UniOreDictionary(OreDictionaryBridge.instance());
            }
        }
        return instance;
    }

    /**
     * @return every registered Ore Dictionary name, in the live map's iteration order (a copy — the live
     *         map can change while the dictionary is being populated)
     */
    public List<String> getOreNames() {
        return new ArrayList<>(
            accessor.getNameToId()
                .keySet());
    }

    public List<ItemStack> get(final String oreDictName) {
        return get(
            accessor.getNameToId()
                .get(oreDictName));
    }

    public List<ItemStack> get(final Integer oreDictId) {
        return checkId(oreDictId) ? accessor.getIdToStack()
            .get(oreDictId) : null;
    }

    public List<ItemStack> getUn(final Integer oreDictId) {
        return checkId(oreDictId) ? accessor.getIdToStackUn()
            .get(oreDictId) : null;
    }

    public List<ItemStack> getUn(final String oreDictName) {
        final Integer id = accessor.getNameToId()
            .get(oreDictName);
        return id == null ? null : getUn(id);
    }

    public Integer getId(final String oreDictName) {
        return accessor.getNameToId()
            .get(oreDictName);
    }

    private boolean checkId(final Integer oreDictId) {
        // Corrected bounds: [0, size). Upstream allowed id <= size (latent off-by-one / OOB).
        return oreDictId != null && oreDictId >= 0
            && oreDictId < accessor.getIdToStack()
                .size();
    }
}
