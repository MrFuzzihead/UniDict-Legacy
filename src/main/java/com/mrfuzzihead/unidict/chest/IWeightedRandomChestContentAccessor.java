package com.mrfuzzihead.unidict.chest;

import net.minecraft.item.ItemStack;

/**
 * T2 seam over the (vanilla, {@code private}) {@code theItemId} field of
 * {@link net.minecraft.util.WeightedRandomChestContent} — the item a chest loot entry can generate.
 *
 * <p>
 * Upstream (1.12.2) mutated this field directly because it is {@code public} there; in <b>MC 1.7.10</b>
 * it is {@code private} (non-final) and cannot be touched from another class without reflection. This
 * seam exposes get+set so {@code ChestIntegration} can still rewrite the entry
 * <em>in place</em> (BB-3: same list element, only the item reference changes; weights/min/max stay
 * untouched). Live impl is {@code WeightedRandomChestContentMixin} (mixins.early, targets the vanilla
 * field with {@code remap = true}); tests use {@code FakeWeightedRandomChestContent}. Plain seam,
 * outside the mixin packages (rule 6).
 */
public interface IWeightedRandomChestContentAccessor {

    /** @return the current item this loot entry can generate ({@code theItemId}) */
    ItemStack getTheItemId();

    /** Replaces the entry's item ({@code theItemId}) in place. */
    void setTheItemId(ItemStack item);
}
