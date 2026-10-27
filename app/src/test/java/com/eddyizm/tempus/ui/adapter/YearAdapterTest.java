package com.eddyizm.tempus.ui.adapter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class YearAdapterTest {
    @Test
    public void oklch_convertsReferenceColoursToSrgb() {
        assertEquals(0xFFFFFFFF, YearAdapter.oklch(1, 0, 0));
        assertEquals(0xFF000000, YearAdapter.oklch(0, 0, 0));
        assertEquals(0xFFFF0000, YearAdapter.oklch(0.627955, 0.257683, 29.2339));
    }
}
