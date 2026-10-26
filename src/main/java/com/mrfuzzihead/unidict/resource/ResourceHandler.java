package com.mrfuzzihead.unidict.resource;

/*
 * Rebuilt from wanion.unidict.resource.ResourceHandler (WanionCane, MPL-2.0).
 * Kept as the public read API + populateIndividualStackAttributes (docs/PLAN.md §M4). The DI
 * dependency ({@code IDependence}) is gone (M2); instances are constructed directly with a
 * resource map and published via UniDict.statics.
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.common.Util;
import com.mrfuzzihead.unidict.pure.CanonicalRules;
import com.mrfuzzihead.unidict.pure.IdentityDamageIndex;
import com.mrfuzzihead.unidict.pure.config.ConfigData;

import cpw.mods.fml.common.registry.GameData;

@SuppressWarnings("unused")
public final class ResourceHandler {

    public final Collection<Resource<UniResourceContainer>> resources;

    /**
     * Whether a variant belongs to a mod blacklisted from NEI hiding: its owning mod is in
     * {@code autoHideInNEIModBlackSet} ({@code autoHideInNEIModBlackList}). This is the mod-level
     * exemption fed into the NEI hide-set builder ({@code SelectionRules.hiddenIndices}) alongside the
     * kind-level {@code hideInNEIBlackSet}; such variants stay visible in NEI while
     * {@code autoHideInNEI} collapses the rest. The legacy {@code keepOneEntryModBlackList} key maps to
     * the same field (TODO.md P0 #1).
     */
    public static boolean isModBlacklisted(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        return Config.get().autoHideInNEIModBlackSet.contains(Util.getModName(stack));
    }

    /**
     * Whether an item is "protected" from canonicalization and NEI hiding. Matches two configurable
     * sets, OR'd together (TODO.md P0 #1):
     * <ul>
     * <li>{@code protectedOreDictionaryNames} — the item is a member of an OD entry whose name
     * contains one of the substrings (default {@code "raw"} keeps raw metals tagged {@code rawCopper}/…
     * as the mined/processing form).</li>
     * <li>{@code protectedItemNames} — the item's registered {@code modid:path} name contains one of
     * the substrings (for carve-outs with no distinct OD tag, e.g. {@code "EtFuturum:block_copper"}
     * keeps a mod's decorative copper block, incl. its aged/oxidized variants).</li>
     * </ul>
     * Protected items are returned unchanged by {@link #getMainItemStack} — so a mined raw-metal or a
     * protected decorative block is preserved (craftable + non-canonicalized) — and are exempt from
     * NEI hiding.
     */
    public static boolean isProtected(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        final ConfigData config = Config.get();
        final Set<String> odTokens = config.protectedOreDictionaryNames;
        final Set<String> itemSubstrings = config.protectedItemNames;
        if (odTokens.isEmpty() && itemSubstrings.isEmpty()) return false;
        try {
            for (final int oreId : OreDictionary.getOreIDs(stack)) {
                final String name = OreDictionary.getOreName(oreId);
                for (final String token : odTokens) if (name.contains(token)) return true;
            }
        } catch (final Exception ignored) {
            // Never let an OD membership lookup abort canonicalization.
        }
        if (!itemSubstrings.isEmpty() && stack.getItem() != null) {
            try {
                final String registry = GameData.getItemRegistry()
                    .getNameForObject(stack.getItem());
                if (registry != null)
                    for (final String token : itemSubstrings) if (registry.contains(token)) return true;
            } catch (final Exception ignored) {
                // Never let a registry name lookup abort canonicalization.
            }
        }
        return false;
    }

    /**
     * Whether an item is a configured <b>canonical override</b>: its registered {@code modid:path} name
     * contains one of the {@code canonicalItemNames} substrings. Such an item is promoted to the main
     * entry of its unified container regardless of owner priority — used to resolve shared-OD-tag craft
     * conflicts (e.g. making EtF's copper block the one canonical {@code blockCopper}, so TF's colliding
     * recipe output is rewritten to it too and only one block is craftable).
     */
    public static boolean isCanonicalPreferred(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        final Set<String> canonical = Config.get().canonicalItemNames;
        if (canonical.isEmpty()) return false;
        try {
            final String registry = GameData.getItemRegistry()
                .getNameForObject(stack.getItem());
            if (registry != null) for (final String token : canonical) if (registry.contains(token)) return true;
        } catch (final Exception ignored) {
            // Never let a registry name lookup abort selection.
        }
        return false;
    }

    /**
     * The variant → resource/container index behind {@link #getMainItemStack}.
     *
     * <p>
     * Keyed by the <b>{@link Item} instance and its damage</b>, never by the numeric registry id: Forge
     * reassigns registry ids at runtime ({@code FMLModIdMappingEvent}) while this index is built once at
     * postInit and lives for the whole session. A numeric key would go stale on every remap — and since a
     * remap shifts each mod's ids into another mod's old slots, a stale key does not merely miss, it
     * <em>cross-wires</em>: looking up item A hit item B's entry and returned B's canonical stack. That is
     * what made EtF Amethyst Shard drops come back as aluminum/gold ore blocks (count preserved, because
     * {@code getMainEntry(size)} keeps the caller's stack size). Item identity survives a remap; ids do not.
     */
    private final IdentityDamageIndex<UniAttributes<UniResourceContainer>> individualStackAttributes = new IdentityDamageIndex<>();
    private final Map<String, UniResourceContainer> containerMap = new HashMap<>();
    private final Map<String, Resource<UniResourceContainer>> resourceMap;
    /**
     * The live Ore Dictionary names captured when the model was built. The dev harness compares it with
     * the current dictionary at load-complete to report late registrations the one-shot snapshot cannot
     * see (docs/CODE_REVIEW.md BUG-8 Phase B). Empty when a handler is constructed without a snapshot
     * (the T2 tests), in which case the check is skipped.
     */
    private final Set<String> dictionarySnapshot;

    ResourceHandler(@Nonnull final Map<String, Resource<UniResourceContainer>> resourceMap) {
        this(resourceMap, java.util.Collections.emptyList());
    }

    ResourceHandler(@Nonnull final Map<String, Resource<UniResourceContainer>> resourceMap,
        @Nonnull final Collection<String> dictionarySnapshot) {
        resources = (this.resourceMap = resourceMap).values();
        this.dictionarySnapshot = java.util.Collections
            .unmodifiableSet(new java.util.LinkedHashSet<>(dictionarySnapshot));
    }

    /** @return the unmodifiable Ore Dictionary names captured at model build time (see the field). */
    public Set<String> getDictionarySnapshot() {
        return dictionarySnapshot;
    }

    public boolean exists(final ItemStack thing) {
        return get(thing) != null;
    }

    public boolean resourceExists(@Nonnull final String name) {
        return resourceMap.containsKey(name);
    }

    public boolean containerExists(@Nonnull final String name) {
        return containerMap.containsKey(name);
    }

    private UniAttributes<UniResourceContainer> get(final ItemStack thing) {
        if (thing == null) return null;
        final Item item = thing.getItem();
        return (item == null) ? null : individualStackAttributes.get(item, item.getDamage(thing));
    }

    public UniResourceContainer getContainer(final String name) {
        return containerMap.get(name);
    }

    public UniResourceContainer getContainer(final ItemStack thing) {
        final UniAttributes<UniResourceContainer> attributesOfThing = get(thing);
        return (attributesOfThing != null) ? attributesOfThing.uniResourceContainer : null;
    }

    /**
     * The canonical ("main") entry for a stack, preserving its stack size. Returns the stack itself
     * (by identity) when it is not part of any unified resource, when it already <em>is</em> the
     * canonical entry, or when it carries NBT.
     *
     * <p>
     * Two resolution paths:
     * <ol>
     * <li><b>Exact index</b> ({@link #individualStackAttributes}) — the fast path used by the
     * machine/integration rewrites, keyed by the {@link Item} instance and item damage taken from the
     * OD snapshot (wildcard-aware; never by registry id — see that field's javadoc).</li>
     * <li><b>Ore-Dictionary membership fallback</b> — when the concrete stack in hand is not in that
     * pre-index (its damage/wildcard differs from the OD-registered value, common for hand-held /
     * dropped items), resolve it through Forge's live OD lookups: any stack that IS an {@code
     * ingotCopper} member maps to that resource's main entry. This is what makes drop-time
     * unification robust.</li>
     * </ol>
     *
     * <p>
     * The final substitution decision is delegated to the pure {@link CanonicalRules#shouldReplace}
     * so the no-op and data-preservation guarantees are T1-tested: an already-canonical stack is
     * returned <b>by identity</b> (making every integration's reference check, the report counts and
     * the server-start re-runs correct) and an NBT-bearing stack is never replaced (the canonical
     * entry cannot carry its tag).
     */
    public ItemStack getMainItemStack(final ItemStack thing) {
        if (thing == null || thing.getItem() == null) return thing;
        // Protected items (e.g. EtF raw copper via the default "raw" OD-name substring) are never
        // canonicalized — a mined raw-metal drop stays raw instead of becoming a mod's ore block
        // (TODO.md P0 #1). This also shields protected items from machine input/output rewrites.
        if (isProtected(thing)) return thing;
        final UniResourceContainer container = containerOf(thing);
        if (container == null) return thing;
        // Decide without allocating first: only build the canonical stack when it will actually replace the
        // input. A canonical drop (or a re-run over already-rewritten recipes) therefore costs nothing
        // beyond the index lookups (docs/CODE_REVIEW.md OPT-1).
        if (!CanonicalRules.shouldReplace(
            thing.hasTagCompound(),
            thing.getItem(),
            thing.getItemDamage(),
            container.getMainEntryItem(),
            container.getMainEntryDamage())) return thing;
        return container.getMainEntry(thing.stackSize);
    }

    /** Resolves a stack to its modelled container: exact index first, then the OD-name fallback. */
    private UniResourceContainer containerOf(final ItemStack thing) {
        final UniAttributes<UniResourceContainer> attributesOfThing = get(thing);
        if (attributesOfThing != null) return attributesOfThing.uniResourceContainer;
        return containerForOreName(thing);
    }

    /**
     * Resolves a stack to a modeled container via its Ore-Dictionary membership, when the exact-hash
     * index missed it. Returns the first modeled container whose OD name the stack belongs to, or
     * {@code null}. Defensive: never throws on an unregistered / unusual stack.
     */
    private UniResourceContainer containerForOreName(final ItemStack thing) {
        if (thing == null || thing.getItem() == null) return null;
        try {
            for (final int oreId : OreDictionary.getOreIDs(thing)) {
                final UniResourceContainer container = containerMap.get(OreDictionary.getOreName(oreId));
                if (container != null) return container;
            }
        } catch (final Exception ignored) {
            // Never let a resolution miss abort the caller (drop / machine rewrite).
        }
        return null;
    }

    /**
     * Maps every variant to its resource/container so {@link #getMainItemStack} can resolve, keyed by
     * item identity + damage (see {@link #individualStackAttributes}). Safe to re-run: the index and
     * the container lookup are cleared first, so a later re-index (or a resource removed before the
     * first index) cannot leave duplicate/stale keys behind.
     */
    void populateIndividualStackAttributes() {
        individualStackAttributes.clear();
        containerMap.clear();
        for (final Resource<UniResourceContainer> resource : new ArrayList<>(resources)) {
            for (final UniResourceContainer container : resource.getChildrenCollection()) {
                containerMap.put(container.name, container);
                final UniAttributes<UniResourceContainer> uniAttributes = new UniAttributes<>(resource, container);
                // Re-derive the (item, damage) pairs from the container's own snapshot instead of a
                // precomputed id hash: the hash baked in load-time registry ids and went stale on remap.
                for (final ItemStack entry : container.getEntries()) {
                    final Item item = (entry == null) ? null : entry.getItem();
                    if (item != null) individualStackAttributes.put(item, item.getDamage(entry), uniAttributes);
                }
            }
        }
    }
}
