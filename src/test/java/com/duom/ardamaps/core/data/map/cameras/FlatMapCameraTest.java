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

    /**
     * Verifies that the overload accepting an explicit zoom parameter uses that zoom instead of the camera's current zoom.
     * This is essential for pre-viewport calculations and animation math.
     */
    @Test
    void screenToWorldCoordinates_atExplicitZoom_usesProvidedZoom() {

        FlatMapCamera camera = new FlatMapCamera(100, 100, 500, 500);
        camera.setDimension(new Dimension("Test", "test:flat", 1f, 0, 999, 0, 999, false));
        camera.setImageWidth(500);
        camera.setImageHeight(500);
        camera.setScale(1.0);
        camera.setIdentityZoom(2);
        camera.updateZoom(2);

        Vec2d world = camera.screenToWorldCoordinates(60, 50, 100, 100, 4);

        assertEquals(505.0, world.x(), 1e-9);
        assertEquals(500.0, world.y(), 1e-9);
    }
}
