package com.mrfuzzihead.unidict.pure.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Pure (Minecraft-free) value describing one unified resource — a single "kind" container such as
 * {@code ingotIron} — for the BB-1 transparency report (docs/PLAN.md §BB-1). It carries the
 * canonical (main) entry, every variant, and the owning mods of those variants so the report can be
 * computed and format-tested with zero {@code net.minecraft*} imports.
 *
 * <p>
 * Immutable. Build via {@link #from(String, String, List)}: variants are sorted for run-to-run
 * stability and owners are derived from each {@code owner:path} descriptor then deduped + sorted.
 */
public final class ReportEntry {

    /** The container/resource name, e.g. {@code ingotIron}. */
    public final String resourceName;

    /** The fully-qualified canonical (main) entry, e.g. {@code minecraft:iron_ingot}, or {@code "none"}. */
    public final String main;

    /** Every variant entry as its qualified name (main included), sorted. */
    public final List<String> variants;

    /** The distinct owning mods across all variants, sorted. */
    public final List<String> owners;

    private ReportEntry(final String resourceName, final String main, final List<String> variants,
        final List<String> owners) {
        this.resourceName = resourceName;
        this.main = main;
        this.variants = variants;
        this.owners = owners;
    }

    /**
     * Builds an entry from the resource/container name, its canonical descriptor, and every variant
     * descriptor (the main entry normally appears in this list too).
     */
    public static ReportEntry from(@Nonnull final String resourceName, @Nonnull final String main,
        @Nonnull final List<String> variantDescriptors) {
        final List<String> variants = new ArrayList<>(variantDescriptors);
        Collections.sort(variants);
        final Set<String> ownerSet = new LinkedHashSet<>();
        ownerSet.add(ownerOf(main));
        for (final String descriptor : variants) ownerSet.add(ownerOf(descriptor));
        final List<String> owners = new ArrayList<>(ownerSet);
        Collections.sort(owners);
        return new ReportEntry(
            resourceName,
            main,
            Collections.unmodifiableList(variants),
            Collections.unmodifiableList(owners));
    }

    /** The mod namespace of a {@code owner:path} descriptor, or the whole descriptor if no ':' is present. */
    private static String ownerOf(final String descriptor) {
        final int colon = descriptor.indexOf(':');
        return colon < 0 ? descriptor : descriptor.substring(0, colon);
    }
}
