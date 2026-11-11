package com.mrfuzzihead.unidict.common;

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

    public void add(final ItemStack canonical) {
        if (canonical == null || canonical.getItem() == null) return;
        index.put(canonical.getItem(), canonical.getItemDamage(), Boolean.TRUE);
    }

    public boolean contains(final ItemStack candidate) {
        return candidate != null && candidate.getItem() != null
            && index.get(candidate.getItem(), candidate.getItemDamage()) != null;
    }
}
