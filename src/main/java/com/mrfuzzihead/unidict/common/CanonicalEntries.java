package com.mrfuzzihead.unidict.common;

import java.util.Collection;

import net.minecraft.item.ItemStack;

/**
 * Item+damage membership for a collection of canonical entries.
 *
 * <p>
 * NEI hiding and the transparency/verify reports must agree on what "canonical elsewhere" means: an
 * item is exempt when it is the main entry of <b>any</b> unified container. The entries are snapshots
 * and the candidate may be a different {@link ItemStack} instance, so compare the item identity and
 * damage, never the stack identity.
 */
public final class CanonicalEntries {

    private CanonicalEntries() {}

    /**
     * @param canonicalEntries the main entries of every unified container (may be {@code null})
     * @param stack            the candidate variant (may be {@code null})
     * @return {@code true} when the candidate matches a canonical entry by item identity and damage
     */
    public static boolean contains(final Collection<ItemStack> canonicalEntries, final ItemStack stack) {
        if (canonicalEntries == null || stack == null || stack.getItem() == null) return false;
        for (final ItemStack canonical : canonicalEntries) {
            if (canonical == null || canonical.getItem() == null) continue;
            if (canonical.getItem() == stack.getItem() && canonical.getItemDamage() == stack.getItemDamage())
                return true;
        }
        return false;
    }
}
