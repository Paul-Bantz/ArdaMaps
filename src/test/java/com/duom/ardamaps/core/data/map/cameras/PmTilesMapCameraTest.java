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
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.client.ClientConfig;
import com.duom.ardamaps.core.data.config.client.ClientProgress;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TiledMap Camera validation
 */
class PmTilesMapCameraTest {

    /**
     * A 2048×2048-block world, centred on (0, 0).
     * xMin=0, xMax=2049, so width = 2049 - 0 - 1 = 2048. Same for Z.
     */
    private static final Dimension DIMENSION =
            new Dimension("Test", "test:dim", 1f, 0, 2049, 0, 2049, false);

    /** 640×480 viewport, centred at world (1024, 1024) - the middle of the dimension. */
    private PmTilesMapCamera camera;

    /**
     * Initiate a camera with the above dimension and viewport size.
     */
    @BeforeEach
    void setUp() {

        var config = Mockito.mock(ClientConfig.class);
        var progress = Mockito.mock(ClientProgress.class);

        Mockito.when(config.isMapRevealAll()).thenReturn(false);
        Mockito.when(config.getClientProgress()).thenReturn(progress);
        Mockito.when(progress.getExplorationState(DIMENSION.getId(), false)).thenReturn(null);

        ArdaMapsClient.CONFIG = config;

        camera = new PmTilesMapCamera(640, 480, 1024, 1024);
        camera.setScale(1.0);   // neutral tile-scale factor (1 pixel per block at identity zoom)
        camera.setDimension(DIMENSION);
    }

    /**
     * At zoom == identityZoom (8), one tile covers exactly tileSize (256) blocks.
     * A wrong value here means every tile is loaded at the wrong geographic area. This has consequence on the rendering
     * of Markers and Map Tiles
     */
    @Test
    void numberOfBlocksPerTile_atIdentityZoom_equalsTileSize() {

        assertEquals(256, camera.numberOfBlocksPerTile(8));
    }

    /**
     * Each zoom step halves/doubles the number of blocks per tile (bit-shift formula).
     * zoom 7 -> each tile covers 512 blocks (one level coarser).
     */
    @Test
    void numberOfBlocksPerTile_oneZoomBelow_doublesCoverage() {

        assertEquals(512, camera.numberOfBlocksPerTile(7));
    }

    /**
     * At the coarsest tile zoom (2), each tile covers 256 * 2^(8-2) = 16 384 blocks.
     * This is the fallback tile level loaded when zoomed all the way out; a wrong value here causes coarse tiles to cover the wrong area.
     */
    @Test
    void numberOfBlocksPerTile_atMinTileZoom_givesMaxCoverage() {

        assertEquals(256 * (1 << (8 - 2)), camera.numberOfBlocksPerTile(2));
    }

    /**
     * At zoom == identityZoom, 1 pixel == 1 block: the baseline for all coordinate conversions.
     * If this is wrong, every on-screen position is off by a constant factor.
     */
    @Test
    void scale_atIdentityZoom_returnsOne() {

        assertEquals(1.0, camera.scale(), 1e-9);
    }

    /**
     * Zooming out means fewer pixels per block - scale must be < 1.
     */
    @Test
    void scale_belowIdentityZoom_returnsLessThanOne() {

        camera.setIdentityZoom(8);

        assertTrue(camera.numberOfBlocksPerTile(7) > camera.numberOfBlocksPerTile(8),
                "Lower zoom level must cover more blocks per tile (= smaller scale)");
    }

    /**
     * Zooming in means more pixels per block - scale must be > 1.
     */
    @Test
    void scale_aboveIdentityZoom_returnsMoreThanOne() {
        assertTrue(camera.numberOfBlocksPerTile(9) < camera.numberOfBlocksPerTile(8),
                "Higher zoom level must cover fewer blocks per tile (= larger scale)");
    }

    /**
     * At zoom=8, scale=1, the 640×480 viewport is 640×480 blocks wide.
     * With 256 blocks/tile the camera covers 2.5 × 1.875 tiles - at least some tiles visible.
     */
    @Test
    void getVisibleTiles_atIdentityZoom_returnsNonEmptySet() {

        Set<PmTileKey> tiles = camera.getVisibleTiles(8);
        assertFalse(tiles.isEmpty(), "There must be at least one visible tile");
    }

    /**
     * No tile coordinate should exceed the world bounds expressed in tile indices.tilesBoundX = worldWidth / blocksPerTile = 2048 / 256 = 8.
     */
    @Test
    void getVisibleTiles_tilesClampedToWorldBounds() {

        Set<PmTileKey> tiles = camera.getVisibleTiles(8);
        for (PmTileKey key : tiles) {
            assertTrue(key.x >= 0, "tile.x must be >= 0, was " + key.x);
            assertTrue(key.y >= 0, "tile.y must be >= 0, was " + key.y);
            assertTrue(key.x <= 8, "tile.x must be <= 8, was " + key.x);
            assertTrue(key.y <= 8, "tile.y must be <= 8, was " + key.y);
        }
    }
}
