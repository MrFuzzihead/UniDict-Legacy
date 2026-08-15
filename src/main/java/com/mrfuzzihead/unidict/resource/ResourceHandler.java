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
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.mrfuzzihead.unidict.Config;
import com.mrfuzzihead.unidict.MetaItem;
import com.mrfuzzihead.unidict.common.Util;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

@SuppressWarnings("unused")
public final class ResourceHandler {

    public final Collection<Resource<UniResourceContainer>> resources;

    /**
     * Whether a variant belongs to a mod blacklisted from NEI hiding: its owning mod is in
     * {@code keepOneEntryModBlackSet}. This is the mod-level exemption fed into the NEI hide-set
     * builder ({@code SelectionRules.hiddenIndices}) alongside the kind-level {@code hideInNEIBlackSet};
     * such variants stay visible in NEI while {@code autoHideInNEI} collapses the rest. The config key
     * keeps upstream's "keep-one-entry" name for back-compat, but the (deferred) keepOneEntry feature
     * does not drive hiding — {@code autoHideInNEI} does (TODO.md P0 #1/#2).
     */
    public static boolean isKeepOneEntryBlacklisted(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        return Config.get().keepOneEntryModBlackSet.contains(Util.getModName(stack));
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
