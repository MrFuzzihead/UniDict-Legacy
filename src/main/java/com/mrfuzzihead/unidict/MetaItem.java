package com.mrfuzzihead.unidict;

/*
 * Rebuilt from wanion.unidict.MetaItem (WanionCane, MPL-2.0).
 * This is the Minecraft-facing glue that folds an ItemStack / Item into the pure key space of
 * com.mrfuzzihead.unidict.pure.MetaKey (the id | damage<<16 arithmetic, ported in M1). It is kept
 * thin on purpose: every bit of decision logic lives in MetaKey, and this class only bridges the
 * registry lookups that cannot run off-game (docs/PLAN.md §M4).
 */

import java.util.Collection;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.mrfuzzihead.unidict.oredict.MetaItemProvider;
import com.mrfuzzihead.unidict.pure.MetaKey;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

@SuppressWarnings("unused")
public final class MetaItem {

    private static final FMLControlledNamespacedRegistry<Item> ITEM_REGISTRY = GameData.getItemRegistry();

    /** T2 seam provider: an {@link ItemStack} → key function usable where {@code MetaItemProvider} is needed. */
    public static final MetaItemProvider PROVIDER = MetaItem::get;

    private MetaItem() {}

    /**
     * The stable key for an {@link ItemStack}: its registry id plus material damage.
     *
     * @return {@code 0} for a null/empty stack or an unregistered item.
     */
    public static int get(final ItemStack itemStack) {
        Item item;
        if (itemStack == null || (item = itemStack.getItem()) == null) return 0;
        final int id = ITEM_REGISTRY.getId(item);
        return id > 0 ? MetaKey.forItemAndDamage(id, item.getDamage(itemStack)) : 0;
    }

    /** The stable item-level key (no damage) for an {@link Item}. */
    public static int get(final Item item) {
        if (item == null) return 0;
        final int id = ITEM_REGISTRY.getIDForObject(item);
        return id > 0 ? MetaKey.forItem(id) : 0;
    }

    /**
     * Reconstructs an {@link ItemStack} from a key (registry id + damage).
     *
     * <p>
     * Deviation from upstream: upstream recovered the id as {@code key ^ (key & 65536)}, which is
     * lossy once {@code damage + 1} sets bit 16. We use {@link MetaKey#itemIdOf(int)}
     * ({@code key & 0xFFFF}) instead (docs/PLAN.md §M1 note).
     */
    public static ItemStack toItemStack(final int metaItemKey) {
        if (!MetaKey.isValid(metaItemKey)) return null;
        final Item item = ITEM_REGISTRY.getObjectById(MetaKey.itemIdOf(metaItemKey));
        if (item == null) return null;
        final int damage = MetaKey.isItemLevel(metaItemKey) ? OreDictionary.WILDCARD_VALUE
            : Math.max(MetaKey.metaOf(metaItemKey) - 1, 0);
        return new ItemStack(item, 0, damage);
    }

    /** Sum of the keys of every stack (used for cumulative recipe keys in craft rewriting). */
    public static int getCumulative(final ItemStack... itemStacks) {
        int cumulativeKey = 0;
        if (itemStacks != null) for (final ItemStack itemStack : itemStacks) cumulativeKey += get(itemStack);
        return cumulativeKey;
    }

    public static int[] getArray(final Collection<ItemStack> itemStackCollection) {
        return getList(itemStackCollection).toArray();
    }

    public static TIntList getList(final Collection<ItemStack> itemStackCollection) {
        final TIntList keys = new TIntArrayList();
        if (itemStackCollection != null) {
            int hash;
            for (final ItemStack itemStack : itemStackCollection) if ((hash = get(itemStack)) != 0) keys.add(hash);
        }
        return keys;
    }

    public static TIntSet getSet(final Collection<ItemStack> itemStackCollection) {
        return new TIntHashSet(getList(itemStackCollection));
    }

    public static <E> void populateMap(final Collection<ItemStack> itemStackCollection, final TIntObjectMap<E> map,
        final E defaultValue) {
        if (itemStackCollection == null) return;
        for (final int id : getArray(itemStackCollection)) map.put(id, defaultValue);
    }

    public static void populateMap(final Collection<ItemStack> itemStackCollection, final TIntLongMap map,
        final long defaultValue) {
        if (itemStackCollection == null) return;
        for (final int id : getArray(itemStackCollection)) map.put(id, defaultValue);
    }
}
