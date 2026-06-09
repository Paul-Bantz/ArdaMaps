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

import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.cache.CachingRangeReader;
import io.tileverse.rangereader.cache.DiskCachingRangeReader;
import io.tileverse.rangereader.http.HttpRangeReader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Tile provider that reads map tiles from a PMTiles file over HTTP.
 */
public class PMTilesHttpTileProvider extends PMTilesProvider {

    /** Private constructor to prevent direct instantiation */
    private PMTilesHttpTileProvider() {/* Instantiated via init */}

    /**
     * Create a PMTilesHttpTileProvider from the specified PMTiles HTTP URI.
     * This constructor is expected to fail with an IOException if the URI is inaccessible.
     *
     * @param uri The URI to the PMTiles file
     */
    public static TileProvider<PmTileKey> init(String uri) throws IOException {

        var httpTilesProvider = new PMTilesFileTileProvider();

        HttpRangeReader rangeReader = HttpRangeReader.builder()
                .uri(URI.create(uri))
                .build();

        RangeReader diskCached = DiskCachingRangeReader.builder(rangeReader)
                .cacheDirectory(Path.of(Client.cacheDirectory().toUri()))
                .withBlockAlignment()
                .maxCacheSizeBytes(200_000_000)
                .build();

        CachingRangeReader memoryCached = CachingRangeReader.builder(diskCached)
                .withBlockAlignment()
                .build();

        httpTilesProvider.configureReader(memoryCached);

        return httpTilesProvider;
    }
}