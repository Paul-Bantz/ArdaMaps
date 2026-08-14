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

import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * Camera implementation for BlueMap-style maps, which use a tile-based LOD system with a configurable LOD factor.
 * <br/>In this system, each LOD level increases the world footprint of tiles by a factor (e.g., 5 means each LOD level covers 5 times more world area than the previous one).
 * <br/>The camera calculates visible tiles based on the current zoom level and viewport size, and provides methods to convert between world and screen coordinates.
 */
public class BlueMapCamera extends TilesMapCamera {

    /**
     * LOD distance multiplier: each LOD level increases the world footprint of tiles by this factor
     * (e.g., 5 means each LOD level covers 5 times more world area than the previous one).
     */
    @Getter
    @Setter
    private double lodFactor;

    /**
     * Last LOD level returned by {@link #getTileSourceClampedZoom()}. The raw computed LOD is
     * continuously re-evaluated against a damped, continuously-changing {@code zoom}, so without
     * hysteresis it can flip back and forth across an integer boundary every frame during a zoom
     * animation, churning the visible-tile set. {@code Integer.MIN_VALUE} means "not yet computed".
     */
    private int lastClampedZoom = Integer.MIN_VALUE;

    /** How far (in raw LOD units) the computed LOD must overshoot the current level before switching. */
    private static final double LOD_HYSTERESIS = 0.15;

    /**
     * Constructor for BlueMapCamera.
     *
     * @param viewportWidth  Width of the viewport in pixels
     * @param viewPortHeight Height of the viewport in pixels
     * @param centerX        Initial centre X coordinate of the camera in world coordinates
     * @param centerY        Initial centre Y coordinate of the camera in world coordinates
     */
    public BlueMapCamera(int viewportWidth, int viewPortHeight, int centerX, int centerY) {

        super(3, 1);

        // Fixed at 501px as defined by BlueMap
        this.tileSize = 501;

        this.lodFactor = 5.0;
        this.scale = 1;

        // Identity is 1 (low resolution tiles at zoom 1 = 1:1)
        this.identityZoom = 1;
        this.zoom = 1;
        this.targetCameraZoom = 1;

        this.minCameraZoom = 8;
        this.maxCameraZoom = 1;

        this.worldX = centerX;
        this.worldZ = centerY;

        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewPortHeight;
    }

    /**
     * Get list of visible tiles
     *
     * @return Set of visible TileKeys - tiles coordinates at current zoom level
     */
    @Override
    public Set<PmTileKey> getVisibleTiles() {

        return getVisibleTiles(getTileSourceClampedZoom());
    }

    /**
     * Get list of visible tiles for a given tile zoom level
     *
     * @param tileZoom Zoom level of the tiles to fetch
     * @return Set of visible TileKeys - tiles coordinates at given zoom level
     */
    @Override
    public Set<PmTileKey> getVisibleTiles(int tileZoom) {

        return getRequestTiles(tileZoom, 0);
    }

