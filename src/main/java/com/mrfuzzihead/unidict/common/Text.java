package com.mrfuzzihead.unidict.common;

/**
 * Small, allocation-free text helpers shared by the MC-facing config matchers.
 *
 * <p>
 * Config values (OD-name and item-name substrings) are documented as user-friendly, so matching must
 * ignore case. {@link String#regionMatches(boolean, int, String, int, int)} gives us that without
 * lower-casing the haystack or allocating a token copy on every resolver call.
 */
public final class Text {

    private Text() {}

    /**
     * Case-insensitive equivalent of {@link String#contains(CharSequence)}.
     *
     * @param haystack the value to search (may be {@code null})
     * @param needle   the non-empty token to find (may be {@code null})
     * @return {@code true} when {@code needle} occurs in {@code haystack} ignoring case
     */
    public static boolean containsIgnoreCase(final String haystack, final String needle) {
        if (haystack == null || needle == null || needle.isEmpty() || needle.length() > haystack.length()) return false;
        final int lastStart = haystack.length() - needle.length();
        for (int i = 0; i <= lastStart; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) return true;
        }
        return false;
    }
}
