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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ranged path, range selection, and equality semantics on {@link MapLayerDefinition}.
 */
class MapLayerDefinitionTest {

    /** Lower band used by the ranged layer fixture. */
    private static final MapLayerRange LOWER = new MapLayerRange(0, "lower.pmtiles", -64, 0);

    /** Upper band used by the ranged layer fixture. */
    private static final MapLayerRange UPPER = new MapLayerRange(1, "upper.pmtiles", 1, 128);

    /**
     * Verifies that a world Y inside a configured band returns that exact band.
     * This is the baseline ranged-selection behavior that higher-level loaders depend on.
     */
    @Test
    void rangeForY_insideBand_returnsContainingRange() {

        assertEquals(UPPER, rangedLayer().rangeForY(42));
    }

    /**
     * Creates a ranged layer fixture with two vertical bands and a layer-scoped fallback icon.
     *
     * @return A ranged PMTiles layer definition.
     */
    private static MapLayerDefinition rangedLayer() {

        return new MapLayerDefinition("Ranged", MapLayerSource.PMTILES, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, null, "fallback.png", List.of(LOWER, UPPER));
    }

    /**
     * Verifies that out-of-band Y values snap to the nearest configured range.
     * This protects callers that use player elevation to resolve a ranged layer even when the player is outside the declared bands.
     */
    @Test
    void rangeForY_outsideBands_returnsNearestRange() {

        assertEquals(LOWER, rangedLayer().rangeForY(-100));
        assertEquals(UPPER, rangedLayer().rangeForY(200));
    }

    /**
     * Verifies that flat layers do not pretend to support range selection.
     * Returning null here is what keeps flat-layer consumers off the ranged code path.
     */
    @Test
    void rangeForY_withoutRanges_returnsNull() {

        assertNull(flatLayer().rangeForY(42));
    }

    /**
     * Creates a flat layer fixture with a single path and icon.
     *
     * @return A flat WEBP layer definition.
     */
    private static MapLayerDefinition flatLayer() {

        return new MapLayerDefinition("Flat", MapLayerSource.WEBP, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, "flat.webp", "flat.png", null);
    }

    /**
     * Verifies that ranged layers resolve their path from the selected or inferred band while leaving the icon layer-scoped.
     * This preserves the current contract that only data paths vary by range, not icons.
     */
    @Test
    void effectivePathUsesSelectedRangeAndIconUsesLayerWhenRanged() {

        MapLayerDefinition layer = rangedLayer();

        assertEquals("upper.pmtiles", layer.effectivePath(12d));
        assertEquals("fallback.png", layer.effectiveIcon(12d));
        assertEquals("lower.pmtiles", layer.effectivePath(null));
        assertEquals("fallback.png", layer.effectiveIcon(null));
    }

    /**
     * Verifies that flat layers always return their own path and icon regardless of the supplied Y coordinate.
     * This guards against ranged-resolution logic leaking into flat-layer behavior.
     */
    @Test
    void effectivePathAndIcon_useLayerValuesWhenFlat() {

        MapLayerDefinition layer = flatLayer();

        assertEquals("flat.webp", layer.effectivePath(12d));
        assertEquals("flat.png", layer.effectiveIcon(12d));
    }

    /**
     * Verifies that equality and hash code account for the range list.
     * This matters because two otherwise identical layers must compare differently when they represent different vertical data slices.
     */
    @Test
    void equalsAndHashCode_includeRanges() {

        MapLayerDefinition left = rangedLayer();
        MapLayerDefinition right = rangedLayer();
        MapLayerDefinition different = new MapLayerDefinition(
                left.layer(), left.type(), left.remote(), left.identityZoom(), left.preferredZoom(),
                left.lodFactor(), left.minLod(), left.maxLod(), left.minZoom(), left.maxZoom(),
                left.tileSize(), left.scale(), left.path(), left.icon(), List.of(LOWER));

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
    }
}
