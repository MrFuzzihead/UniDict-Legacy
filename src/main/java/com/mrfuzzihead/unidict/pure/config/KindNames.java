package com.mrfuzzihead.unidict.pure.config;

import java.util.Collection;
import java.util.Locale;

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
}
