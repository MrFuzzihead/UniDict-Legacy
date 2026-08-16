package com.mrfuzzihead.unidict.resource;

/*
 * Rebuilt from wanion.unidict.resource.Resource (WanionCane, MPL-2.0).
 * Behavior is ported faithfully with two deliberate fixes (see docs/PLAN.md M1):
 * 1. The kind limit is ENFORCED, not commented: registering more than {@link #MAX_KINDS} (64)
 * distinct kinds throws {@link IllegalStateException}, and kind bits use a long shift
 * ({@code 1L << n}) instead of an int shift so all 64 bits stay usable.
 * 2. The class is generic over {@link IResourceContainer} so its child mechanics are
 * testable against a pure fake with zero net.minecraft* imports (docs/TestPlan.md rules 2/6).
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import gnu.trove.impl.unmodifiable.TUnmodifiableLongObjectMap;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;

/**
 * A unified resource (e.g. "ingotIron") holding a set of child entries, one per "kind" bit
 * (ORE, INGOT, DUST, ...). The kind taxonomy is a global long bitfield shared by every instance.
 *
 * @param <E> the child-entry type ({@code UniResourceContainer} in production, a fake in tests).
 */
@SuppressWarnings("unused")
public class Resource<E extends IResourceContainer> {

    /** Maximum distinct kinds representable in the {@code long} bitfield used for kinds. */
    static final int MAX_KINDS = 64;

    private static final TObjectLongMap<String> nameToKind = new TObjectLongHashMap<>();
    private static final TLongObjectMap<String> kindToName = new TLongObjectHashMap<>();
    private static int totalKindsRegistered = 0;

    public final String name;

    private final TLongObjectMap<E> childrenMap = new TLongObjectHashMap<>();
    private final List<Resource<E>> copies = new ArrayList<>();
    private long children = 0;
    private boolean updated;

    public Resource(@Nonnull final String name) {
        this.name = name;
    }

    public Resource(@Nonnull final String name, @Nonnull final TLongObjectMap<E> containerMap) {
        this.name = name;
        containerMap.forEachValue(container -> {
            children |= container.getKind();
            return childrenMap.put(container.getKind(), container) == null;
        });
    }

    public long getChildren() {
        return children;
    }

    public E getChild(@Nonnull final String childName) {
        return childrenMap.get(nameToKind.get(childName));
    }

    public E getChild(final long kind) {
        return childrenMap.get(kind);
    }

    /**
     * Returns a copy of this resource holding only the children whose kind overlaps {@code kinds}.
     *
     * @param kinds the kind bit(s) to keep
     * @return a new {@link Resource} that shares the surviving children with this one
     */
    public Resource<E> filteredClone(final long kinds) {
        final TLongObjectMap<E> newChildrenMap = new TLongObjectHashMap<>();
        childrenMap.forEachEntry((child, container) -> {
            if ((child & kinds) > 0) newChildrenMap.put(child, container);
            return true;
        });
        final Resource<E> copiedResource = new Resource<>(name, newChildrenMap);
        copies.add(copiedResource);
        return copiedResource;
    }

    /**
     * Adds a child entry to this resource.
     *
     * @param child the entry to add
     * @return {@code true} if added; {@code false} if a child of that kind already exists or the
     *         entry's name does not end with this resource's name.
     */
    public boolean addChild(@Nonnull final E child) {
        final long kind = child.getKind();
        if ((children & kind) > 0 || !child.getName()
            .endsWith(name)) return false;
        children |= kind;
        childrenMap.put(kind, child);
        return true;
    }

    /** Reconciles every child (and prior {@link #filteredClone(long)} copies) with the Ore Dictionary. */
    public void updateEntries() {
        if (updated) return;
        else updated = true;
        for (final TLongIterator childrenIterator = childrenMap.keySet()
            .iterator(); childrenIterator.hasNext();) {
            long kindId = childrenIterator.next();
            if (childrenMap.get(kindId)
                .updateEntries()) continue;
            children &= ~kindId;
            childrenIterator.remove();
        }
        copies.forEach(Resource::updateEntries);
    }

