package com.mrfuzzihead.unidict.pure;

/*
 * Identity-keyed, damage-aware index — the terminal lookup behind
 * ResourceHandler#individualStackAttributes. No net.minecraft* imports so it is T1-testable.
 * Why this exists (bug fixed 2026-08-1x, "UnifyDrops swaps drops to unrelated ores"):
 * the index used to be a single TIntObjectMap keyed by MetaKey.forItemAndDamage(registryId, damage).
 * That key bakes in the numeric registry id, which Forge REASSIGNS at runtime (FMLModIdMappingEvent:
 * "Fixed item id mismatch <name>: 6902 (init) -> 6937 (map)"). The index is built once at postInit
 * and lives for the whole session, so after a remap it is keyed by stale ids — and because a remap
 * merely shifts every mod's ids into each other's old slots, a lookup for item A can hit item B's
 * entry and get B's canonical stack back. That is how an EtF Amethyst Shard drop came back as an
 * aluminum ore block while keeping its drop count.
 * Keys here are the Item *instance* (identity), which a remap does not touch, and the damage value,
 * which no remap touches. MetaKey keeps its role for one-shot key math inside a single pass
 * (recipe/cumulative keys); it must never be the key of a map that outlives postInit.
 */

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A two-level index: an identity-keyed outer map (the item itself, compared by {@code ==}) and a
 * damage-keyed inner map, with {@link MetaKey#WILDCARD} honoured as a fallback for damage-specific
 * misses.
 *
 * <p>
 * Deliberately <b>not</b> keyed by anything the game can renumber at runtime (registry id, block id,
 * ore-dictionary id): entries stored here survive {@code FMLModIdMappingEvent} and still resolve.
 *
 * @param <V> the indexed value
 */
public final class IdentityDamageIndex<V> {

    /** Outer key is the item instance — {@link IdentityHashMap} compares with {@code ==}, never {@code equals}. */
    private final Map<Object, Map<Integer, V>> byKey = new IdentityHashMap<>();

    /**
     * Stores {@code value} under {@code (key, damage)}. Null keys and values are ignored so a
     * partially-built index can never throw at populate time.
     *
     * @param key    the identity key (production: the {@code Item} instance)
     * @param damage the stack damage, or {@link MetaKey#WILDCARD} to register a damage-independent entry
     * @param value  the value to index
     */
    public void put(final Object key, final int damage, final V value) {
        if (key == null || value == null) return;
        Map<Integer, V> byDamage = byKey.get(key);
        if (byDamage == null) {
            byDamage = new HashMap<>(4);
            byKey.put(key, byDamage);
        }
        byDamage.put(damage, value);
    }

    /**
     * Looks up {@code (key, damage)}, falling back to a {@link MetaKey#WILDCARD} entry registered for
     * the same key (an Ore Dictionary entry registered as {@code new ItemStack(item, 1, WILDCARD)}
     * must match every damage of that item).
     *
     * @return the indexed value, or {@code null} when neither the exact nor the wildcard entry exists
     */
    public V get(final Object key, final int damage) {
        if (key == null) return null;
        final Map<Integer, V> byDamage = byKey.get(key);
        if (byDamage == null) return null;
        final V exact = byDamage.get(damage);
        return (exact != null) ? exact : byDamage.get(MetaKey.WILDCARD);
    }

    /** @return {@code true} when {@code (key, damage)} (or its wildcard form) resolves. */
    public boolean contains(final Object key, final int damage) {
        return get(key, damage) != null;
    }

    /** Drops every entry, keeping the (identity) map instance — used when re-indexing. */
    public void clear() {
        byKey.clear();
    }

    /** @return {@code true} when nothing is indexed. */
    public boolean isEmpty() {
        return byKey.isEmpty();
    }
}
