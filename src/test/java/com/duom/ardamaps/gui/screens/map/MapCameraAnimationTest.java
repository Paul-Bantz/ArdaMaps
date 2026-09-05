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

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MapCameraAnimation} timing, interpolation, and cancellation behaviour.
 */
class MapCameraAnimationTest {

    /**
     * Verifies that a new animation starts idle.
     * This protects callers that gate camera updates on {@link MapCameraAnimation#isRunning()}.
     */
    @Test
    void isRunning_beforeStart_returnsFalse() {

        var clock = new MutableClock(100);
        var animation = new MapCameraAnimation(clock);

        assertFalse(animation.isRunning());
    }

    /**
     * Verifies that applying at t=0 leaves the camera at the captured start position and zoom.
     * This protects the animation from jumping on the first frame before any time has elapsed.
     */
    @Test
    void apply_atStart_keepsStartPosition() {

        var clock = new MutableClock(100);
        var animation = new MapCameraAnimation(clock);
        var camera = new FakeCamera(10, 20, 3);

        animation.start(new Vec2d(110, 220), camera, 8);

        assertTrue(animation.apply(camera, 5, 6));
        assertEquals(10, camera.getWorldX());
        assertEquals(20, camera.getWorldZ());
        assertEquals(3, camera.getZoom());
        assertEquals(5, camera.lastOffsetX);
        assertEquals(6, camera.lastOffsetZ);
    }

    /**
     * Verifies that applying at or beyond the duration snaps exactly to the target state and stops the animation.
     * This protects against drift or lingering "running" state after the easing window completes.
     */
    @Test
    void apply_atDuration_setsTargetAndStops() {

        var clock = new MutableClock(100);
        var animation = new MapCameraAnimation(clock);
        var camera = new FakeCamera(10, 20, 3);

        animation.start(new Vec2d(110, 220), camera, 8);
        clock.now = 1100;

        assertFalse(animation.apply(camera, 0, 0));
        assertFalse(animation.isRunning());
        assertEquals(110, camera.getWorldX());
        assertEquals(220, camera.getWorldZ());
        assertEquals(8, camera.getZoom());
    }

    /**
     * Verifies that cancelling mid-flight prevents further camera updates.
     * This matters because user input is expected to abort auto-pan immediately.
     */
    @Test
    void cancel_midFlight_stopsAnimation() {

        var clock = new MutableClock(100);
        var animation = new MapCameraAnimation(clock);
        var camera = new FakeCamera(10, 20, 3);

        animation.start(new Vec2d(110, 220), camera, 8);
        animation.cancel();
        clock.now = 600;

        assertFalse(animation.isRunning());
        assertFalse(animation.apply(camera, 0, 0));
        assertEquals(10, camera.getWorldX());
        assertEquals(20, camera.getWorldZ());
        assertEquals(3, camera.getZoom());
    }

    /**
     * Mutable fake clock used to advance animation time deterministically in tests.
     */
    private static class MutableClock implements LongSupplier {

        /** Current mocked time in milliseconds returned to the animation. */
        private long now;

        /**
         * Creates a fake clock initialized to the supplied timestamp.
         *
         * @param now The initial mocked time in milliseconds.
         */
        @SuppressWarnings("SameParameterValue")
        private MutableClock(long now) {
            this.now = now;
        }

        /**
         * Returns the current mocked time.
         *
         * @return The mocked timestamp in milliseconds.
         */
        @Override
        public long getAsLong() {
            return now;
        }
    }

    /**
     * Minimal camera stub that records world position, zoom, and offset writes from the animation.
     */
    private static class FakeCamera extends MapCamera {

        /** Last horizontal frame offset passed to {@link #setWorldX(double, double)}. */
        private double lastOffsetX;

        /** Last vertical frame offset passed to {@link #setWorldZ(double, double)}. */
        private double lastOffsetZ;

        /**
         * Creates a fake camera with explicit initial world position and zoom.
         *
         * @param worldX Initial world X.
         * @param worldZ Initial world Z.
         * @param zoom   Initial zoom.
         */
        @SuppressWarnings("SameParameterValue")
        private FakeCamera(double worldX, double worldZ, double zoom) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.zoom = zoom;
        }

        /**
         * Records the animated horizontal camera position and frame offset.
         *
         * @param worldX The world X assigned by the animation.
         * @param offset The frame offset supplied by the caller.
         */
        @Override
        public void setWorldX(double worldX, double offset) {
            this.worldX = worldX;
            this.lastOffsetX = offset;
        }

        /**
         * Records the animated vertical camera position and frame offset.
         *
         * @param worldZ The world Z assigned by the animation.
         * @param offset The frame offset supplied by the caller.
         */
        @Override
        public void setWorldZ(double worldZ, double offset) {
            this.worldZ = worldZ;
            this.lastOffsetZ = offset;
        }

        /**
         * Returns a constant scale because this stub does not model real projection math.
         *
         * @return Always {@code 1}.
         */
        @Override
        public double scale() {
            return 1;
        }

        /**
         * Returns a constant render scale because this stub does not model real projection math.
         *
         * @return Always {@code 1}.
         */
        @Override
        public double renderScale() {
            return 1;
        }

        /**
         * Identity-maps world coordinates to screen coordinates for deterministic testing.
         *
         * @param screenX Input X value.
         * @param screenY Input Y value.
         * @return The same coordinates wrapped as {@link Vec2d}.
         */
        @Override
        public Vec2d worldToScreenCoordinates(double screenX, double screenY) {
            return new Vec2d(screenX, screenY);
        }

        /**
         * Identity-maps screen coordinates to world coordinates for deterministic testing.
         *
         * @param screenX Input X value.
         * @param screenY Input Y value.
         * @param screenW Ignored stub viewport width.
         * @param screenH Ignored stub viewport height.
         * @return The same coordinates wrapped as {@link Vec2d}.
         */
        @Override
        public Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH) {
            return new Vec2d(screenX, screenY);
        }

        /**
         * Identity-maps screen coordinates to world coordinates for deterministic testing at an arbitrary zoom.
         *
         * @param screenX Input X value.
         * @param screenY Input Y value.
         * @param screenW Ignored stub viewport width.
         * @param screenH Ignored stub viewport height.
         * @param zoom    Ignored stub zoom.
         * @return The same coordinates wrapped as {@link Vec2d}.
         */
        @Override
        public Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH, double zoom) {
            return new Vec2d(screenX, screenY);
        }

        /**
         * Returns a constant pixels-per-block value because this stub does not simulate rendering scale.
         *
         * @return Always {@code 1}.
         */
        @Override
        public double getVisualPixelsPerBlock() {
            return 1;
        }

        /**
         * No-op for the stub camera because it does not model scale matching.
         */
        @Override
        public void setZoomToMatchVisualPixelsPerBlock() {
        }

        /**
         * Returns a non-zero texture width placeholder required by the abstract contract.
         *
         * @return Always {@code 1}.
         */
        @Override
        public int getWorldTextureWidth() {
            return 1;
        }

        /**
         * Returns a non-zero texture height placeholder required by the abstract contract.
         *
         * @return Always {@code 1}.
         */
        @Override
        public int getWorldTextureHeight() {
            return 1;
        }

        /**
         * Returns a constant blocks-per-pixel value because this stub does not simulate zoom scaling.
         *
         * @return Always {@code 1}.
         */
        @Override
        public double getBlocksPerPixel() {
            return 1;
        }
    }
}
