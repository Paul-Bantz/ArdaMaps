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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests tile-key equality symmetry across subclasses.
 */
class TileKeyTest {

    /**
     * Verifies that TileKey and PmTileKey instances with identical coordinates are not equal across different classes.
     * This enforces type-safe tile-key comparisons so heterogeneous collections do not incorrectly match.
     */
    @Test
    void tileKeyAndPmTileKey_areNotEqualAcrossClasses() {

        TileKey tileKey = new TileKey(1, 2, 3);
        PmTileKey pmTileKey = new PmTileKey(1, 2, 3);

        assertNotEquals(tileKey, pmTileKey);
        assertNotEquals(pmTileKey, tileKey);
    }

    /**
     * Verifies the PMTiles TileID prefix bound for all zooms up to six. The bound must sit after
     * every tile at zooms {@code <= B} and before or at the first tile at zoom {@code B + 1}.
     */
    @Test
    void pmTileKey_tileIdUpperBound_exhaustiveThroughZoomSix() {

        long[] expected = {1, 5, 21, 85, 341, 1365, 5461};

        for (int boundZoom = 0; boundZoom <= 6; boundZoom++) {
            long bound = PmTileKey.tileIdUpperBound(boundZoom);
            assertEquals(expected[boundZoom], bound);

            long maxIncluded = Long.MIN_VALUE;
            for (int z = 0; z <= boundZoom; z++) {
                int edge = 1 << z;
                for (int x = 0; x < edge; x++) {
                    for (int y = 0; y < edge; y++) {
                        maxIncluded = Math.max(maxIncluded, new PmTileKey(z, x, y).toTileId());
                    }
                }
            }

            long minExcluded = Long.MAX_VALUE;
            int nextZoom = boundZoom + 1;
            int edge = 1 << nextZoom;
            for (int x = 0; x < edge; x++) {
                for (int y = 0; y < edge; y++) {
                    minExcluded = Math.min(minExcluded, new PmTileKey(nextZoom, x, y).toTileId());
                }
            }

            assertTrue(maxIncluded < bound, "Bound must exclude all TileIDs up to zoom " + boundZoom);
            assertTrue(bound <= minExcluded, "Bound must not skip into zoom " + nextZoom);
        }
    }
}
