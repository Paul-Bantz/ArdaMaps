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
 * Tests for {@link DimensionRemap} normalization and geometry checks.
 */
class DimensionRemapTest {

    /**
     * Reversed corners are normalized and bounds are inclusive.
     */
    @Test
    void constructor_reversedCorners_normalizesInclusiveRegion() {

        DimensionRemap remap = new DimensionRemap("multiworld:freebuild", 30, 40, 10, 20);

        assertEquals(10, remap.x1());
        assertEquals(20, remap.z1());
        assertEquals(30, remap.x2());
        assertEquals(40, remap.z2());
        assertTrue(remap.contains(10, 20));
        assertTrue(remap.contains(30, 40));
        assertFalse(remap.contains(31, 40));
    }

    /**
     * Regionless remaps have no region and never contain teleport coordinates.
     */
    @Test
    void regionlessRemap_hasNoRegion() {

        DimensionRemap remap = new DimensionRemap("multiworld:freebuild");

        assertFalse(remap.hasRegion());
        assertFalse(remap.contains(0, 0));
    }

    /**
     * Overlap detection ignores regionless remaps and detects shared cells.
     */
    @Test
    void overlaps_detectsRegionalIntersectionOnly() {

        DimensionRemap first = new DimensionRemap("first", 0, 0, 10, 10);
        DimensionRemap second = new DimensionRemap("second", 10, 10, 20, 20);
        DimensionRemap third = new DimensionRemap("third", 11, 11, 20, 20);

        assertTrue(first.overlaps(second));
        assertFalse(first.overlaps(third));
        assertFalse(first.overlaps(new DimensionRemap("regionless")));
    }
}
