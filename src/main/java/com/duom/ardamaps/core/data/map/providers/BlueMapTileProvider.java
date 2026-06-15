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

package com.duom.ardamaps.core.data.map.providers;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.map.tiles.TileKey;
import org.jetbrains.annotations.NotNull;

/**
 * Tile provider that fetches map tiles from a BlueMap server.
 */
public class BlueMapTileProvider extends TileProvider<TileKey> {

    /** The minimum level of detail (LOD) to load. */
    private final int minLod;

    /** The maximum level of detail (LOD) to load. */
    private final int maxLod;

    /** The root path for BlueMap tiles. */
    private final String blueMapRoot;

    /**
     * Constructs a BlueMapTileProvider with the specified root path.
     *
     * @param path The root path for BlueMap tiles.
     * @param minLod The minimum level of detail (LOD) to load.
     * @param maxLod The maximum level of detail (LOD) to load.
     */
    public BlueMapTileProvider(String path, int minLod, int maxLod) {

        this.blueMapRoot = path;
        this.minLod = minLod;
        this.maxLod = maxLod;
        ArdaMapsClient.getHttpImageProvider().scheduleBlueMapRefresh(blueMapRoot);
    }

    /**
     * Asynchronously loads a map tile for the given tile key.
     *
     * @param key The tile key identifying the tile to load.
     */
    @Override
    protected void loadTile(TileKey key) {

        if (key.z < minLod || key.z > maxLod)
            return; // Always skip out-of-range tiles

        ArdaMapsClient.getHttpImageProvider().loadImage(
                getUrlForKey(key),
                image -> registerTexture("bluemap_", image == null ? null : image.getLeft(), key),
                () -> markTransportFailure(key)
        );
    }

    /**
     * Constructs the URL for the given tile key.
     *
     * @param key The tile key.
     * @return The URL string for the tile.
     */
    private @NotNull String getUrlForKey(TileKey key) {

        return "%s/%d/x%d/z%d.png".formatted(blueMapRoot, key.z, key.x, key.y);
    }
}