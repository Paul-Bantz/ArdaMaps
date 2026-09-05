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

package com.duom.ardamaps.gui.screens.map;

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.config.MapLayerSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link MapLayerLoader} range resolution and effective ranged-path selection.
 */
class MapLayerLoaderTest {

    /**
     * Verifies that an explicit UI-selected range overrides player-position based range resolution.
     * This protects the loader from silently replacing a user choice with an inferred band.
     */
    @Test
    void resolveExplorationRange_selectedRange_wins() {

        var low = range(0, "low.pmtiles", -64, 63);
        var high = range(1, "high.pmtiles", 64, 320);
        Dimension dimension = dimensionWithRanges(low, high);

        assertSame(low, MapLayerLoader.resolveExplorationRange(dimension, low, 100.0));
    }

    /**
     * Creates a simple range fixture with a stable index and path.
     *
     * @param index The stable exploration index.
     * @param path  The path associated with the range.
     * @param minY  The inclusive minimum Y.
     * @param maxY  The inclusive maximum Y.
     * @return A range fixture for loader tests.
     */
    private static MapLayerRange range(int index, String path, int minY, int maxY) {

        return new MapLayerRange(index, path, minY, maxY);
    }

    /**
     * Creates a dimension fixture whose first layer exposes the supplied canonical exploration ranges.
     *
     * @param ranges The ranges to attach to the first layer.
     * @return A dimension suitable for loader range-resolution tests.
     */
    private static Dimension dimensionWithRanges(MapLayerRange... ranges) {

        Dimension dimension = new Dimension("Overworld", "minecraft:overworld", 1, -100, 100, -100, 100, false);
        dimension.getMapLayers().add(layer(List.of(ranges)));
        return dimension;
    }

    /**
     * Creates a ranged layer fixture with the supplied range list.
     *
     * @param ranges The ranges to expose from the layer.
     * @return A ranged grid layer definition used by these tests.
     */
    private static MapLayerDefinition layer(List<MapLayerRange> ranges) {

        return new MapLayerDefinition(
                "Layer",
                MapLayerSource.GRID,
                false,
                8,
                null,
                1.0,
                1,
                3,
                1,
                14,
                256,
                1.0,
                "default.pmtiles",
                "",
                ranges);
    }

    /**
     * Verifies that player Y selects the matching canonical exploration range when no explicit range is selected.
     * This protects the default loading path used when the screen opens on a ranged dimension.
     */
    @Test
    void resolveExplorationRange_playerY_selectsMatchingRange() {

        var low = range(0, "low.pmtiles", -64, 63);
        var high = range(1, "high.pmtiles", 64, 320);
        Dimension dimension = dimensionWithRanges(low, high);

        assertSame(high, MapLayerLoader.resolveExplorationRange(dimension, null, 100.0));
    }

    /**
     * Verifies that missing player Y falls back to the first canonical exploration range.
     * This documents the deterministic fallback used when the player position is unavailable.
     */
    @Test
    void resolveExplorationRange_noPlayerY_usesFirstRange() {

        var low = range(0, "low.pmtiles", -64, 63);
        var high = range(1, "high.pmtiles", 64, 320);
        Dimension dimension = dimensionWithRanges(low, high);

        assertSame(low, MapLayerLoader.resolveExplorationRange(dimension, null, null));
    }

    /**
     * Verifies that the resolved layer path uses the selected range path when a range is explicitly selected.
     * This protects ranged layer loading from accidentally falling back to a player-derived path.
     */
    @Test
    void resolveLayer_selectedRange_usesSelectedRangePath() {

        var low = range(0, "low.pmtiles", -64, 63);
        var high = range(1, "high.pmtiles", 64, 320);
        MapLayerDefinition layer = layer(List.of(low, high));

        assertEquals("high.pmtiles", MapLayerLoader.resolveLayer(layer, high, 0.0).path());
    }

    /**
     * Verifies that the resolved layer path uses {@code effectivePath(playerY)} when no range is explicitly selected.
     * This guards the inferred ranged-path branch used during initial layer construction.
     */
    @Test
    void resolveLayer_noSelectedRange_usesEffectivePath() {

        var low = range(0, "low.pmtiles", -64, 63);
        var high = range(1, "high.pmtiles", 64, 320);
        MapLayerDefinition layer = layer(List.of(low, high));

        assertEquals("high.pmtiles", MapLayerLoader.resolveLayer(layer, null, 100.0).path());
    }
}