    public Collection<E> getChildrenCollection() {
        return childrenMap.valueCollection();
    }

    @Override
    public String toString() {
        if (childrenMap.isEmpty()) return name + " = {}";
        final StringBuilder output = new StringBuilder(name + " = {");
        for (final TLongIterator childrenIterator = childrenMap.keySet()
            .iterator(); childrenIterator.hasNext();) output.append(kindToName.get(childrenIterator.next()))
                .append((childrenIterator.hasNext()) ? ", " : "}");
        return output.toString();
    }

    TLongObjectMap<E> getChildrenMap() {
        return new TUnmodifiableLongObjectMap<>(childrenMap);
    }

    Resource<E> setSortOfChildren(final boolean sort) {
        childrenMap.forEachValue(child -> {
            child.setSort(sort);
            return true;
        });
        return this;
    }

    static void register(@Nonnull final String kindName) {
        if (nameToKind.containsKey(kindName)) return;
        final long kind = nextKind();
        nameToKind.put(kindName, kind);
        kindToName.put(kind, kindName);
    }

    static long registerAndGet(@Nonnull final String kindName) {
        if (nameToKind.containsKey(kindName)) return nameToKind.get(kindName);
        final long kind = nextKind();
        nameToKind.put(kindName, kind);
        kindToName.put(kind, kindName);
        return kind;
    }

    /** Allocates the next kind bit, enforcing the 64-kind limit. */
    static long nextKind() {
        if (totalKindsRegistered >= MAX_KINDS)
            throw new IllegalStateException("Cannot register more than " + MAX_KINDS + " resource kinds");
        return 1L << totalKindsRegistered++;
    }

    /** Test-only: reset the global kind registry to an empty state. */
    static void clearKinds() {
        nameToKind.clear();
        kindToName.clear();
        totalKindsRegistered = 0;
    }

    /**
     * Returns the resources that have every requested kind, or an empty list if a requested
     * kind name is unknown.
     */
    public static List<Resource<?>> getResources(@Nonnull final Collection<? extends Resource<?>> resources,
        final String... kinds) {
        long kindsId = 0;
        for (final String kind : kinds) {
            long kindId;
            if ((kindId = getKindOfName(kind)) == 0) return Collections.emptyList();
            kindsId |= kindId;
        }
        return getResources(resources, kindsId);
    }

    /** Returns the resources that have every requested kind bit set, or an empty list for {@code 0}. */
    public static List<Resource<?>> getResources(@Nonnull final Collection<? extends Resource<?>> resources,
        final long kinds) {
        return (kinds != 0) ? resources.stream()
            .filter(resource -> (kinds & resource.getChildren()) == kinds)
            .collect(Collectors.toList()) : Collections.emptyList();
    }

    /** Returns the resources that have every requested kind bit set (dropping {@code 0} entries from the mask). */
    public static List<Resource<?>> getResources(@Nonnull final Collection<? extends Resource<?>> resources,
        final long... kinds) {
        long trueKinds = 0;
        for (final long kind : kinds) if (kind != 0) trueKinds |= kind;
        else return Collections.emptyList();
        return getResources(resources, trueKinds);
    }

    public static List<String> getKinds() {
        return Collections.unmodifiableList(new ArrayList<>(nameToKind.keySet()));
    }

    public static long getKindOfName(@Nonnull final String name) {
        return nameToKind.get(name);
    }

    public static String getNameOfKind(final long kind) {
        return kindToName.get(kind);
    }

    public static boolean kindExists(@Nonnull final String name) {
        return nameToKind.containsKey(name);
    }

    public static boolean kindExists(@Nonnull final String... names) {
        for (final String name : names) if (!nameToKind.containsKey(name)) return false;
        return true;
    }
}
