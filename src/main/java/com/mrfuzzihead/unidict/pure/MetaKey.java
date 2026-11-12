package com.mrfuzzihead.unidict.pure;

/**
 * Shared Ore Dictionary wildcard constant.
 *
 * <p>
 * The old item-key packing helpers were removed as dead code: the resource model keys by the
 * {@code Item} instance and damage via {@link IdentityDamageIndex}, never by packed registry ids
 * (which Forge reassigns at runtime). {@link #WILDCARD} is still the wildcard sentinel used by
 * {@link IdentityDamageIndex} and the report/verify descriptors.
 */
public final class MetaKey {

    /** Forge's {@code OreDictionary.WILDCARD_VALUE} (32767), as a plain constant — no MC import. */
    public static final int WILDCARD = 32767;

    private MetaKey() {}
}
