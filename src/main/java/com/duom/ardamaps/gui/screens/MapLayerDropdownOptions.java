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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Prepares map layer dropdown options from dimension config without mutating config state.
 */
final class MapLayerDropdownOptions {

    private MapLayerDropdownOptions() {
    }

    static List<MapLayerDefinition> forDimension(@Nullable Dimension selectedDimension) {

        /*
         This can happen if the client has not yet received a dimension configuration from the server or the server
         is misconfigured. Provide a bare empty list to avoid crashing the client.
         */
        List<MapLayerDefinition> mapLayers = selectedDimension != null
                ? new ArrayList<>(selectedDimension.getMapLayers())
                : new ArrayList<>();

        // Provide a default grid layer if no layers are defined for the dimension to ensure the map is minimally functional
        if (mapLayers.isEmpty()) mapLayers.add(MapLayerDefinition.DEFAULT_GRID_LAYER);

        return mapLayers;
    }
}
