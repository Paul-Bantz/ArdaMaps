/*
 * This file is part of ArdaMaps, licensed under the MIT License (MIT).
 *
 * Copyright (c) Paul-Bantz <https://github.com/Paul-Bantz>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.duom.ardamaps.core.data.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DimensionRemap} region matching.
 */
class DimensionRemapTest {

    /**
     * Reversed corner coordinates are normalized for inclusive containment checks.
     */
    @Test
    void contains_reversedCorners_includesBoundaries() {

        DimensionRemap remap = new DimensionRemap("multiworld:freebuild", 10, 20, 0, 5);

        assertEquals(0, remap.x1());
        assertEquals(5, remap.z1());
        assertEquals(10, remap.x2());
        assertEquals(20, remap.z2());
        assertTrue(remap.contains(0, 5));
        assertTrue(remap.contains(10, 20));
        assertTrue(remap.contains(4.5D, 12.5D));
        assertFalse(remap.contains(-0.1D, 5));
        assertFalse(remap.contains(10, 20.1D));
    }

    /**
     * Regionless remaps carry an ID but are not regional teleport targets.
     */
    @Test
    void contains_regionlessRemap_neverMatches() {

        DimensionRemap remap = new DimensionRemap("multiworld:freebuild");

        assertFalse(remap.hasRegion());
        assertFalse(remap.contains(0, 0));
    }

    /**
     * Overlap checks ignore regionless remaps and detect intersecting regional rectangles.
     */
    @Test
    void overlaps_regionalRemaps_detectsIntersection() {

        DimensionRemap first = new DimensionRemap("multiworld:first", 0, 0, 10, 10);
        DimensionRemap second = new DimensionRemap("multiworld:second", 5, 5, 15, 15);
        DimensionRemap outside = new DimensionRemap("multiworld:outside", 11, 0, 20, 10);

        assertTrue(first.overlaps(second));
        assertFalse(first.overlaps(outside));
        assertFalse(first.overlaps(new DimensionRemap("multiworld:regionless")));
    }
}
