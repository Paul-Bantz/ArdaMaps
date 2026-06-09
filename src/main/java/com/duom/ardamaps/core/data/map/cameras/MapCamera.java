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
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;

/**
 * Abstract base class for map cameras, handling zoom levels and world-to-screen coordinate conversions.
 */
public abstract class MapCamera {

    /** Epsilon value for snapping zoom levels to avoid jitter from tiny floating point differences during smooth zooming. */
    private static final double ZOOM_EPSILON = 0.001;

    /** Viewport width - this is the "window into the world" width */
    @Getter
    protected int viewportWidth;

    /** Viewport height - this is the "window into the world" height */
    @Getter
    protected int viewportHeight;

    /** Target pixels per block for matching zoom levels across map camera types */
    @Setter
    protected double preferredRenderScale = Double.NaN;

    /** Maximum camera zoom level */
    protected int maxCameraZoom;

    /** Minimum camera zoom level */
    protected int minCameraZoom;

    /**
     * Minimum zoom level to fit the entire world within the viewport,
     * computed based on current viewport size and world dimensions.
     * This is used to prevent zooming out so far that the world becomes smaller than the viewport.
     */
    protected double zoomLevelToFitContentArea = Double.NaN;

    /** Cache of the last viewport width used to compute minZoomToFitContentArea, to avoid unnecessary recalculations. */
    protected int cachedViewportWidth = -1;

    /** Cache of the last viewport height used to compute minZoomToFitContentArea, to avoid unnecessary recalculations. */
    protected int cachedViewportHeight = -1;

    /** Identity zoom level (1:1 scale - 1 block = 1 pixel) */
    @Getter
    protected double identityZoom = -1;

    /** Current zoom level */
    @Getter
    protected double zoom;

    /** Target zoom level for smooth zooming */
    protected double targetCameraZoom;

    /** Camera centre X in world coordinates */
    @Getter
    protected double worldX;

    /** Camera centre Z in world coordinates */
    @Getter
    protected double worldZ;

    /** Current scale (pixels per block) */
    @Setter
    protected double scale;

    /** World coordinates of the zoom anchor point (world position under mouse when zoom was triggered) */
    private Vec2d zoomAnchorWorld;

    /** Screen coordinates of the zoom anchor point (mouse position when zoom was triggered) */
    private Vec2d zoomAnchorScreen;

    /** Dimension definition for the current dimension, used for clamping camera position within world bounds. */
    @Setter
    @Getter
    protected Dimension dimension;

