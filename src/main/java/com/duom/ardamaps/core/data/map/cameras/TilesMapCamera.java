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

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import lombok.Setter;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashSet;
import java.util.Set;

public abstract class TilesMapCamera extends MapCamera {

    /** Tile size in pixels */
    @Setter
    protected int tileSize;

    /** Maximum zoom level allowed by the pmtiles file */
    protected int maxTileZoom;

    /** Minimum zoom level allowed by the pmtiles file */
    protected int minTileZoom;

    /** Current exploration state for the tileset */
    private PlayerExploration playerExploration;

    /**
     * Constructor for a tiled map camera.
     *
     * @param minTileZoom the minimum tile zoom
     * @param maxTileZoom the maximum tile zoom
     */
    public TilesMapCamera(int minTileZoom, int maxTileZoom) {

        this.minTileZoom = minTileZoom;
        this.maxTileZoom = maxTileZoom;
    }

    /**
     * Get list of visible tiles
     *
     * @return Set of visible TileKeys - tiles coordinates at current zoom level
     */
    @SuppressWarnings("unused")
    public abstract Set<PmTileKey> getVisibleTiles();

    /**
     * Get list of visible tiles for a given tile zoom level
     *
     * @param tileZoom Zoom level of the tiles to fetch
     * @return Set of visible TileKeys - tiles coordinates at given zoom level
     */
    @SuppressWarnings("unused")
    public abstract Set<PmTileKey> getVisibleTiles(int tileZoom);

    /**
     * Get number of blocks per tile for a given zoom level
     *
     * @param zoom level of the tile source
     * @return Number of blocks per tile for the given zoom level
     */
    @SuppressWarnings("unused")
    protected abstract int numberOfBlocksPerTile(int zoom);

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
    @SuppressWarnings("unused")
    public abstract Vec2d tilePositionOnViewport(int worldX, int worldY, int tileZoom);

    /**
     * Get displayed tile size in pixels for current zoom level
     *
     * @param tileZoom the zoom level to calculate tile size for
     */
    @SuppressWarnings("unused")
    public abstract int displayedTileSize(int tileZoom);

    /**
     * Get current zoom level clamped allowed by the tiles source configuration
     * Zoom levels outside this range are possible by scaling the underlying tiles,
     *
     * @return Current tile zoom level
     */
    @SuppressWarnings("unused")
    public abstract int getTileSourceClampedZoom();

    /**
     * Set tile zoom bounds. This is the allowed zoom range for the tile source.
     *
     * @param minZoom Minimum zoom level
     * @param maxZoom Maximum zoom level
     */
    public void setTilesZoomBounds(int minZoom, int maxZoom) {

        this.minTileZoom = minZoom;
        this.maxTileZoom = maxZoom;
    }

    /**
     * Returns a set of tile keys that are at least visible and explored for the given bounds.
     * If reveal all is active, returns the full tile range within the given bounds.
     *
     * @param tileZoom      the current tile zoom level
     * @param minTileX      the minimum tile X coordinate to consider
     * @param maxTileX      the maximum tile X coordinate to consider
     * @param minTileY      the minimum tile Y coordinate to consider
     * @param maxTileY      the maximum tile Y coordinate to consider
     * @param blocksPerTile the number of blocks per tile
     * @return the set of tile keys that are at least visible and explored (if map reveal all is disabled) within the given tile coordinate bounds and zoom level
     */
    protected @NonNull Set<PmTileKey> getVisibleExploredTiles(int tileZoom,
                                                              int minTileX, int maxTileX,
                                                              int minTileY, int maxTileY,
                                                              int blocksPerTile) {

        boolean revealAll = ArdaMapsClient.CONFIG != null && ArdaMapsClient.CONFIG.isMapRevealAll();

        Set<PmTileKey> result = new LinkedHashSet<>();
        for (int x = minTileX; x <= maxTileX; x++) {
            for (int y = minTileY; y <= maxTileY; y++) {
                if (revealAll || tileExplored(playerExploration, x, y, blocksPerTile)) {
                    result.add(new PmTileKey(tileZoom, x, y));
                }
            }
        }

        return result;
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
    protected abstract boolean tileExplored(PlayerExploration exploration, int tileX, int tileY, int blocksPerTile);

    /**
     * Sets the dimension for this camera. References the player exploration if dimension is valid.
     * @param dimension The DimensionDefinition to set for this camera
     */
    @Override
    public void setDimension(Dimension dimension) {

        super.setDimension(dimension);

        var clientProgress = ArdaMapsClient.CONFIG.getClientProgress();

        if (this.dimension != null && clientProgress != null)
            this.playerExploration = clientProgress.getExplorationState(dimension.getId(), false);
    }
}
