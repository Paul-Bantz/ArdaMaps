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
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;

/**
 * A flat map camera implementation.
 */
@Setter
@Getter
public class FlatMapCamera extends MapCamera {

    /** Image width in pixels */
    private int imageWidth;

    /** Image height in pixels */
    private int imageHeight;

    /**
     * Creates a new FlatMapCamera.
     *
     * @param viewportWidth  The width of the viewport in pixels.
     * @param viewPortHeight The height of the viewport in pixels.
     * @param centerX        The initial centre X coordinate in world space.
     * @param centerY        The initial centre Y coordinate in world space.
     */
    public FlatMapCamera(int viewportWidth, int viewPortHeight, int centerX, int centerY) {

        this.worldX = centerX;
        this.worldZ = centerY;

        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewPortHeight;
    }

    /**
     * @return The render scale factor. In non-tiled maps, this is the same as scale.
     */
    @Override
    public double renderScale() {
        return scale();
    }

    /**
     * Gets the current scale of the camera.
     *
     * @return The scale factor.
     */
    @Override
    public double scale() {
        return scale(zoom);
    }

    /**
     * Gets the scale at the given zoom for the camera.
     *
     * @param zoom The zoom level to calculate the scale for.
     * @return The scale factor.
     */
    public double scale(double zoom) {
        return scale * Math.pow(2.0, zoom - identityZoom);
    }

    /**
     * Converts world coordinates to screen coordinates.
     *
     * @param objWorldX The X coordinate in world space.
     * @param objWorldZ The Z coordinate in world space.
     * @return A Vec2d representing the screen coordinates.
     */
    @Override
    public Vec2d worldToScreenCoordinates(double objWorldX, double objWorldZ) {

        return new Vec2d(
                ((objWorldX - worldX) / getBlocksPerPixel()) * scale() + viewportWidth / 2.0,
                ((objWorldZ - worldZ) / getBlocksPerPixel()) * scale() + viewportHeight / 2.0
        );
    }

    /**
     * Blocks per pixel = world width in blocks / world texture width in pixels
     * i.e. how many world blocks are represented by one pixel in the texture at identity zoom.
     *
     * @return the number of world blocks represented by each pixel in the texture at identity zoom
     */
    @Override
    public double getBlocksPerPixel() {

        return (double) dimension.getWidth() / imageWidth;
    }

    /* Getters and Setters */

    /**
     * Convert screen coordinates to world coordinates
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
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
    public Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH, double zoom){

        return new Vec2d(
                worldX + (screenX - screenW / 2.0) / scale() * getBlocksPerPixel(),
                worldZ + (screenY - screenH / 2.0) / scale() * getBlocksPerPixel()
        );
    }

    /**
     * Gets the height of the world texture in world units. This is used to calculate the blocks per pixel ratio.
     *
     * @return The height of the world texture in world units.
     */
    @Override
    public int getWorldTextureHeight() {
        return (int) Math.ceil(imageHeight * scale());
    }

    /**
     * Gets the width of the world texture in world units. This is used to calculate the blocks per pixel ratio.
     *
     * @return The width of the world texture in world units.
     */
    @Override
    public int getWorldTextureWidth() {
        return (int) Math.ceil(imageWidth * scale());
    }

    /**
     * Visual pixels per block = scale() / getBlocksPerPixel()
     * i.e. how many screen pixels one world block occupies at the current zoom.
     */
    @Override
    public double getVisualPixelsPerBlock() {
        return scale() / getBlocksPerPixel();
    }

    /**
     * Solve for zoom such that: scale * pow(2, zoom - identityZoom) / blocksPerPixel = targetPixelsPerBlock
     */
    @Override
    public void setZoomToMatchVisualPixelsPerBlock() {

        if (Double.isNaN(preferredRenderScale)) return;

        double newZoom = (Math.log(preferredRenderScale * getBlocksPerPixel() / scale) / Math.log(2.0)) + identityZoom;
        newZoom = MathHelper.clamp(newZoom, minCameraZoom, maxCameraZoom);
        this.zoom = newZoom;
        this.targetCameraZoom = newZoom;

        // Once zoom is set reset targetPixelsPerBlock to NaN to prevent repeated zoom adjustments in subsequent frames
        this.preferredRenderScale = Double.NaN;
    }
}