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

/**
 * A camera for the grid renderer. Uses the same zoom convention as TiledMapCamera:
 * identity zoom = 8 (1 block = 1 pixel), range [1, 14].
 */
public class GridCamera extends MapCamera {

    private static final int IDENTITY = 8;

    public GridCamera(int viewportWidth, int viewPortHeight, int centerX, int centerY) {

        this.worldX = centerX;
        this.worldZ = centerY;

        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewPortHeight;

        this.identityZoom = IDENTITY;
        this.zoom = IDENTITY;
        this.targetCameraZoom = IDENTITY;

        this.minCameraZoom = 1;
        this.maxCameraZoom = 14;
    }

    /**
     * @return Pixels per block at current zoom. At zoom == 8, scale == 1.0.
     */
    @Override
    public double scale() {
        return scale(zoom);
    }

    /**
     * @param zoom Zoom level to calculate scale for.
     * @return Pixels per block at current zoom. At zoom == 8, scale == 1.0.
     */
    private double scale(double zoom) {
        return Math.pow(2.0, zoom - IDENTITY);
    }

    @Override
    public double renderScale() {
        return scale();
    }

    /** Blocks represented by one pixel at current zoom. */
    @Override
    public double getBlocksPerPixel() {
        return 1.0 / scale();
    }

    @Override
    public double getVisualPixelsPerBlock() {
        return scale();
    }

    @Override
    public void setZoomToMatchVisualPixelsPerBlock() {
        if (!Double.isNaN(preferredRenderScale) && preferredRenderScale > 0) {
            double targetZoom = IDENTITY + Math.log(preferredRenderScale) / Math.log(2.0);
            zoom = targetZoom;
            targetCameraZoom = targetZoom;
        }
    }

    @Override
    public Vec2d worldToScreenCoordinates(double objWorldX, double objWorldZ) {
        return new Vec2d(
                (objWorldX - worldX) * scale() + viewportWidth / 2.0,
                (objWorldZ - worldZ) * scale() + viewportHeight / 2.0
        );
    }

    /**
     * Convert screen coordinates to world coordinates, given the viewport size. This is used for coordinate conversions before the viewport size is set.
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param screenW Viewport width in pixels
     * @param screenH Viewport height in pixels
     * @return World coordinates as Vec2d
     */
    @Override
    public Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH) {

        return screenToWorldCoordinates(screenX, screenY, screenW, screenH, zoom);
    }

    /**
     * Convert screen coordinates to world coordinates at a given zoom level, given the viewport size. This is used for coordinate conversions before the viewport size is set.
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param screenW Viewport width in pixels
     * @param screenH Viewport height in pixels
     * @param zoom    the zoom level for which to compute the world coordinates
     * @return World coordinates as Vec2d
     */
    @Override
    public Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH, double zoom) {

        return new Vec2d(
                worldX + (screenX - screenW / 2.0) / scale(zoom),
                worldZ + (screenY - screenH / 2.0) / scale(zoom)
        );
    }

    /**
     * Width of the entire dimension in pixels at the current zoom level.
     * Used by renderFogOfWar() to size the fog quad.
     */
    @Override
    public int getWorldTextureWidth() {
        return (int) Math.ceil(dimension.getWidth() * scale());
    }

    /**
     * Height of the entire dimension in pixels at the current zoom level.
     * Used by renderFogOfWar() to size the fog quad.
     */
    @Override
    public int getWorldTextureHeight() {
        return (int) Math.ceil(dimension.getHeight() * scale());
    }
}
