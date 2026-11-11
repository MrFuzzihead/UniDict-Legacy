package com.mrfuzzihead.unidict.common;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.pure.IdentityDamageIndex;

/**
 * O(1) item+damage membership for the canonical entries of a resource model.
 *
 * <p>
 * NEI hiding and the verify/report diagnostics both need "is this stack the main entry of any
 * unified container?" on a per-variant loop. This index replaces a linear scan with an identity-keyed
 * damage map; the key is the item instance (remap-safe) plus damage, not the stack identity.
 */
public final class CanonicalIndex {

    private final IdentityDamageIndex<Boolean> index = new IdentityDamageIndex<>();

    public void clear() {
        index.clear();
    }

    /** Adds a canonical entry from its immutable item+damage facts, without allocating a stack. */
    public void add(final Item item, final int damage) {
        if (item == null) return;
        index.put(item, damage, Boolean.TRUE);
    }

    /** Convenience overload for callers that already hold a canonical stack. */
    public void add(final ItemStack canonical) {
        if (canonical == null) return;
        add(canonical.getItem(), canonical.getItemDamage());
    }

    public boolean contains(final ItemStack candidate) {
        return candidate != null && candidate.getItem() != null
            && index.get(candidate.getItem(), candidate.getItemDamage()) != null;
    }
}