    /**
     * Set the viewport size
     *
     * @param width  Viewport width in pixels
     * @param height Viewport height in pixels
     */
    public void setViewportSize(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    /**
     * @return Current camera scale (pixels per block)
     */
    @SuppressWarnings("unused")
    public abstract double scale();

    /**
     * @return Current render scale (pixels per block) for rendering calculations
     */
    @SuppressWarnings("unused")
    public abstract double renderScale();

    /**
     * Update camera state
     *
     * @param deltaTime    Time elapsed since last update in seconds
     * @param frameOffsetX map frame outer horizontal offset for panning adjustment
     * @param frameOffsetZ map frame outer vertical for panning adjustment
     */
    public void update(double deltaTime, double frameOffsetX, double frameOffsetZ) {

        var zoomDiff = targetCameraZoom - zoom;

        if (Math.abs(zoomDiff) > 0.01) {

            // Frame-rate independent damping
            double alpha = 1.0 - Math.exp(-1 * deltaTime);

            zoom += zoomDiff * alpha;
            zoom = snapZoom(zoom);

        } else if (zoom != targetCameraZoom) {

            // Reset to target zoom if close enough - avoids tiny oscillations
            zoom = snapZoom(targetCameraZoom);
        }

        // If a zoom anchor is set, continuously re-pan the camera so the anchored world point
        // stays under the mouse cursor throughout the smooth zoom animation.
        if (zoomAnchorWorld != null && zoomAnchorScreen != null) {

            Vec2d currentScreen = worldToScreenCoordinates(zoomAnchorWorld.x(), zoomAnchorWorld.y());
            double dx = zoomAnchorScreen.x() - currentScreen.x();
            double dy = zoomAnchorScreen.y() - currentScreen.y();
            Vec2d corrected = screenToWorldCoordinates(viewportWidth / 2.0 - dx, viewportHeight / 2.0 - dy);

            worldX = corrected.x();
            worldZ = corrected.y();

            // Clear anchor once zoom has settled
            if (Math.abs(zoomDiff) <= 0.01) {
                zoomAnchorWorld = null;
                zoomAnchorScreen = null;
            }
        }

        // Re-clamp pan position so bounds stay tight when zoom changes
        setWorldX(worldX, frameOffsetX);
        setWorldZ(worldZ, frameOffsetZ);
    }

    /**
     * Snap zoom level to a fixed precision to avoid jitter from tiny floating point differences during smooth zooming.
     *
     * @param zoom The zoom level to snap
     * @return The snapped zoom level
     */
    private static double snapZoom(double zoom) {
        return Math.round(zoom / ZOOM_EPSILON) * ZOOM_EPSILON;
    }

    /**
     * Convert screen coordinates to world coordinates
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @return World coordinates as Vec2d
     */
    public abstract Vec2d worldToScreenCoordinates(double screenX, double screenY);

    /**
     * Convert screen coordinates to world coordinates
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @return World coordinates as Vec2d
     */
    public final Vec2d screenToWorldCoordinates(double screenX, double screenY) {
        return screenToWorldCoordinates(screenX, screenY, viewportWidth, viewportHeight);
    }

    /**
     * Converts screen coordinates to world coordinates at the given zoom level
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param zoom    the zoom level for which to compute the world coordinates
     * @return World coordinates as Vec2d
     */
    public final Vec2d screenToWorldCoordinates(double screenX, double screenY, double zoom) {
        return screenToWorldCoordinates(screenX, screenY, viewportWidth, viewportHeight, zoom);
    }

    /**
     * Set camera centre X in world coordinates, clamped so WorldBounds edges never scroll past the viewport edge.
     *
     * @param worldX The world X coordinate to set the camera centre to
     */
    public void setWorldX(double worldX, double offset) {

        double halfW = viewportWidth / (2.0 * getVisualPixelsPerBlock());
        var worldOffset = offset / getVisualPixelsPerBlock();

        double lo = dimension.getXMin() + halfW - worldOffset;
        double hi = dimension.getXMax() - halfW + worldOffset;

        this.worldX = lo <= hi ? MathHelper.clamp(worldX, lo, hi) : (dimension.getXMin() + dimension.getXMax()) / 2.0;
    }

    /**
     * Set camera centre Z in world coordinates, clamped so WorldBounds edges never scroll past the viewport edge.
     */
    public void setWorldZ(double worldZ, double offset) {

        double halfH = viewportHeight / (2.0 * getVisualPixelsPerBlock());
        var worldOffset = offset / getVisualPixelsPerBlock();

        double lo = dimension.getZMin() + halfH - worldOffset;
        double hi = dimension.getZMax() - halfH + worldOffset;

        this.worldZ = lo <= hi ? MathHelper.clamp(worldZ, lo, hi) : (dimension.getZMin() + dimension.getZMax()) / 2.0;
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
    public abstract Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH);

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
    public abstract Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH, double zoom);

    /**
     * Get the visual pixels per block at the current zoom level.
     * This is the true on-screen pixel size of one world block, used to match zoom levels across maps.
     *
     * @return Visual pixels per block
     */
    public abstract double getVisualPixelsPerBlock();

    /**
     * Set camera zoom bounds. This is the allowed zoom range for the user.
     *
     * @param minZoom Minimum zoom level
     * @param maxZoom Maximum zoom level
     */
    public void setCameraZoomBounds(int minZoom, int maxZoom) {

        this.minCameraZoom = minZoom;
        this.maxCameraZoom = maxZoom;
    }

    /**
     * Set identity zoom level (1:1 scale - 1 block = 1 pixel)
     *
     * @param identityZoom The identity zoom level to set
     */
    public void setIdentityZoom(double identityZoom) {
        this.identityZoom = identityZoom;
        this.zoom = identityZoom;
        this.targetCameraZoom = identityZoom;
    }

    /**
     * Convert world coordinates to screen coordinates
     *
     * @param worldPosition World position as Vec2d
     * @return Screen coordinates as Vec2d
     */
    public Vec2d worldToScreenCoordinates(Vec2d worldPosition) {
        return worldToScreenCoordinates(worldPosition.x(), worldPosition.y());
    }

