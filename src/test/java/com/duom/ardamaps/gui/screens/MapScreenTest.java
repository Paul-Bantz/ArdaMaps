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

package com.duom.ardamaps.gui.screens;

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for map screen layer-option preparation.
 */
class MapScreenTest {

    /**
     * Verifies that the grid fallback used by the layer dropdown does not mutate dimension config.
     */
    @Test
    void mapLayersForDropdown_emptyDimension_usesGridFallbackWithoutMutatingConfig() {

        Dimension dimension = new Dimension("Moria", "test:moria", 1, 0, 100, 0, 100, false);

        List<MapLayerDefinition> dropdownLayers = MapLayerDropdownOptions.forDimension(dimension);

        assertTrue(dimension.getMapLayers().isEmpty());
        assertEquals(List.of(MapLayerDefinition.DEFAULT_GRID_LAYER), dropdownLayers);
    }

    /**
     * Verifies that dropdown options are copied from config instead of aliasing the live list.
     */
    @Test
    void mapLayersForDropdown_configuredDimension_returnsIndependentLayerList() {

        Dimension dimension = new Dimension("Arda", "test:arda", 1, 0, 100, 0, 100, false);
        MapLayerDefinition layer = layer("Rendered");
        dimension.getMapLayers().add(layer);

        List<MapLayerDefinition> dropdownLayers = MapLayerDropdownOptions.forDimension(dimension);

        assertEquals(List.of(layer), dropdownLayers);
        assertSame(layer, dropdownLayers.getFirst());
        assertNotSame(dimension.getMapLayers(), dropdownLayers);
    }

    @SuppressWarnings("SameParameterValue")
    private static MapLayerDefinition layer(String name) {

        return new MapLayerDefinition(
                name,
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
                List.of());
    }
}
