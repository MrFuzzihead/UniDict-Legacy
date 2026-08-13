package com.mrfuzzihead.unidict.oredict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.pure.MetaKey;

import cpw.mods.fml.common.registry.GameData;

/**
 * Read-only query surface over Forge's Ore Dictionary, rewritten for M3 to read every map through the
 * {@link IOreDictionaryAccessor} seam. There is no reflection here — the last
 * {@code Util.getField}/{@code setField} call sites from v1 step 13 are gone, replaced by the
 * {@code OreDictionaryMixin} accessors. Ported (behaviour) from {@code wanion.unidict.UniOreDictionary}
 * (WanionCane, MPL-2.0).
 *
 * <p>
 * Scope (docs/PLAN.md "Scope rework" + M3, docs/STATUS.md): <b>read-only</b>. The invasive mutation
 * surface ({@code removeFromElsewhere} / {@code keepOneEntry} collapse) is deliberately NOT ported.
 * {@code getFirstEntry} is kept for the IE crusher.
 *
 * <p>
 * Deviation notes (behaviour-preserving or strictly safer): id bounds are corrected to
 * {@code [0, size)} so {@code get}/{@code getUn} never throw on a bad id; unknown-name and empty-list
 * paths return {@code null} (or a guarded result) instead of throwing; {@code get(Collection)} returns
 * the actual union of entries.
 */
public final class UniOreDictionary {

    private static volatile UniOreDictionary instance;

    private final IOreDictionaryAccessor accessor;
    private final MetaItemProvider metaItem;
    private final Map<List<ItemStack>, String> entryToName = new IdentityHashMap<>();
    private final Map<Integer, String> stackToName = new HashMap<>();

    /**
     * @param accessor the live Ore Dictionary maps (production: {@link OreDictionaryBridge#instance()};
     *                 tests: {@code FakeOreDictionaryAccessor})
     * @param metaItem hashes an {@link ItemStack} to its {@code int} key
     */
    public UniOreDictionary(final IOreDictionaryAccessor accessor, final MetaItemProvider metaItem) {
        this.accessor = accessor;
        this.metaItem = metaItem;
        for (final Map.Entry<String, Integer> entry : accessor.getNameToId()
            .entrySet()) {
            final String name = entry.getKey();
            final List<ItemStack> entries = getUn(entry.getValue());
            if (entries == null) continue;
            for (final ItemStack stack : entries) {
                final int hash = metaItem.of(stack);
                if (!stackToName.containsKey(hash)) stackToName.put(hash, name);
            }
            entryToName.put(entries, name);
        }
    }

    /** @return the shared instance wired to the live bridge. Lazy; not touched in-game during M3. */
    public static UniOreDictionary instance() {
        if (instance == null) {
            synchronized (UniOreDictionary.class) {
                if (instance == null)
                    // TODO(M4): reuse MetaItem glue once it lands; this is the same id+damage math.
                    instance = new UniOreDictionary(OreDictionaryBridge.instance(), UniOreDictionary::gameDataMetaItem);
            }
        }
        return instance;
    }

    public String getName(final Object thing) {
        if (thing instanceof ItemStack) return stackToName.get(metaItem.of((ItemStack) thing));
        else if (thing instanceof List) return entryToName.get(thing);
        else return null;
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

    public ItemStack getFirstEntry(final String oreDictName) {
        final List<ItemStack> oreList = get(oreDictName);
        return (oreList != null && !oreList.isEmpty() && !oreDictName.isEmpty()) ? oreList.get(0)
            .copy() : null;
    }

    public ItemStack getLastEntry(final String oreDictName) {
        final List<ItemStack> oreList = get(oreDictName);
        return (oreList != null && !oreList.isEmpty() && !oreDictName.isEmpty()) ? oreList.get(oreList.size() - 1)
            .copy() : null;
    }

    public Set<ItemStack> get(final Collection<String> oreDictNames) {
        final Set<ItemStack> itemStacks = new HashSet<>();
        for (final String name : oreDictNames) {
            final List<ItemStack> list = get(name);
            if (list != null) itemStacks.addAll(list);
        }
        return itemStacks;
    }

    public List<ItemStack> get(final ItemStack thing) {
        final int thingId = metaItem.of(thing);
        final List<Integer> ids = accessor.getStackToId()
            .get(thingId);
        if (ids != null && !ids.isEmpty()) return accessor.getIdToStack()
            .get(ids.get(0));
        return null;
    }

    public List<Matcher> getThoseThatMatches(final String regex) {
        return getThoseThatMatches(Pattern.compile(regex));
    }

    public List<Matcher> getThoseThatMatches(final Pattern pattern) {
        final List<Matcher> matcherList = new ArrayList<>();
        for (final String name : accessor.getNameToId()
            .keySet()) {
            final Matcher matcher = pattern.matcher(name);
            if (matcher.find()) matcherList.add(matcher);
        }
        return matcherList;
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

    /** Live MetaItem provider for {@link #instance()}: registry id + damage via the pure {@link MetaKey}. */
    private static int gameDataMetaItem(final ItemStack stack) {
        final int damage = stack.getItemDamage();
        return MetaKey.forItemAndDamage(
            GameData.getItemRegistry()
                .getId(stack.getItem()),
            damage);
    }
}