    @Override
    public Set<PmTileKey> getRequestTiles(int tileZoom, int rings) {

        int blocksPerTile = numberOfBlocksPerTile(tileZoom);

        // Calculate visible world area (screen size / scale)
        double visibleWorldWidth = Math.min(viewportWidth / scale(), dimension.getWidth());
        double visibleWorldHeight = Math.min(viewportHeight / scale(), dimension.getHeight());

        // World bounds of visible area
        double halfViePortWidth = (visibleWorldWidth / 2.0);
        double halfViePortHeight = (visibleWorldHeight / 2.0);

        double minWorldX = worldX - halfViePortWidth;
        double maxWorldX = worldX + halfViePortWidth;
        double minWorldZ = worldZ - halfViePortHeight;
        double maxWorldZ = worldZ + halfViePortHeight;

        int minTileX = (int) Math.floor(minWorldX / blocksPerTile);
        int maxTileX = (int) Math.floor(maxWorldX / blocksPerTile);
        int minTileY = (int) Math.floor(minWorldZ / blocksPerTile);
        int maxTileY = (int) Math.floor(maxWorldZ / blocksPerTile);

        // Clamp tile indices to world bounds to avoid fetching nonexistent tiles
        int boundMinTileX = (int) Math.floor((double) dimension.getXMin() / blocksPerTile);
        int boundMaxTileX = (int) Math.floor((double) dimension.getXMax() / blocksPerTile);
        int boundMinTileY = (int) Math.floor((double) dimension.getZMin() / blocksPerTile);
        int boundMaxTileY = (int) Math.floor((double) dimension.getZMax() / blocksPerTile);

        int expansion = Math.max(0, rings);
        minTileX = Math.max(minTileX - expansion, boundMinTileX);
        maxTileX = Math.min(maxTileX + expansion, boundMaxTileX);
        minTileY = Math.max(minTileY - expansion, boundMinTileY);
        maxTileY = Math.min(maxTileY + expansion, boundMaxTileY);

        return getVisibleExploredTiles(tileZoom, minTileX, maxTileX, minTileY, maxTileY, blocksPerTile);
    }

    /**
     * Check whether a tile is at least partially explored. If current exploration is null, return true.
     *
     * @param exploration   the exploration state to check against
     * @param tileX         the tile X coordinate
     * @param tileY         the tile Y coordinate
     * @param blocksPerTile the number of blocks per tile at the tile's zoom level
     * @return whether the tile is at least partially explored
     */
    @Override
    protected boolean tileExplored(PlayerExploration exploration, int tileX, int tileY, int blocksPerTile) {

        if (exploration == null)
            return true;

        double tileWorldX = tileX * (double) blocksPerTile;
        double tileWorldZ = tileY * (double) blocksPerTile;

        return exploration.regionExplored(tileWorldX, tileWorldZ, blocksPerTile, blocksPerTile, 2);
    }

    /**
     * Convert world coordinates to screen coordinates, taking into account the current camera position and zoom level.
     *
     * @param objWorldX X coordinate in the world
     * @param objWorldZ Z coordinate in the world
     * @return Vec2d containing screen coordinates (x,y)
     */
    @Override
    public Vec2d worldToScreenCoordinates(double objWorldX, double objWorldZ) {

        return new Vec2d(
                (objWorldX - worldX) * scale() + viewportWidth / 2.0,
                (objWorldZ - worldZ) * scale() + viewportHeight / 2.0
        );
    }

    /**
     * Get the current scale factor based on the zoom level.
     * The scale is calculated using the formula: scale = lodFactor^(identityZoom - zoom).
     *
     * @return The current scale factor for rendering, which determines how many pixels correspond to one block in the world.
     */
    @Override
    public double scale() {
        return scale(zoom);
    }

    /**
     * pixels per block: at identityZoom scale=1, zooming out (higher zoom value) reduces scale
     *
     * @param zoom Zoom level to calculate scale for
     * @return Scale factor for the given zoom level, calculated as lodFactor^(identityZoom - zoom)
     */
    private double scale(double zoom) {

        return Math.pow(lodFactor, identityZoom - zoom);
    }

