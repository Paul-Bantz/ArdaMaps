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

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import com.mojang.blaze3d.platform.NativeImage;
import io.tileverse.pmtiles.PMTilesDirectory;
import io.tileverse.pmtiles.PMTilesEntry;
import io.tileverse.pmtiles.PMTilesHeader;
import io.tileverse.pmtiles.PMTilesReader;
import io.tileverse.rangereader.RangeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * PMTilesProvider is a TileProvider implementation that retrieves map tiles from a PMTiles file.
 * It uses a PMTilesReader to access tile data and manages asynchronous loading of tiles into Minecraft's texture system.
 */
public abstract class PMTilesProvider extends TileProvider<PmTileKey> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PMTilesProvider.class);

    /** PMTiles reader for accessing tile data */
    protected volatile PMTilesReader reader;

    /** Human-readable archive path/URI for diagnostics. */
    protected volatile String archivePath = "unknown PMTiles archive";

    /**
     * Remote PMTiles bootstrap target: min zoom plus three levels. On the measured archive this is
     * z<=3, 55 tiles, about 3.8 MB of contiguous tile data after the leaf directory section.
     */
    private static final int COARSE_PYRAMID_EXTRA_ZOOMS = 3;

    /** Abort coarse data prewarm if the resolved contiguous span is larger than this. */
    private static final long MAX_BOOTSTRAP_EXTENT_BYTES = 32L * 1024 * 1024;

    /**
     * Asynchronously loads a map tile for the given tile key.
     *
     * @param key The tile key identifying the tile to load.
     */
    @Override
    public void loadTile(PmTileKey key) {

        // Snapshot the reader once: `close()` may null the field concurrently, and re-reading it
        // inside the lambda would be a check-then-use race against that assignment.
        PMTilesReader activeReader = reader;

        if (activeReader == null) {
            clearLoading(key);
            return;
        }

        try {
            submitTileLoad(() -> {

                if (closed) return null;

                Optional<ByteBuffer> optionalTile;
                try {
                    optionalTile = activeReader.getTile(key.toTileId());
                } catch (IOException e) {
                    LOGGER.warn("Failed to read tile {} from PMTiles source", key, e);
                    markTransportFailure(key);
                    return null;
                } catch (RuntimeException e) {
                    LOGGER.warn("Unexpected error reading tile {} from PMTiles source", key, e);
                    markTransportFailure(key);
                    return null;
                }

                if (optionalTile.isEmpty()) {
                    markMissing(key);
                    return null;
                }

                ByteBuffer buffer = optionalTile.get();
                buffer.rewind();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                try {

                    return NativeImage.read(new ByteArrayInputStream(bytes));

                } catch (IOException | RuntimeException e) {
                    logDecodeFailure(key, e);

                    return null;
                }

            }).whenComplete((image, ex) -> {
                if (ex != null) {
                    LOGGER.error("Unexpected async failure loading PMTiles tile {}", key, ex);
                    clearLoading(key);
                    return;
                }

                registerTexture("pmtiles_", image, key);
            });
        } catch (RejectedExecutionException e) {
            LOGGER.warn("PMTiles tile executor rejected tile {}, retrying next frame", key);
            clearLoading(key);
        }
    }

    CompletableFuture<NativeImage> submitTileLoad(Supplier<NativeImage> supplier) {

        return CompletableFuture.supplyAsync(supplier, ArdaMapsClient.TILE_EXECUTOR);
    }

    private void logDecodeFailure(PmTileKey key, Exception e) {

        int failures = decodeFailureCounts.getOrDefault(key, 0) + 1;
        if (failures >= 3) {
            LOGGER.error("Abandoning undecodable PMTiles tile {} from {} after {} attempts", key, archivePath, failures, e);
        } else if (failures == 1) {
            LOGGER.warn("Failed to decode PMTiles tile {} from {}", key, archivePath, e);
        }
        markDecodeFailure(key);
    }

    protected void setArchivePath(String archivePath) {

        this.archivePath = archivePath == null || archivePath.isBlank() ? "unknown PMTiles archive" : archivePath;
    }

    /**
     * Configures the TileProvider with the given PMTilesReader.
     * Sets up the reader and extracts zoom level information.
     *
     * @param rangeReader The PMTilesReader to use for tile retrieval.
     */
    public void configureReader(RangeReader rangeReader) throws IOException {

        configureReader(rangeReader, false);
    }

    /**
     * Configures the TileProvider with the given PMTilesReader.
     *
     * @param rangeReader      The PMTilesReader to use for tile retrieval.
     * @param bootstrapRemote  Whether to start remote coarse-pyramid bootstrap after configuration.
     */
    public void configureReader(RangeReader rangeReader, boolean bootstrapRemote) throws IOException {

        this.reader = new PMTilesReader(rangeReader);

        var header = reader.getHeader();
        this.minZoom = Math.max(header.minZoom(), 0);
        this.maxZoom = Math.max(header.maxZoom(), 1);

        if (bootstrapRemote && coarsePyramidBootstrapEnabled()) {
            scheduleRemoteBootstrap(rangeReader, reader, header);
        }
    }

    private boolean coarsePyramidBootstrapEnabled() {

        return ArdaMapsClient.CONFIG == null || ArdaMapsClient.CONFIG.isCoarsePyramidBootstrap();
    }

    private void scheduleRemoteBootstrap(RangeReader rangeReader, PMTilesReader activeReader, PMTilesHeader header) {

        CompletableFuture.runAsync(() -> runRemoteBootstrap(rangeReader, activeReader, header), ArdaMaps.IO_EXECUTOR)
                .exceptionally(ex -> {
                    LOGGER.warn("PMTiles coarse pyramid bootstrap failed for {}", archivePath, ex);
                    return null;
                });
    }

    void runRemoteBootstrap(RangeReader rangeReader, PMTilesReader activeReader, PMTilesHeader header) {

        if (!header.clustered()) {
            LOGGER.warn("Skipping PMTiles coarse pyramid bootstrap for {} because archive is not clustered", archivePath);
            return;
        }

        try {
            if (header.leafDirsBytes() > 0) {
                rangeReader.readRange(header.leafDirsOffset(), checkedLength(header.leafDirsBytes()));
            }

            Extent extent = resolveCoarseExtent(activeReader, header);
            if (extent == null) {
                enqueueBootstrapTiles(coarsePyramidKeys());
                return;
            }

            if (extent.length() > MAX_BOOTSTRAP_EXTENT_BYTES) {
                LOGGER.warn("Skipping PMTiles coarse data prewarm for {} because resolved span is {} bytes", archivePath, extent.length());
                enqueueBootstrapTiles(coarsePyramidKeys());
                return;
            }

            rangeReader.readRange(extent.offset(), checkedLength(extent.length()));
        } catch (IOException | RuntimeException e) {
            if (isRangeNotSatisfiable(e)) {
                LOGGER.warn("Skipping PMTiles coarse data prewarm for {} after unsatisfiable range", archivePath);
            } else {
                LOGGER.warn("Skipping PMTiles coarse data prewarm for {}", archivePath, e);
            }
        }

        enqueueBootstrapTiles(coarsePyramidKeys());
    }

    private Extent resolveCoarseExtent(PMTilesReader activeReader, PMTilesHeader header) {

        long upperBound = PmTileKey.tileIdUpperBound(bootstrapMaxZoom());
        ExtentAccumulator accumulator = new ExtentAccumulator(header.tileDataOffset());

        collectCoarseEntries(activeReader.getRootDirectory(), activeReader, header, upperBound, accumulator);

        return accumulator.toExtent();
    }

    private void collectCoarseEntries(PMTilesDirectory directory,
                                      PMTilesReader activeReader,
                                      PMTilesHeader header,
                                      long upperBound,
                                      ExtentAccumulator accumulator) {

        for (PMTilesEntry entry : directory) {
            if (entry.tileId() >= upperBound) break;

            if (entry.isLeaf()) {
                collectCoarseEntries(activeReader.getDirectory(entry), activeReader, header, upperBound, accumulator);
            } else {
                accumulator.accept(header.tileDataOffset() + entry.offset(), entry.length());
            }
        }
    }

    private List<PmTileKey> coarsePyramidKeys() {

        int maxZoom = bootstrapMaxZoom();
        List<PmTileKey> keys = new ArrayList<>();

        for (int z = minZoom; z <= maxZoom; z++) {
            int edge = 1 << z;
            for (int x = 0; x < edge; x++) {
                for (int y = 0; y < edge; y++) {
                    keys.add(new PmTileKey(z, x, y));
                }
            }
        }

        return keys;
    }

    private int bootstrapMaxZoom() {

        return Math.min(maxZoom, minZoom + COARSE_PYRAMID_EXTRA_ZOOMS);
    }

    private static int checkedLength(long length) {

        if (length > Integer.MAX_VALUE) throw new IllegalArgumentException("Range too large: " + length);
        return (int) length;
    }

    private static boolean isRangeNotSatisfiable(Throwable throwable) {

        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("416")) return true;
            current = current.getCause();
        }
        return false;
    }

    /**
     * Releases PMTiles reader resources and registered tile textures.
     */
    @Override
    public void close() {

        super.close();

        if (reader != null) {
            try {

                reader.close();

            } catch (IOException e) {

                LOGGER.warn("Failed to close PMTiles reader", e);

            } finally {
                reader = null;
            }
        }
    }

    private record Extent(long offset, long length) {

    }

    private static final class ExtentAccumulator {

        private final long tileDataOffset;
        private long minOffset = Long.MAX_VALUE;
        private long maxEnd = Long.MIN_VALUE;

        private ExtentAccumulator(long tileDataOffset) {

            this.tileDataOffset = tileDataOffset;
        }

        private void accept(long absoluteOffset, int length) {

            minOffset = Math.min(minOffset, absoluteOffset);
            maxEnd = Math.max(maxEnd, absoluteOffset + length);
        }

        private Extent toExtent() {

            if (minOffset == Long.MAX_VALUE) return null;
            long offset = Math.max(tileDataOffset, minOffset);
            return new Extent(offset, maxEnd - offset);
        }
    }
}
