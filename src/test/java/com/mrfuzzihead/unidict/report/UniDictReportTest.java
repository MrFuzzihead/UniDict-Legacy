package com.mrfuzzihead.unidict.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.unidict.pure.MetaKey;

class UniDictReportTest {

    @Test
    void damageSuffixDistinguishesMetadataFromWildcard() {
        assertEquals("@0", UniDictReport.damageSuffix(0));
        assertEquals("@1", UniDictReport.damageSuffix(1));
        assertEquals("@*", UniDictReport.damageSuffix(MetaKey.WILDCARD));
    }
}
