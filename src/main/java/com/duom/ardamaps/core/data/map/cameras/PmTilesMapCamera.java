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
import net.minecraft.util.math.MathHelper;

import java.util.Set;

/**
 * A camera for pmtiles maps, handling zoom levels and tile visibility calculations.
 */
public class PmTilesMapCamera extends TilesMapCamera {

    /**
     * Create a new MapCamera
     *
     * @param viewportWidth  Viewport width in pixels
     * @param viewPortHeight Viewport height in pixels
     */
    public PmTilesMapCamera(int viewportWidth, int viewPortHeight, int centerX, int centerY) {

        super(2,8);

        this.tileSize = 256;

        this.identityZoom = 8;
        this.zoom = 8;
        this.targetCameraZoom = 8;

        this.minCameraZoom = 2;
        this.maxCameraZoom = 14;

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
     * Get list of visible tiles
     *
     * @return Set of visible TileKeys - tiles coordinates at current zoom level
     */
    @Override
    public Set<PmTileKey> getVisibleTiles(int tileZoom) {

        double scale = renderScale();

        int blocksPerTile = numberOfBlocksPerTile(tileZoom);

        // Calculate visible world area (screen size / scale)
        double visibleWorldWidth = Math.min(viewportWidth / scale, dimension.getWidth());
        double visibleWorldHeight = Math.min(viewportHeight / scale, dimension.getHeight());

        // World bounds of visible area
        double halfViePortWidth = (visibleWorldWidth / 2.0);
        double halfViePortHeight = (visibleWorldHeight / 2.0);

        double minWorldX = worldX - halfViePortWidth;
        double maxWorldX = worldX + halfViePortWidth;
        double minWorldZ = worldZ - halfViePortHeight;
        double maxWorldZ = worldZ + halfViePortHeight;

        int tilesBoundX = dimension.getWidth() / blocksPerTile;
        int tilesBoundY = dimension.getWidth() / blocksPerTile;

        // Convert to tile coordinates
        int minTileX = (int) Math.floor((minWorldX - dimension.getXMin()) / blocksPerTile);
        int maxTileX = (int) Math.floor((maxWorldX - dimension.getXMin()) / blocksPerTile);
        int minTileY = (int) Math.floor((minWorldZ - dimension.getZMin()) / blocksPerTile);
        int maxTileY = (int) Math.floor((maxWorldZ - dimension.getZMin()) / blocksPerTile);

        // Clamp to world bounds
        minTileX = Math.max(minTileX, 0);
        maxTileX = Math.min(tilesBoundX, maxTileX);
        minTileY = Math.max(minTileY, 0);
        maxTileY = Math.min(tilesBoundY, maxTileY);

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

        // Tile indices from PmTilesMapCamera are dimension-relative, so add the dimension origin back
        double tileWorldX = dimension.getXMin() + tileX * (double) blocksPerTile;
        double tileWorldZ = dimension.getZMin() + tileY * (double) blocksPerTile;

        // Add a buffer around the region to consider it explored if the player has explored just outside the tile boundaries
        return exploration.regionExplored(tileWorldX, tileWorldZ, blocksPerTile, blocksPerTile, 2);
    }

    /**
     * Get current zoom level clamped allowed by the tiles source configuration
     * Zoom levels outside this range are possible by scaling the underlying tiles,
     *
     * @return Current tile zoom level
     */
    @Override
    public int getTileSourceClampedZoom() {
        return (int) MathHelper.clamp(zoom, minTileZoom, maxTileZoom);
    }

    /**
     * Get render scale (pixels per world unit) for current zoom level
     * This is scale adjusted by tile scale factor, this affects
     * the size of rendered tiles on screen and the coordinates conversion
     */
    @Override
    public double renderScale() {
        return scale() * this.scale;
    }

    /**
     * Get render scale (pixels per world unit) at the given zoom level
     * This is scale adjusted by tile scale factor, this affects
     * the size of rendered tiles on screen and the coordinates conversion
     */
    private double renderScale(double zoom) {
        return scale(zoom) * this.scale;
    }

    /**
     * This method calculates how many blocks (world units) fit into one tile at the given zoom level.
     * <p>
     * Calculation is based on the fact that at zoom level <b>identityZoom</b>, one tile covers 256×256 blocks.
     * tiles are always <b>tileSize</b> (256×256 pixels usually) regardless of zoom level.
     * What changes is the geographic area each tile covers. At zoom level <b>identityZoom</b>, one tile covers a certain area,
     * at zoom level 7, one tile covers 4× that area (2×2 tiles from zoom <b>identityZoom</b>), etc.
     *
     * @param zoom the zoom level for which to calculate the number of blocks per tile.
     * @return number of blocks per tile at this zoom level
     */
    @Override
    public int numberOfBlocksPerTile(int zoom) {
        return tileSize * (1 << ((int) identityZoom - zoom));
    }

    /**
     * Get current scale factor (pixels per world unit)
     */
    @Override
    public double scale() {

        return scale(zoom);
    }

    /**
     * Get current scale factor at the given zoom (pixels per world unit)
     * @param zoom the zoom level to get the scale for
     */
    private double scale(double zoom) {

        return Math.pow(2.0, zoom - identityZoom);
    }

    /**
     * Convert screen coordinates to world coordinates
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param screenW Screen width
     * @param screenH Screen height
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

        double wx = worldX + (screenX - screenW / 2.0) / renderScale(zoom);
        double wz = worldZ + (screenY - screenH / 2.0) / renderScale(zoom);

        return new Vec2d(wx, wz);
    }

    /**
     * Get displayed tile size in pixels for current zoom level
     */
    @Override
    public int displayedTileSize(int tileZoom) {

        return (int) Math.ceil(numberOfBlocksPerTile(tileZoom) * renderScale());
    }

    /**
     * Get screen position of a tile on the viewport at a specific zoom level.
     * Use this overload when rendering tiles at a zoom level different from the current camera zoom
     * (e.g. fallback/coarse tiles).
     *
     * @param worldX   Tile world X coordinate
     * @param worldY   Tile world Y coordinate
     * @param tileZoom The zoom level the tile belongs to
     * @return Screen position as Vec2d
     */
    @Override
    public Vec2d tilePositionOnViewport(int worldX, int worldY, int tileZoom) {

        int worldUnitsPerTile = numberOfBlocksPerTile(tileZoom);

        // Calculate screen position
        return worldToScreenCoordinates(
                dimension.getXMin() + worldX * worldUnitsPerTile,
                dimension.getZMin() + worldY * worldUnitsPerTile
        );
    }

    /**
     * Get screen position of an object in world coordinates
     *
     * @param objWorldX Object world X coordinate
     * @param objWorldZ Object world Z coordinate
     * @return Screen position as Vec2d
     */
    @Override
    public Vec2d worldToScreenCoordinates(double objWorldX, double objWorldZ) {

        return new Vec2d(
                (objWorldX - worldX) * renderScale() + viewportWidth / 2.0,
                (objWorldZ - worldZ) * renderScale() + viewportHeight / 2.0
        );
    }

    /**
     * @return the world texture width in pixels, calculated based on the current render scale and world bounds
     */
    @Override
    public int getWorldTextureWidth() {

        return (int) Math.ceil(renderScale() * dimension.getWidth());
    }

    /**
     * @return the world texture height in pixels, calculated based on the current render scale and world bounds
     */
    @Override
    public int getWorldTextureHeight() {

        return (int) Math.ceil(renderScale() * dimension.getHeight());
    }

    /**
     * Get blocks per pixel on screen
     * At identity zoom: 1 pixel = 1 block
     * Zooming in (zoom > identity): multiple pixels per block (fractional blocks/pixel)
     * Zooming out (zoom < identity): multiple blocks per pixel (integer or >1)
     *
     * @return the number of blocks represented by a single pixel on the screen
     */
    @Override
    public double getBlocksPerPixel() {
        return renderScale();
    }

    /**
     * Visual pixels per block = renderScale(), since worldToScreenCoordinates multiplies directly by renderScale().
     *
     * @return the amount of pixels representing one block of the world at the current zoom level
     */
    @Override
    public double getVisualPixelsPerBlock() {
        return renderScale();
    }

    /**
     * Solve for zoom such that: pow(2, zoom - identityZoom) * scale = targetPixelsPerBlock
     */
    @Override
    public void setZoomToMatchVisualPixelsPerBlock() {

        if (Double.isNaN(preferredRenderScale)) return;

        double newZoom = (Math.log(preferredRenderScale / scale) / Math.log(2.0)) + identityZoom;
        newZoom = MathHelper.clamp(newZoom, minCameraZoom, maxCameraZoom);
        this.zoom = newZoom;
        this.targetCameraZoom = newZoom;

        // Once zoom is set reset targetPixelsPerBlock to NaN to prevent repeated zoom adjustments in subsequent frames
        preferredRenderScale = Double.NaN;
    }
}