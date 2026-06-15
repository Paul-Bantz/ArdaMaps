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

package com.duom.ardamaps.core.data.map.markers;

import com.duom.ardamaps.ArdaMapsClient;
import org.jetbrains.annotations.NotNull;

/**
 * Manager for map markers, responsible for loading marker definitions from resources and binding them to location data.
 * This class provides methods to reload marker definitions and rebind them to locations, ensuring that the latest
 * marker configurations are applied to the map.
 */
public class MarkersManager {

    /** Definition for map markers, loaded from resources and bound to location data. */
    private static @NotNull MarkersDefinition MARKERS_DEFINITION = MarkersDefinition.createDefault();

    /**
     * Reloads the map markers definitions file and rebind them to the location data.
     * This method should be called on client initialization or whenever a resource refresh even fires.
     */
    public static void reload() {

        MARKERS_DEFINITION = MarkersDefinition.loadMarkersDefinition();
        rebind();
    }

    /**
     * Rebinds each location to its corresponding marker type based on the loaded marker definition.
     * This method should be called after loading the markers definition or when location data changes.
     */
    public static void rebind() {

        MARKERS_DEFINITION.bindMarkers(ArdaMapsClient.CONFIG.getLocations());
    }

    /**
     * @return Gets the current marker definition.
     */
    public static @NotNull MarkersDefinition get() {

        return MARKERS_DEFINITION;
    }
}
