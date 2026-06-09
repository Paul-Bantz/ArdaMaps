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
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import io.tileverse.pmtiles.PMTilesReader;
import io.tileverse.rangereader.RangeReader;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * PMTilesProvider is a TileProvider implementation that retrieves map tiles from a PMTiles file.
 * It uses a PMTilesReader to access tile data and manages asynchronous loading of tiles into Minecraft's texture system.
 */
public abstract class PMTilesProvider extends TileProvider<PmTileKey> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PMTilesProvider.class);

    /** PMTiles reader for accessing tile data */
    protected PMTilesReader reader;

    /**
     * Asynchronously loads a map tile for the given tile key.
     *
     * @param key The tile key identifying the tile to load.
     */
    @Override
    public void loadTile(PmTileKey key) {

        if (reader == null) return;

        CompletableFuture.supplyAsync(() -> {

            Optional<ByteBuffer> optionalTile;
            try {
                optionalTile = reader.getTile(key.toTileId());
            } catch (IOException e) {
                LOGGER.error("Failed to read tile {} from PMTiles source", key, e);
                markTransportFailure(key);
                return null;
            }

            if (optionalTile.isEmpty()) return null;

            ByteBuffer buffer = optionalTile.get();
            buffer.rewind();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            try {
                return NativeImage.read(new ByteArrayInputStream(bytes));
            } catch (IOException e) {
                // Decode failures are not transport failures; keep key retriable.
                LOGGER.error("Failed to decode tile {}", key, e);
                return null;
            }

        }, ArdaMapsClient.IMAGE_EXECUTOR).thenAccept(image -> registerTexture("pmtiles_", image, key));
    }

    /**
     * Configures the TileProvider with the given PMTilesReader.
     * Sets up the reader and extracts zoom level information.
     *
     * @param rangeReader The PMTilesReader to use for tile retrieval.
     */
    public void configureReader(RangeReader rangeReader) throws IOException {

        this.reader = new PMTilesReader(rangeReader);

        var header = reader.getHeader();
        this.minZoom = Math.max(header.minZoom(), 0);
        this.maxZoom = Math.max(header.maxZoom(), 1);
    }
}