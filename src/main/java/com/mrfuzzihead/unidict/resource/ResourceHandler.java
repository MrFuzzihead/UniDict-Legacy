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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.MetaItem;
import com.mrfuzzihead.unidict.common.Util;
import com.mrfuzzihead.unidict.pure.config.ConfigData;

import cpw.mods.fml.common.registry.GameData;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

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

    private final TIntObjectMap<UniAttributes<UniResourceContainer>> individualStackAttributes = new TIntObjectHashMap<>();
    private final Map<String, UniResourceContainer> containerMap = new HashMap<>();
    private final Map<String, Resource<UniResourceContainer>> resourceMap;

    ResourceHandler(@Nonnull final Map<String, Resource<UniResourceContainer>> resourceMap) {
        resources = (this.resourceMap = resourceMap).values();
    }

    public boolean exists(final int thingId) {
        return individualStackAttributes.containsKey(thingId);
    }

    public boolean exists(final ItemStack thing) {
        return (thing != null) && individualStackAttributes.containsKey(MetaItem.get(thing));
    }

    public boolean resourceExists(@Nonnull final String name) {
        return resourceMap.containsKey(name);
    }

    public boolean containerExists(@Nonnull final String name) {
        return containerMap.containsKey(name);
    }

    private UniAttributes<UniResourceContainer> get(final ItemStack thing) {
        return (thing == null || thing.getItem() == null) ? null : individualStackAttributes.get(MetaItem.get(thing));
    }

    public Resource<UniResourceContainer> getResource(final String resourceName) {
        return resourceMap.get(resourceName);
    }

    public Resource<UniResourceContainer> getResource(final ItemStack thing) {
        final UniAttributes<UniResourceContainer> attributesOfThing = get(thing);
        return (attributesOfThing != null) ? attributesOfThing.resource : null;
    }

    public UniResourceContainer getContainer(final String name) {
        return containerMap.get(name);
    }

    public UniResourceContainer getContainer(final ItemStack thing) {
        final UniAttributes<UniResourceContainer> attributesOfThing = get(thing);
        return (attributesOfThing != null) ? attributesOfThing.uniResourceContainer : null;
    }

    public String getContainerName(final ItemStack thing) {
        final UniAttributes<UniResourceContainer> attributesOfThing = get(thing);
        return (attributesOfThing != null) ? attributesOfThing.uniResourceContainer.name : null;
    }

    /**
     * The canonical ("main") entry for a stack, preserving its stack size. Returns the stack itself
     * when it is not part of any unified resource.
     *
     * <p>
     * Two resolution paths:
     * <ol>
     * <li><b>Exact-hash index</b> ({@link #individualStackAttributes}) — the fast path used by the
     * machine/integration rewrites, keyed by {@code (registeredId, item damage)} from the OD
     * snapshot.</li>
     * <li><b>Ore-Dictionary membership fallback</b> — when the concrete stack in hand is not in that
     * pre-index (its damage/wildcard differs from the OD-registered value, common for hand-held /
     * dropped items), resolve it through Forge's live OD lookups: any stack that IS an {@code
     * ingotCopper} member maps to that resource's main entry. This is what makes drop-time
     * unification robust.</li>
     * </ol>
     */
    public ItemStack getMainItemStack(final ItemStack thing) {
        // Protected items (e.g. EtF raw copper via the default "raw" OD-name substring) are never
        // canonicalized — a mined raw-metal drop stays raw instead of becoming a mod's ore block
        // (TODO.md P0 #1). This also shields protected items from machine input/output rewrites.
        if (isProtected(thing)) return thing;
        final UniAttributes<UniResourceContainer> attributesOfThing = get(thing);
        if (attributesOfThing != null) return attributesOfThing.uniResourceContainer.getMainEntry(thing.stackSize);
        final UniResourceContainer byOreName = containerForOreName(thing);
        return (byOreName != null) ? byOreName.getMainEntry(thing.stackSize) : thing;
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

    public List<ItemStack> getMainItemStackList(@Nonnull final Collection<ItemStack> things) {
        return things.stream()
            .map(this::getMainItemStack)
            .collect(Collectors.toList());
    }

    public void setMainItemStacks(@Nonnull final List<ItemStack> thingList) {
        for (int i = 0; i < thingList.size(); i++) thingList.set(i, getMainItemStack(thingList.get(i)));
    }

    public ItemStack[] getMainItemStacks(@Nonnull final ItemStack[] things) {
        for (int i = 0; i < things.length; i++) things[i] = getMainItemStack(things[i]);
        return things;
    }

    public void setMainItemStacks(@Nonnull final Object[] things) {
        for (int i = 0; i < things.length; i++)
            if (things[i] instanceof ItemStack) things[i] = getMainItemStack((ItemStack) things[i]);
    }

    public List<Resource<?>> getResources(final long kinds) {
        return Resource.getResources(resources, kinds);
    }

    public List<Resource<?>> getResources(final long... kinds) {
        return Resource.getResources(resources, kinds);
    }

    /** Maps every variant's key to its resource/container so {@link #getMainItemStack} can resolve. */
    void populateIndividualStackAttributes() {
        for (final Resource<UniResourceContainer> resource : new ArrayList<>(resources)) {
            for (final UniResourceContainer container : resource.getChildrenCollection()) {
                containerMap.put(container.name, container);
                final UniAttributes<UniResourceContainer> uniAttributes = new UniAttributes<>(resource, container);
                final int[] hashes = container.getHashes();
                if (hashes != null) for (final int hash : hashes) individualStackAttributes.put(hash, uniAttributes);
            }
        }
    }
}
