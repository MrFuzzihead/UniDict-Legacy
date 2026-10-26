package com.mrfuzzihead.unidict.pure.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure, case-insensitive matching for resource <b>kind names</b> (docs/CODE_REVIEW.md BUG-3).
 *
 * <p>
 * Kind names are derived from Ore Dictionary prefixes and are <b>not</b> uniformly lowercase — the
 * default set contains {@code dustTiny} and {@code dustSmall} alongside {@code ore}/{@code ingot}. The
 * configuration surface documents {@code INGOT}-style spellings and the legacy
 * {@code ownerOfEveryIngot} alias, so matching kind names must ignore case while preserving the
 * registered spelling (lower-casing the stored values would break {@code dustTiny}).
 *
 * <p>
 * Minecraft-free so the rule is T1-testable; the Minecraft-bound call sites
 * ({@code UniResourceHandler}, {@code ConfigData#ownerOfKindFor}) delegate here.
 */
public final class KindNames {

    private KindNames() {}

    /**
     * @param names     configured kind names (any casing)
     * @param candidate the registered OD-derived kind name (e.g. {@code dustTiny})
     * @return {@code true} when {@code candidate} equals one of {@code names} ignoring case
     */
    public static boolean containsIgnoreCase(final Collection<String> names, final String candidate) {
        return findIgnoreCase(names, candidate) != null;
    }

    /**
     * Finds the first configured name matching {@code candidate} ignoring case.
     *
     * @return the matching entry from {@code names} (its original spelling), or {@code null}
     */
    public static String findIgnoreCase(final Collection<String> names, final String candidate) {
        if (names == null || candidate == null) return null;
        for (final String name : names) {
            if (name != null && name.equalsIgnoreCase(candidate)) return name;
        }
        return null;
    }

    /**
     * Canonical form used for map look-ups: {@code null}-safe, locale-independent lowercase.
     *
     * @param name a kind name, or {@code null}
     * @return the lowercased name, or {@code null}
     */
    public static String lookUpKey(final String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    /**
     * Every kind name the configuration references: {@link ConfigData#childrenOfMetals}, the NEI-hide
     * exemptions and the per-kind owner overrides. Used by the {@code kindDebugMode} log and the
     * {@code kindDiagnostics} verify line (docs/CODE_REVIEW.md BUG-18).
     *
     * @param config the parsed configuration (may be {@code null})
     * @return the referenced kind names (duplicates possible; {@link #unknownKindNames} de-duplicates)
     */
    public static List<String> configuredKindNames(final ConfigData config) {
        final List<String> configured = new ArrayList<>();
        if (config == null) return configured;
        configured.addAll(config.childrenOfMetals);
        configured.addAll(config.hideInNEIBlackSet);
        configured.addAll(config.ownerOfKind.keySet());
        return configured;
    }

    /**
     * @param names kind names in any order
     * @return the non-null, non-blank names, sorted (stable for logs/reports)
     */
    public static List<String> sorted(final Collection<String> names) {
        final List<String> sorted = new ArrayList<>();
        if (names == null) return sorted;
        for (final String name : names) if (name != null && !name.trim()
            .isEmpty()) sorted.add(name);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Configured kind names that do not match any registered kind (case-insensitive), sorted for a
     * stable log line. This is the diagnostic behind {@code kindDebugMode} (docs/CODE_REVIEW.md BUG-18):
     * a typo in {@code childrenOfMetals} / {@code hideInNEIBlackSet} / {@code ownerOfKind} used to be
     * silently inert.
     *
     * @param configured the configured kind names (any casing; {@code null}/blank entries are skipped)
     * @param registered the kind names the taxonomy actually registered
     * @return the sorted, de-duplicated configured names with no registered match (never {@code null})
     */
    public static List<String> unknownKindNames(final Collection<String> configured,
        final Collection<String> registered) {
        final List<String> unknown = new ArrayList<>();
        if (configured == null || configured.isEmpty()) return unknown;
        final Set<String> seen = new LinkedHashSet<>(); // lowercased keys: "ghost"/"Ghost" are one kind
        for (final String kind : configured) {
            if (kind == null || kind.trim()
                .isEmpty()) continue;
            if (findIgnoreCase(registered, kind) != null) continue;
            if (seen.add(lookUpKey(kind))) unknown.add(kind);
        }
        Collections.sort(unknown);
        return unknown;
    }
}
