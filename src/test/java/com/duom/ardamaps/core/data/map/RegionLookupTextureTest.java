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

package com.duom.ardamaps.core.data.map;

import com.duom.ardamaps.core.data.config.Dimension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the Region Lookup Texture class (LUT)
 */
class RegionLookupTextureTest {

    /**
     * A small 4×4-pixel texture that covers a 400×400 block world (100 blocks/pixel)
     * anchored at (0, 0).  Region index layout:
     * 1  1  2  2
     * 1  1  2  2
     * 3  3  0  0   (0 = no region)
     * 3  3  0  0
     */
    private static final int TEX_W = 4;

    private static final int TEX_H = 4;

    /** The dimension this texture covers. */
    private Dimension dimension;

    /** The regions listed on that LUT */
    private Region[] regions;

    /** The raw LUT Bytes */
    private byte[] pixels;

    /**
     * LUT Texture setup
     */
    @BeforeEach
    void setUp() {
        // xMin=0, xMax=401, zMin=0, zMax=401 -> width=400, height=400 (xMax - xMin - 1)
        dimension = new Dimension("Test", "test:dim", 1f, 0, 401, 0, 401, false);
        regions = new Region[]{new Region("r1", "Shire"), new Region("r2", "Rohan"), new Region("r3", "Gondor")};
        pixels = new byte[]{
                1, 1, 2, 2,
                1, 1, 2, 2,
                3, 3, 0, 0,
                3, 3, 0, 0
        };
    }

    /**
     * RegionLookupTexture.DEFAULT is used as a sentinel before any data arrives.
     * It is queried every render frame - it must never throw.
     */
    @Test
    void getRegionAt_defaultEmptyTexture_returnsNull() {

        assertNull(RegionLookupTexture.DEFAULT.getRegionAt(dimension, 100, 100));
    }

    /**
     * The method is called per frame; a null dimension (e.g. during dimension switch) must not cause a NullPointerException.
     */
    @Test
    void getRegionAt_nullDimension_returnsNull() {

        RegionLookupTexture tex = new RegionLookupTexture(pixels, regions, TEX_W, TEX_H, "test:dim", new Date());

        assertNull(tex.getRegionAt(null, 50, 50));
    }

    /**
     * Prevents region names from a different world bleeding onto the current map when the player switches dimensions
     * without the cache clearing first.
     */
    @Test
    void getRegionAt_dimensionIdMismatch_returnsNull() {

        Dimension otherDim = new Dimension("Other", "other:dim", 1f, 0, 401, 0, 401, false);
        RegionLookupTexture tex = new RegionLookupTexture(pixels, regions, TEX_W, TEX_H, "test:dim", new Date());

        assertNull(tex.getRegionAt(otherDim, 50, 50));
    }

    /**
     * A large-negative worldX produces a clearly negative texX index. Java's (int) cast truncates toward zero,
     * so only values further than -100 blocks west of xMin=0 produce texX < 0 for a 400-block-wide world at 4 pixels wide.
     * Without the bounds guard this would be ArrayIndexOutOfBoundsException.
     */
    @Test
    void getRegionAt_worldXBelowBound_returnsNull() {

        RegionLookupTexture tex = new RegionLookupTexture(pixels, regions, TEX_W, TEX_H, "test:dim", new Date());

        assertNull(tex.getRegionAt(dimension, -200, 50));
    }

    /**
     * Same for the X upper bound - world positions past xMax must be safe.
     */
    @Test
    void getRegionAt_worldXAboveBound_returnsNull() {

        RegionLookupTexture tex = new RegionLookupTexture(pixels, regions, TEX_W, TEX_H, "test:dim", new Date());

        assertNull(tex.getRegionAt(dimension, 500, 50));
    }

    /**
     * Same for the Z upper bound - world positions past xMax must be safe.
     */
    @Test
    void getRegionAt_worldZAboveBound_returnsNull() {
        RegionLookupTexture tex = new RegionLookupTexture(pixels, regions, TEX_W, TEX_H, "test:dim", new Date());
        assertNull(tex.getRegionAt(dimension, 50, 500));
    }

    /**
     * Index 0 means "no region mapped here". Without this check the code would access regions[-1] which is an ArrayIndexOutOfBoundsException.
     * The bottom-right quadrant of our test texture has pixel value 0.Index 0 means "no region mapped here". Without this check the code would
     * access regions[-1] which is an ArrayIndexOutOfBoundsException.
     * The bottom-right quadrant of our test texture has pixel value 0.
     */
    @Test
    void getRegionAt_pixelIndexZero_returnsNull() {

        RegionLookupTexture tex = new RegionLookupTexture(pixels, regions, TEX_W, TEX_H, "test:dim", new Date());

        // World coords that map to pixel (2,2) or (3,3) - the no-region quadrant.
        // pixel col = floor(worldX / 100), so worldX=250 -> col 2, worldZ=250 -> row 2.
        assertNull(tex.getRegionAt(dimension, 250, 250));
    }

    /**
     * Core use case: validates the world-coord-to-pixel formula.  An off-by-one would return the neighbouring region's name.
     */
    @Test
    void getRegionAt_validPosition_returnsCorrectRegionName() {

        RegionLookupTexture tex = new RegionLookupTexture(pixels, regions, TEX_W, TEX_H, "test:dim", new Date());

        // pixel (0,0) -> region index 1 -> regions[0] = "Shire"
        assertEquals("Shire", tex.getRegionAt(dimension, 10, 10));

        // pixel (2,0) -> region index 2 -> regions[1] = "Rohan"
        assertEquals("Rohan", tex.getRegionAt(dimension, 210, 10));

        // pixel (0,2) -> region index 3 -> regions[2] = "Gondor"
        assertEquals("Gondor", tex.getRegionAt(dimension, 10, 210));
    }

    /**
     * Pixel value 255 is stored as signed byte -1 in Java. Without `& 0xFF`, the lookup would use index -1 leading to ArrayIndexOutOfBoundsException.
     * Build a texture with 255 regions and a single pixel set to (byte) -1.
     */
    @Test
    void getRegionAt_pixelValueOf255_treatedAsUnsigned() {

        int numRegions = 255;
        Region[] manyRegions = new Region[numRegions];
        for (int i = 0; i < numRegions; i++) manyRegions[i] = new Region("r" + i, "Region" + i);

        byte[] singlePixel = new byte[]{(byte) 255}; // raw -1 in signed Java byte
        RegionLookupTexture tex = new RegionLookupTexture(singlePixel, manyRegions, 1, 1, "test:dim", new Date());

        // Should return regions[254].name() = "Region254", not throw.
        assertEquals("Region254", tex.getRegionAt(dimension, 0, 0));
    }
}
