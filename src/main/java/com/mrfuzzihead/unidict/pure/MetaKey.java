package com.mrfuzzihead.unidict.pure;

/**
 * Pure packing of a Minecraft item into a single {@code int} key, extracted from
 * {@code wanion.unidict.MetaItem} (WanionCane, MPL-2.0). Only primitive math here — NO
 * {@code net.minecraft*} / {@code net.minecraftforge*} imports — so it is T1-testable on a bare
 * JVM (docs/TestPlan.md rule 2). The Minecraft-facing wrapper ({@code MetaItem}) is ported in M4.
 *
 * <p>
 * Layout (faithful to upstream): the item's registry id occupies the low 16 bits; bit 16
 * ({@link #ITEM_FLAG}) marks an item-level key with no damage; an item+damage key packs
 * {@code damage + 1} shifted into the upper bits (so damage {@code 0} yields the same key as an
 * item-level key, matching upstream's convention).
 *
 * <p>
 * Note for M4: upstream {@code MetaItem.toItemStack} recovers the id as {@code key ^ (key & 65536)},
 * which is lossy once {@code damage + 1} sets bit 16 or higher. That reconstruction belongs to the
 * MC glue and is not ported here; M4 must use {@link #itemIdOf(int)} ({@code key & 0xFFFF}) instead.
 */
public final class MetaKey {

    /** Forge's {@code OreDictionary.WILDCARD_VALUE} (32767), as a plain constant — no MC import. */
    public static final int WILDCARD = 32767;

    /** Bit 16 ({@code 1 << 16}): set when the key refers to an item-without-damage (item-level). */
    public static final int ITEM_FLAG = 1 << 16;

    private MetaKey() {}

    /**
     * Packs an item registry id and its damage into a single key.
     *
     * @param id     the item's registry id
     * @param damage the stack damage, or {@link #WILDCARD}
     * @return {@code 0} when {@code id <= 0}; otherwise {@code id | (damage + 1) << 16}, or just
     *         {@code id} when {@code damage == WILDCARD}.
     */
    public static int forItemAndDamage(final int id, final int damage) {
        if (id <= 0) return 0;
        if (damage == WILDCARD) return id;
        return id | ((damage + 1) << 16);
    }

    /**
     * Packs an item-level (no-damage) registry id into a single key.
     *
     * @param id the item's registry id
     * @return {@code 0} when {@code id <= 0}; otherwise {@code id | ITEM_FLAG}.
     */
    public static int forItem(final int id) {
        return id > 0 ? id | ITEM_FLAG : 0;
    }

    /**
     * Recovers the item registry id from a key.
     *
     * @return {@code key & 0xFFFF} (the low 16 bits that hold the registry id).
     */
    public static int itemIdOf(final int key) {
        return key & 0xFFFF;
    }

    /**
     * Recovers the meta/damage value packed in a key (upper bits).
     *
     * @return {@code key >> 16}; for a {@link #forItemAndDamage(int, int)} key this is
     *         {@code damage + 1}.
     */
    public static int metaOf(final int key) {
        return key >> 16;
    }

    /** @return {@code true} when {@code key} represents an item-level (no-damage) entry. */
    public static boolean isItemLevel(final int key) {
        return (key & ITEM_FLAG) != 0;
    }

    /** @return {@code true} when {@code key} is a valid (non-zero) item key. */
    public static boolean isValid(final int key) {
        return key > 0;
    }
}