    /**
     * Set zoom level so that the visual pixels per block matches the given value as closely as possible.
     * This is used to preserve the visual zoom level when switching between maps.
     */
    @SuppressWarnings("unused")
    public abstract void setZoomToMatchVisualPixelsPerBlock();

    /**
     * Set zoom level based on mouse position and zoom amount, anchored to mouse position
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param width  Viewport width
     * @param height Viewport height
     * @param amount Zoom amount (positive to zoom in, negative to zoom out)
     */
    public void setZoom(double mouseX, double mouseY, int width, int height, double amount) {

        setZoomAnchor(mouseX, mouseY, width, height);
        setZoom(amount);
    }

    /**
     * Sets the zoom level
     *
     * @param amount Zoom amount (positive to zoom in, negative to zoom out)
     */
    public void setZoom(double amount) {

        var newZoom = targetCameraZoom + amount;

        if (newZoom < minCameraZoom || newZoom > maxCameraZoom) {
            targetCameraZoom = MathHelper.clamp(newZoom, minCameraZoom, maxCameraZoom);
        } else {
            targetCameraZoom = newZoom;
        }

        /*
         * If minimum zoom to fit content area is defined, enforce it as a lower bound to prevent zooming out so far
         * that the world becomes smaller than the viewport.
         */
        if (!Double.isNaN(zoomLevelToFitContentArea) && targetCameraZoom < zoomLevelToFitContentArea) {
            targetCameraZoom = zoomLevelToFitContentArea;
        }
    }

    public void updateZoom(double zoom) {

        this.zoom = snapZoom(zoom);
        this.targetCameraZoom = this.zoom;
    }

    /**
     * Capture the world point under the mouse cursor as the zoom anchor.
     * Call this before changing targetCameraZoom so the anchor is recorded at the pre-zoom scale.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param width  Viewport width
     * @param height Viewport height
     */
    protected void setZoomAnchor(double mouseX, double mouseY, int width, int height) {
        zoomAnchorWorld = screenToWorldCoordinates(mouseX, mouseY, width, height);
        zoomAnchorScreen = new Vec2d(mouseX, mouseY);
    }

    /**
     * Reset the zoom anchor, clearing any stored anchor point.
     */
    public void resetZoomAnchor() {
        zoomAnchorWorld = null;
        zoomAnchorScreen = null;
    }

    /**
     * Get complete world texture width in pixels. IE : for a flatmap that would be the image width,
     * for a tiled map it would be the total width of all tiles combined at identity zoom.
     *
     * @return The world texture width in pixels
     */
    public abstract int getWorldTextureWidth();

    /**
     * Get complete world texture height in pixels. IE : for a flatmap that would be the image height,
     * for a tiled map it would be the total height of all tiles combined at identity zoom.
     *
     * @return The world texture height in pixels
     */
    public abstract int getWorldTextureHeight();

    /**
     * Get number of blocks represented by each pixel at current zoom level
     *
     * @return Blocks per pixel
     */
    @SuppressWarnings("unused")
    public abstract double getBlocksPerPixel();

    /**
     * Compute the minimum zoom level at which the entire world fits inside the given content area.
     * Uses whichever dimension (width or height) is more restrictive.
     *
     * @param contentWidth  Content area width in pixels
     * @param contentHeight Content area height in pixels
     */
    public void computeZoomLevelToFitContentArea(int contentWidth, int contentHeight) {

        if (Double.isNaN(zoomLevelToFitContentArea) ||
                contentWidth != cachedViewportWidth ||
                contentHeight != cachedViewportHeight) {

            cachedViewportHeight = contentHeight;
            cachedViewportWidth = contentWidth;

            // Minimum pixels-per-block so the full world just fits in the content area
            double minPpb = Math.max(
                    contentWidth / (double) dimension.getWidth(),
                    contentHeight / (double) dimension.getHeight()
            );

            // Derive ppb at identityZoom from current ppb and current zoom:
            // ppb(z) = ppbIdentity * 2^(z - identityZoom)  =>  ppbIdentity = ppb(z) * 2^(identityZoom - z)
            double ppbAtIdentity = getVisualPixelsPerBlock() * Math.pow(2.0, identityZoom - zoom);

            zoomLevelToFitContentArea = identityZoom + Math.log(minPpb / ppbAtIdentity) / Math.log(2.0);
        }
    }
}