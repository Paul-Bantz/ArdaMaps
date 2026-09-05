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

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link FlatMapCamera} coordinate conversion.
 */
class FlatMapCameraTest {

    /** Shared test dimension with a 1000-block square area. */
    private static final Dimension DIMENSION =
            new Dimension("Test", "test:flat", 1f, 0, 999, 0, 999, false);

    /**
     * Verifies that the overload accepting an explicit zoom parameter uses that zoom instead of the camera's current zoom.
     * This is essential for pre-viewport calculations and animation math.
     */
    @Test
    void screenToWorldCoordinates_atExplicitZoom_usesProvidedZoom() {

        FlatMapCamera camera = new FlatMapCamera(100, 100, 500, 500);
        camera.setDimension(DIMENSION);
        camera.setImageWidth(500);
        camera.setImageHeight(500);
        camera.setScale(1.0);
        camera.setIdentityZoom(2);
        camera.updateZoom(2);

        Vec2d world = camera.screenToWorldCoordinates(60, 50, 100, 100, 4);

        assertEquals(505.0, world.x(), 1e-9);
        assertEquals(500.0, world.y(), 1e-9);
    }

    /**
     * Matching a carried render scale should clamp to the lower configured zoom bound.
     */
    @Test
    void setZoomToMatchVisualPixelsPerBlock_belowMinCameraZoom_clampsToBound() {

        FlatMapCamera camera = configuredCamera();
        camera.setCameraZoomBounds(4, 6);
        camera.setPreferredRenderScale(1.0 / 512.0);

        camera.setZoomToMatchVisualPixelsPerBlock();

        assertEquals(4.0, camera.getZoom(), 1e-9);
        assertEquals(4.0, camera.targetCameraZoom, 1e-9);
    }

    /**
     * Matching a carried render scale should clamp to the upper configured zoom bound.
     */
    @Test
    void setZoomToMatchVisualPixelsPerBlock_aboveMaxCameraZoom_clampsToBound() {

        FlatMapCamera camera = configuredCamera();
        camera.setCameraZoomBounds(4, 6);
        camera.setPreferredRenderScale(512.0);

        camera.setZoomToMatchVisualPixelsPerBlock();

        assertEquals(6.0, camera.getZoom(), 1e-9);
        assertEquals(6.0, camera.targetCameraZoom, 1e-9);
    }

    /**
     * Public world X clamping should match the value stored by {@link MapCamera#setWorldX(double, double)}.
     */
    @Test
    void clampWorldX_matchesSetWorldXClamping() {

        FlatMapCamera camera = configuredCamera();

        double clamped = camera.clampWorldX(2000, 5);
        camera.setWorldX(2000, 5);

        double expectedMax = DIMENSION.getXMax()
                - 100.0 / (2.0 * camera.getVisualPixelsPerBlock())
                + 5.0 / camera.getVisualPixelsPerBlock();

        assertEquals(clamped, camera.getWorldX(), 1e-9);
        assertEquals(expectedMax, clamped, 1e-9);
    }

    /**
     * Public zoom clamping should respect the computed fit-to-content floor.
     */
    @Test
    void clampZoom_belowFitToContentFloor_clampsToFloor() {

        FlatMapCamera camera = configuredCamera();
        camera.setCameraZoomBounds(-10, 10);
        camera.computeZoomLevelToFitContentArea(1000, 1000);

        double clamped = camera.clampZoom(-10);

        assertEquals(3.0, clamped, 1e-9);
    }

    /**
     * Creates a flat camera with enough image metadata for scale matching.
     *
     * @return The configured camera.
     */
    private static FlatMapCamera configuredCamera() {

        FlatMapCamera camera = new FlatMapCamera(100, 100, 500, 500);
        camera.setDimension(DIMENSION);
        camera.setImageWidth(500);
        camera.setImageHeight(500);
        camera.setScale(1.0);
        camera.setIdentityZoom(2);
        camera.updateZoom(2);
        return camera;
    }
}
