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
 * Tests for canonical range selection behaviour on {@link Dimension}.
 */
class DimensionRangeTest {

    /** First canonical exploration band used by the ranged-layer fixtures. */
    private static final MapLayerRange CANONICAL_LOW = new MapLayerRange(0, "low.pmtiles", -64, 0);

    /** Second canonical exploration band used by the ranged-layer fixtures. */
    private static final MapLayerRange CANONICAL_HIGH = new MapLayerRange(1, "high.pmtiles", 1, 128);

    /** Alternative range set used to prove only the first ranged layer contributes exploration bands. */
    private static final MapLayerRange OTHER = new MapLayerRange(2, "other.pmtiles", 200, 300);

    /**
     * Verifies that the first ranged map layer defines the canonical exploration bands for the whole dimension.
     * This matters because later ranged layers may exist for rendering, but exploration state must stay keyed to one stable range set.
     */
    @Test
    void getExplorationRanges_firstRangedLayerWins() {

        Dimension dimension = dimension();
        dimension.getMapLayers().add(flatLayer());
        dimension.getMapLayers().add(rangedLayer(List.of(CANONICAL_LOW, CANONICAL_HIGH)));
        dimension.getMapLayers().add(rangedLayer(List.of(OTHER)));

        assertTrue(dimension.hasRanges());
        assertEquals(List.of(CANONICAL_LOW, CANONICAL_HIGH), dimension.getExplorationRanges());
    }

    /**
     * Creates a minimal test dimension to which flat or ranged layers can be attached.
     *
     * @return A mutable dimension fixture with no map layers.
     */
    private static Dimension dimension() {

        return new Dimension("Test", "test:dimension", 1f, 0, 1000, 0, 1000, false);
    }

    /**
     * Creates a flat layer fixture that should not contribute exploration ranges.
     *
     * @return A flat WEBP layer definition.
     */
    private static MapLayerDefinition flatLayer() {

        return new MapLayerDefinition("Flat", MapLayerSource.WEBP, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, "flat.webp", "flat.png", null);
    }

    /**
     * Creates a ranged layer fixture backed by the supplied canonical range list.
     *
     * @param ranges The range set to expose from the layer.
     * @return A ranged PMTiles layer definition.
     */
    private static MapLayerDefinition rangedLayer(List<MapLayerRange> ranges) {

        return new MapLayerDefinition("Ranged", MapLayerSource.PMTILES, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, "fallback.pmtiles", "fallback.png", ranges);
    }

    /**
     * Verifies that range lookup uses the canonical exploration bands and snaps to the nearest band when outside all bounds.
     * This protects callers that infer range selection from player Y even when the player is above or below configured bands.
     */
    @Test
    void rangeForY_usesCanonicalRangesAndNearestSnap() {

        Dimension dimension = dimension();
        dimension.getMapLayers().add(rangedLayer(List.of(CANONICAL_LOW, CANONICAL_HIGH)));

        assertEquals(CANONICAL_HIGH, dimension.rangeForY(64));
        assertEquals(CANONICAL_LOW, dimension.rangeForY(-100));
    }

    /**
     * Verifies that dimensions without any ranged layers report no canonical ranges and return null for range lookup.
     * This keeps flat dimensions on the non-ranged code path instead of fabricating synthetic range state.
     */
    @Test
    void rangeForY_withoutRanges_returnsNull() {

        Dimension dimension = dimension();
        dimension.getMapLayers().add(flatLayer());

        assertFalse(dimension.hasRanges());
        assertTrue(dimension.getExplorationRanges().isEmpty());
        assertNull(dimension.rangeForY(64));
    }
}
