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

package com.duom.ardamaps.core.data.map.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for even-odd polygon containment.
 */
class PointInPolygonTest {

    /**
     * Verifies containment for a concave polygon notch.
     */
    @Test
    void contains_concavePolygon_respectsNotch() {

        int[] concave = {10, 10, 50, 10, 50, 20, 25, 20, 25, 50, 10, 50};

        assertTrue(PointInPolygon.contains(new int[][]{concave}, 15, 15));
        assertFalse(PointInPolygon.contains(new int[][]{concave}, 40, 40));
    }

    /**
     * Verifies even-odd handling for a polygon with a hole.
     */
    @Test
    void contains_multiRingPolygon_treatsInnerRingAsHole() {

        int[] outer = {0, 0, 100, 0, 100, 100, 0, 100};
        int[] hole = {25, 25, 75, 25, 75, 75, 25, 75};

        assertTrue(PointInPolygon.contains(new int[][]{outer, hole}, 10, 10));
        assertFalse(PointInPolygon.contains(new int[][]{outer, hole}, 50, 50));
    }
}
