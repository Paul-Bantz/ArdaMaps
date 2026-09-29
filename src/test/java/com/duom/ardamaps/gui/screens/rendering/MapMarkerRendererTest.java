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

package com.duom.ardamaps.gui.screens.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for marker rendering calculations.
 */
class MapMarkerRendererTest {

    /**
     * Marker scale remains full size at identity zoom and closer.
     */
    @Test
    void markerZoomScale_atIdentityOrCloser_returnsFullSize() {

        assertEquals(1f, MapMarkerRenderer.markerZoomScale(1.0, 0.25), 1e-6f);
        assertEquals(1f, MapMarkerRenderer.markerZoomScale(2.0, 0.25), 1e-6f);
    }

    /**
     * Marker scale remains full size when camera scales are not usable.
     */
    @Test
    void markerZoomScale_withNaN_returnsFullSize() {

        assertEquals(1f, MapMarkerRenderer.markerZoomScale(Double.NaN, 0.25), 1e-6f);
        assertEquals(1f, MapMarkerRenderer.markerZoomScale(0.5, Double.NaN), 1e-6f);
    }

    /**
     * Marker scale reaches its minimum at the camera zoom-out limit.
     */
    @Test
    void markerZoomScale_atMinimumScale_returnsMinimumMarkerSize() {

        assertEquals(0.25f, MapMarkerRenderer.markerZoomScale(0.125, 0.125), 1e-6f);
    }

    /**
     * Marker scale shrinks monotonically between identity zoom and the camera zoom-out limit.
     */
    @Test
    void markerZoomScale_betweenIdentityAndMinimum_isMonotonic() {

        float nearIdentity = MapMarkerRenderer.markerZoomScale(0.5, 0.125);
        float midpoint = MapMarkerRenderer.markerZoomScale(0.25, 0.125);
        float nearMinimum = MapMarkerRenderer.markerZoomScale(0.125, 0.125);

        assertTrue(nearIdentity > midpoint);
        assertTrue(midpoint > nearMinimum);
    }
}
