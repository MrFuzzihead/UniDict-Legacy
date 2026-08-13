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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.unidict.MetaItem;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

@SuppressWarnings("unused")
public final class ResourceHandler {

    static final Set<ItemStack> keepOneEntryBlackSet = new HashSet<>();

    public final Collection<Resource<UniResourceContainer>> resources;

    private final TIntObjectMap<UniAttributes<UniResourceContainer>> individualStackAttributes = new TIntObjectHashMap<>();
    private final Map<String, UniResourceContainer> containerMap = new HashMap<>();
    private final Map<String, Resource<UniResourceContainer>> resourceMap;

    ResourceHandler(@Nonnull final Map<String, Resource<UniResourceContainer>> resourceMap) {
        resources = (this.resourceMap = resourceMap).values();
    }

    /** Records an item that belongs to a keep-one-entry blacklisted mod (fed by the comparator). */
    public static void addToKeepOneEntryModBlackSet(@Nonnull final ItemStack itemStack) {
        keepOneEntryBlackSet.add(itemStack);
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
     */
    public ItemStack getMainItemStack(final ItemStack thing) {
        final UniAttributes<UniResourceContainer> attributesOfThing = get(thing);
        return (attributesOfThing != null) ? attributesOfThing.uniResourceContainer.getMainEntry(thing.stackSize)
            : thing;
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
