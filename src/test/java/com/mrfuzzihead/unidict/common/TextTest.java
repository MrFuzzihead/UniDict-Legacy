package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void containsIgnoreCaseMatchesTheDocumentedNames() {
        assertTrue(Text.containsIgnoreCase("etfuturum:copper_block", "copper_block"));
        assertTrue(Text.containsIgnoreCase("etfuturum:copper_block", "COPPER_BLOCK"));
        assertTrue(Text.containsIgnoreCase("rawCopper", "raw"));
        assertTrue(Text.containsIgnoreCase("RAW_COPPER", "raw"));
    }

    @Test
    void containsIgnoreCaseRejectsAbsentOrInvalidTokens() {
        assertFalse(Text.containsIgnoreCase("etfuturum:copper_block", "block_copper"));
        assertFalse(Text.containsIgnoreCase("copper", "copper_block"));
        assertFalse(Text.containsIgnoreCase("copper", ""));
        assertFalse(Text.containsIgnoreCase(null, "copper"));
        assertFalse(Text.containsIgnoreCase("copper", null));
    }
}
