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

package com.duom.ardamaps.core.data.map.tiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests tile-key equality symmetry across subclasses.
 */
class TileKeyTest {

    /**
     * Verify that the base tile key and PMTiles tile key remain unequal across classes.
     */
    @Test
    void tileKeyAndPmTileKey_areNotEqualAcrossClasses() {

        TileKey tileKey = new TileKey(1, 2, 3);
        PmTileKey pmTileKey = new PmTileKey(1, 2, 3);

        assertNotEquals(tileKey, pmTileKey);
        assertNotEquals(pmTileKey, tileKey);
    }

    /**
     * Verify that PMTiles tile-id banding separates successive zoom levels.
     */
    @Test
    void pmTileKeyUpperBoundSeparatesZoomBandsThroughSix() {

        long[] expected = {1, 5, 21, 85, 341, 1365, 5461};

        for (int boundZoom = 0; boundZoom <= 6; boundZoom++) {
            long bound = PmTileKey.tileIdUpperBound(boundZoom);
            long maxBelowOrAt = Long.MIN_VALUE;
            long minNext = Long.MAX_VALUE;

            for (int z = 0; z <= boundZoom + 1; z++) {
                int edge = 1 << z;
                for (int x = 0; x < edge; x++) {
                    for (int y = 0; y < edge; y++) {
                        long tileId = new PmTileKey(z, x, y).toTileId();
                        if (z <= boundZoom) maxBelowOrAt = Math.max(maxBelowOrAt, tileId);
                        if (z == boundZoom + 1) minNext = Math.min(minNext, tileId);
                    }
                }
            }

            assertEquals(expected[boundZoom], bound);
            assertTrue(maxBelowOrAt < bound, "Bound must exceed every tile ID at or below zoom " + boundZoom);
            assertTrue(bound <= minNext, "Bound must not exceed the first tile ID at zoom " + (boundZoom + 1));
        }
    }
}
