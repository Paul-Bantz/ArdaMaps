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

package com.duom.ardamaps.core.data.map.cameras;

import com.duom.ardamaps.core.data.config.Dimension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link GridCamera} zoom clamping.
 */
class GridCameraTest {

    /** Shared test dimension for grid camera bounds calculations. */
    private static final Dimension DIMENSION =
            new Dimension("Test", "test:grid", 1f, 0, 999, 0, 999, false);

    /**
     * Matching a carried render scale should clamp to the lower configured zoom bound.
     */
    @Test
    void setZoomToMatchVisualPixelsPerBlock_belowMinCameraZoom_clampsToBound() {

        GridCamera camera = configuredCamera();
        camera.setCameraZoomBounds(4, 6);
        camera.setPreferredRenderScale(1.0 / 256.0);

        camera.setZoomToMatchVisualPixelsPerBlock();

        assertEquals(4.0, camera.getZoom(), 1e-9);
        assertEquals(4.0, camera.targetCameraZoom, 1e-9);
    }

    /**
     * Matching a carried render scale should clamp to the upper configured zoom bound.
     */
    @Test
    void setZoomToMatchVisualPixelsPerBlock_aboveMaxCameraZoom_clampsToBound() {

        GridCamera camera = configuredCamera();
        camera.setCameraZoomBounds(4, 6);
        camera.setPreferredRenderScale(256.0);

        camera.setZoomToMatchVisualPixelsPerBlock();

        assertEquals(6.0, camera.getZoom(), 1e-9);
        assertEquals(6.0, camera.targetCameraZoom, 1e-9);
    }

    /**
     * Creates a grid camera with a dimension attached.
     *
     * @return The configured camera.
     */
    private static GridCamera configuredCamera() {

        GridCamera camera = new GridCamera(100, 100, 500, 500);
        camera.setDimension(DIMENSION);
        return camera;
    }
}
