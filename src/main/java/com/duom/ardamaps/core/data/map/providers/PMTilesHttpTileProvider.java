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
import io.tileverse.rangereader.cache.CachingRangeReader;
import io.tileverse.rangereader.cache.DiskCachingRangeReader;
import io.tileverse.rangereader.http.HttpRangeReader;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tile provider that reads map tiles from a PMTiles file over HTTP.
 */
public class PMTilesHttpTileProvider extends PMTilesProvider {

    /** Clear hooks for live PMTiles disk caches. */
    private static final Set<Runnable> DISK_CACHE_CLEAR_HOOKS = ConcurrentHashMap.newKeySet();

    /** Clear hook registered for this provider's disk cache. */
    private Runnable diskCacheClearHook;

    /** HTTP client owned by this provider. */
    private DelegatingHttpClient httpClient;

    /** Private constructor to prevent direct instantiation */
    private PMTilesHttpTileProvider() {/* Instantiated via init */}

    /**
     * Clears registered PMTiles disk caches.
     */
    public static void clearDiskCaches() {

        DISK_CACHE_CLEAR_HOOKS.forEach(Runnable::run);
    }

    /**
     * Create a PMTilesHttpTileProvider from the specified PMTiles HTTP URI.
     * This constructor is expected to fail with an IOException if the URI is inaccessible.
     *
     * @param uri The URI to the PMTiles file
     * @return A tile provider backed by the requested PMTiles URI.
     */
    public static TileProvider<PmTileKey> init(String uri) throws IOException {

        var httpTilesProvider = new PMTilesHttpTileProvider();
        httpTilesProvider.setArchivePath(uri);

        DelegatingHttpClient httpClient = DelegatingHttpClient.create();
        httpTilesProvider.httpClient = httpClient;

        HttpRangeReader rangeReader = HttpRangeReader.builder()
                .uri(URI.create(uri))
                .httpClient(httpClient)
                .build();

        DiskCachingRangeReader diskCached = DiskCachingRangeReader.builder(rangeReader)
                .cacheDirectory(Client.cacheDirectory().resolve("pmtiles-http"))
                .withBlockAlignment()
                .maxCacheSizeBytes(200_000_000)
                .build();
        httpTilesProvider.diskCacheClearHook = diskCached::clearCache;
        DISK_CACHE_CLEAR_HOOKS.add(httpTilesProvider.diskCacheClearHook);

        CachingRangeReader memoryCached = CachingRangeReader.builder(diskCached)
                .withBlockAlignment()
                .build();

        try {
            httpTilesProvider.configureReader(memoryCached, true);
            return httpTilesProvider;
        } catch (IOException | RuntimeException e) {
            httpTilesProvider.close();
            throw e;
        }
    }

    /**
     * Releases remote PMTiles resources and unregisters this provider's cache hook.
     */
    @Override
    public void close() {

        if (diskCacheClearHook != null) {
            DISK_CACHE_CLEAR_HOOKS.remove(diskCacheClearHook);
            diskCacheClearHook = null;
        }

        super.close();

        if (httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
    }
}