    /**
     * Convert screen coordinates to world coordinates, taking into account the current camera position and zoom level.
     *
     * @param screenX X coordinate on the screen
     * @param screenY Y coordinate on the screen
     * @param screenW Width of the viewport
     * @param screenH Height of the viewport
     * @return Vec2d containing world coordinates (x,z)
     */
    @Override
    public Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH) {

        return screenToWorldCoordinates(screenX, screenY, screenW, screenH, zoom);
    }

    /**
     * Convert screen coordinates to world coordinates at the given zoom level, taking into account the current camera position and zoom level.
     *
     * @param screenX X coordinate on the screen
     * @param screenY Y coordinate on the screen
     * @param screenW Width of the viewport
     * @param screenH Height of the viewport
     * @param zoom    Zoom level to use for the conversion (used to calculate scale)
     * @return Vec2d containing world coordinates (x,z)
     */
    @Override
    public Vec2d screenToWorldCoordinates(double screenX, double screenY, int screenW, int screenH, double zoom) {

        double wx = worldX + (screenX - screenW / 2.0) / scale(zoom);
        double wz = worldZ + (screenY - screenH / 2.0) / scale(zoom);

        return new Vec2d(wx, wz);
    }

    /**
     * Get the visual pixels per block at the current zoom level.
     * For BlueMap, this is the same as the render scale, since tiles are rendered at their displayed size in pixels.
     *
     * @return The visual pixels per block, which is the same as the render scale in this case.
     */
    @Override
    public double getVisualPixelsPerBlock() {
        return this.renderScale();
    }

    /**
     * For BlueMap, the render scale is the same as the camera scale, as tiles are rendered at their displayed size in pixels.
     *
     * @return The scale factor for rendering, which is the same as the camera scale in this case.
     */
    @Override
    public double renderScale() {
        return scale();
    }

    /**
     * Adjust the camera zoom level so that the visual pixels per block matches the target value.
     * Inverts: scale() = lodFactor^(identityZoom - zoom)
     * Solving for zoom: zoom = identityZoom - log(preferredRenderScale) / log(lodFactor)
     */
    @Override
    public void setZoomToMatchVisualPixelsPerBlock() {

        if (Double.isNaN(preferredRenderScale)) return;

        double newZoom = identityZoom - (Math.log(preferredRenderScale) / Math.log(lodFactor));
        newZoom = CameraMath.clamp(newZoom, maxCameraZoom, minCameraZoom); // note: BlueMap min/max are inverted (higher value = more zoomed out)
        this.zoom = newZoom;
        this.targetCameraZoom = newZoom;

        // Once zoom is set, reset preferredRenderScale to prevent repeated adjustments
        preferredRenderScale = Double.NaN;
    }

    /**
     * Adjust the target camera zoom level based on mouse wheel input, while keeping the zoom anchor point fixed.
     * The zoom level is clamped to the defined minimum and maximum zoom levels, and also to the zoom level required to fit the content area if that is set.
     *
     * @param mouseX X coordinate of the mouse cursor on the screen
     * @param mouseY Y coordinate of the mouse cursor on the screen
     * @param width  Width of the viewport in pixels
     * @param height Height of the viewport in pixels
     * @param amount Amount to adjust the zoom level by (positive for zooming in, negative for zooming out)
     */
    @Override
    public void setZoom(double mouseX, double mouseY, int width, int height, double amount) {

        setZoomAnchor(mouseX, mouseY, width, height);
        setZoom(amount);
    }

    /**
     * Sets the zoom level
     *
     * @param amount Zoom amount (positive to zoom in, negative to zoom out)
     */
    @Override
    public void setZoom(double amount) {

        // Additional damping here to make zoom less snappy
        var newZoom = targetCameraZoom - amount * 0.35;

        if (newZoom < minCameraZoom || newZoom > maxCameraZoom) {
            targetCameraZoom = CameraMath.clamp(newZoom, maxCameraZoom, minCameraZoom);

        } else {
            targetCameraZoom = newZoom;
        }

        if (!Double.isNaN(zoomLevelToFitContentArea) && targetCameraZoom > zoomLevelToFitContentArea) {
            targetCameraZoom = zoomLevelToFitContentArea;
        }
    }

    /**
     * Get screen position of tile (top-left corner) for given tile coordinates (x,y) at a specific LOD level.
     *
     * @param x   Tile X coordinate
     * @param y   Tile Y coordinate
     * @param lod LOD level of the tile
     * @return Vec2d containing screen coordinates (x,y) of the top-left corner of the tile on the viewport
     */
    @Override
    public Vec2d tilePositionOnViewport(int x, int y, int lod) {

        var numberOfBlocksPerTile = numberOfBlocksPerTile(lod);
        double tileWorldX = x * numberOfBlocksPerTile;
        double tileWorldZ = y * numberOfBlocksPerTile;

        double screenX = ((tileWorldX - worldX) * scale() + viewportWidth / 2.0);
        double screenY = ((tileWorldZ - worldZ) * scale() + viewportHeight / 2.0);

        return new Vec2d(screenX, screenY);
    }

    /**
     * Get number of blocks per tile for a given LOD level
     * LOD world footprint: tileSize * lodFactor^(lod-1)
     *
     * @param lod LOD level (zoom level of the tile source)
     * @return Number of blocks per tile for the given LOD level
     */
    @Override
    protected int numberOfBlocksPerTile(int lod) {

        return (int) Math.round(tileSize * Math.pow(lodFactor, lod - 1));
    }

    /**
     * Get the coarsest LOD zoom level (highest z-value = lowest resolution).
     * In BlueMap, z+1 is coarser, so coarsest = minTileZoom (the stored minimum).
     *
     * @return The coarsest LOD zoom level
     */
    public int getCoarsestZoom() {
        return minTileZoom;
    }

    /**
     * Pick the current LOD and only switch to a coarser one when the next LOD would reach its native pixel size (1 tile px = 1 screen px).
     * This lets the current LOD tiles scale up by up to lodFactor× before switching, eliminating the sharp LOD cut.
     * <p>
     * A LOD-lod tile at native size satisfies: lodFactor^(lod-1) * scale() = 1.
     * The switch threshold for lod is therefore: lod = -log(scale()) / log(lodFactor).
     * So: lod = ceil(-log(scale()) / log(lodFactor)), clamped to [maxTileZoom, minTileZoom].
     * <p>
     * {@code zoom} is continuously damped every frame during a zoom animation (see
     * {@link MapCamera#update}), so the raw computed LOD can sit right at an integer boundary and
     * flip back and forth frame to frame. {@link #LOD_HYSTERESIS} requires the raw value to
     * overshoot the current level by a margin before switching, so a mid-animation LOD is sticky
     * instead of thrashing the visible-tile set (and therefore the tile request queue) every frame.
     *
     * @return Zoom level of the tile source to fetch, clamped to the range of available zoom levels in the pmtiles file.
     */
    @Override
    public int getTileSourceClampedZoom() {

        double s = scale();
        double raw = (s <= 0 || Double.isNaN(s)) ? minTileZoom : -Math.log(s) / Math.log(lodFactor);

        int candidate;
        if (lastClampedZoom == Integer.MIN_VALUE
                || raw > lastClampedZoom + LOD_HYSTERESIS
                || raw < lastClampedZoom - 1 - LOD_HYSTERESIS) {
            candidate = (int) Math.ceil(raw);
        } else {
            candidate = lastClampedZoom;
        }

        lastClampedZoom = CameraMath.clamp(candidate, maxTileZoom, minTileZoom);
        return lastClampedZoom;
    }

    /**
     * Get displayed tile size in pixels for a given LOD level
     */
    @Override
    public int displayedTileSize(int lod) {
        return (int) Math.ceil(numberOfBlocksPerTile(lod) * scale());
    }

    @Override
    public int getWorldTextureWidth() {
        return (int) Math.ceil(scale() * dimension.getWidth());
    }

    @Override
    public int getWorldTextureHeight() {
        return (int) Math.ceil(scale() * dimension.getHeight());
    }

    @Override
    public double getBlocksPerPixel() {
        return 1.0 / renderScale();
    }

    /**
     * Compute the zoom level required to fit the entire world within the content area,
     * based on the current bluemap LOD factor and tile size.
     *
     * @param contentWidth  Content area width in pixels
     * @param contentHeight Content area height in pixels
     */
    @Override
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

            zoomLevelToFitContentArea = identityZoom - Math.log(minPpb) / Math.log(lodFactor);
        }
    }
}
