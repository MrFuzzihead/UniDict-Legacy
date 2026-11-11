package com.mrfuzzihead.unidict.pure.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReportPagerTest {

    private static final List<String> LINES = Arrays.asList("a", "b", "c", "d", "e");

    @Test
    void pagesSplitAtTheBoundary() {
        assertEquals(3, ReportPager.pageCount(5, 2));
        assertEquals(Arrays.asList("a", "b"), ReportPager.page(LINES, 1, 2));
        assertEquals(Arrays.asList("e"), ReportPager.page(LINES, 3, 2));
    }

    @Test
    void pageIsClampedToTheAvailableRange() {
        assertEquals(Arrays.asList("e"), ReportPager.page(LINES, 99, 2));
        assertEquals(Arrays.asList("a", "b"), ReportPager.page(LINES, -4, 2));
    }

    @Test
    void emptyOrInvalidInputsReturnEmpty() {
        assertEquals(0, ReportPager.pageCount(0, 20));
        assertEquals(0, ReportPager.pageCount(5, 0));
        assertEquals(Collections.emptyList(), ReportPager.page(Collections.emptyList(), 1, 20));
        assertEquals(Collections.emptyList(), ReportPager.page(LINES, 1, 0));
    }
}
