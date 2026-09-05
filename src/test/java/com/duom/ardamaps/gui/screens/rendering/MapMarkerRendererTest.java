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

/**
 * Marker renderer validation.
 */
class MapMarkerRendererTest {

    /**
     * Marker graphics stay full size at identity zoom and when zoomed in.
     */
    @Test
    void markerZoomScale_identityAndZoomedIn_clampsToFullSize() {

        assertEquals(1f, MapMarkerRenderer.markerZoomScale(1.0, 1.0 / 64.0));
        assertEquals(1f, MapMarkerRenderer.markerZoomScale(4.0, 1.0 / 64.0));
    }

    /**
     * Marker graphics reach minimum size at the effective zoom-out limit.
     */
    @Test
    void markerZoomScale_atAndPastLimit_clampsToMinimum() {

        assertEquals(0.25f, MapMarkerRenderer.markerZoomScale(1.0 / 64.0, 1.0 / 64.0));
        assertEquals(0.25f, MapMarkerRenderer.markerZoomScale(1.0 / 128.0, 1.0 / 64.0));
    }

    /**
     * Marker graphics shrink gradually across the full zoom-out range.
     */
    @Test
    void markerZoomScale_zoomedOut_interpolatesAcrossRange() {

        assertEquals(0.625f, MapMarkerRenderer.markerZoomScale(1.0 / 8.0, 1.0 / 64.0), 1e-6f);
        assertEquals(0.875f, MapMarkerRenderer.markerZoomScale(0.5, 1.0 / 64.0), 1e-6f);
    }

    /**
     * Degenerate zoom-out ranges keep markers full size.
     */
    @Test
    void markerZoomScale_degenerateZoomRange_returnsFullSize() {

        assertEquals(1f, MapMarkerRenderer.markerZoomScale(0.5, 1.0));
        assertEquals(1f, MapMarkerRenderer.markerZoomScale(0.5, 2.0));
    }

    /**
     * Invalid camera scales fall back to full-size marker graphics.
     */
    @Test
    void markerZoomScale_nan_returnsFullSize() {

        assertEquals(1f, MapMarkerRenderer.markerZoomScale(Double.NaN, 1.0 / 64.0));
        assertEquals(1f, MapMarkerRenderer.markerZoomScale(0.5, Double.NaN));
    }
}
