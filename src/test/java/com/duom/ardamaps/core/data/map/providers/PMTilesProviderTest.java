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
import io.tileverse.pmtiles.PMTilesDirectory;
import io.tileverse.pmtiles.PMTilesEntry;
import io.tileverse.pmtiles.PMTilesHeader;
import io.tileverse.pmtiles.PMTilesReader;
import io.tileverse.rangereader.RangeReader;
import net.minecraft.client.texture.NativeImage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PMTilesProviderTest {

    /**
     * Verify that loading after close is a no-op.
     */
    @Test
    void loadTileAfterCloseIsCleanNoOp() {

        var provider = new TestPmTilesProvider();
        var key = new PmTileKey(3, 1, 1);

        provider.loading.add(key);
        provider.close();
        provider.loadTile(key);

        assertFalse(provider.loading.contains(key));
    }

    /**
     * Verify that executor rejection does not escape and clears the in-flight key.
     */
    @Test
    void executorRejectionDoesNotEscapeAndClearsLoadingKey() throws Exception {

        assertInstanceOf(ThreadPoolExecutor.class, ArdaMapsClient.TILE_EXECUTOR);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) ArdaMapsClient.TILE_EXECUTOR;
        var provider = new TestPmTilesProvider();
        var key = new PmTileKey(3, 1, 1);
        var blockers = saturate(executor);

        provider.reader = Mockito.mock(PMTilesReader.class);

        try {
            provider.beginFrame();
            provider.request(key, 0);

            assertDoesNotThrow(provider::endFrame);
            assertFalse(provider.loading.contains(key));
        } finally {
            blockers.releaseAll();
            provider.close();
        }
    }

    /**
     * Verify that an undecodable tile is retried three times and then marked missing.
     */
    @Test
    void undecodableTileIsAttemptedThreeTimesThenMarkedMissing() throws IOException {

        var provider = new CountingPmTilesProvider();
        var key = new PmTileKey(3, 2, 2);
        var reader = Mockito.mock(PMTilesReader.class);

        Mockito.when(reader.getTile(Mockito.anyLong())).thenReturn(Optional.of(ByteBuffer.wrap(new byte[]{1, 2, 3, 4})));
        provider.reader = reader;

        for (int i = 0; i < 3; i++) {
            provider.loading.add(key);
            provider.loadTile(key);
        }

        assertEquals(3, provider.loadCalls);
        assertNotNull(provider.missingKeys.getIfPresent(key));

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertEquals(3, provider.loadCalls);
    }

    /**
     * Verify that clustered archives prewarm the leaf directory and contiguous coarse span.
     */
    @Test
    void bootstrapClusteredArchiveReadsLeafSectionThenCoarseExtent() {

        var provider = new TestPmTilesProvider();
        provider.minZoom = 0;
        provider.maxZoom = 3;
        var rangeReader = new RecordingRangeReader();
        var reader = mockBootstrapReader();

        provider.runRemoteBootstrap(rangeReader, reader, header(true));

        assertEquals(List.of(new RangeRead(300, 50), new RangeRead(1000, 200)), rangeReader.reads);
    }

    /**
     * Verify that unclustered archives skip bootstrap reads.
     */
    @Test
    void bootstrapUnclusteredArchiveSkipsReads() {

        var provider = new TestPmTilesProvider();
        provider.minZoom = 0;
        provider.maxZoom = 3;
        var rangeReader = new RecordingRangeReader();

        provider.runRemoteBootstrap(rangeReader, mockBootstrapReader(), header(false));

        assertTrue(rangeReader.reads.isEmpty());
    }

    /**
     * Verify that oversized bootstrap extents fall back to queued coarse tiles.
     */
    @Test
    void bootstrapExtentGuardAbortsDataReadButPumpStillRuns() {

        var provider = new BootstrapPmTilesProvider();
        provider.minZoom = 0;
        provider.maxZoom = 3;
        var rangeReader = new RecordingRangeReader();
        var reader = mockBootstrapReader(PMTilesEntry.of(0, 0, 33 * 1024 * 1024, 1));

        provider.runRemoteBootstrap(rangeReader, reader, header(true));

        assertEquals(List.of(new RangeRead(300, 50)), rangeReader.reads);

        provider.beginFrame();
        provider.endFrame();

        assertEquals(1, provider.loadCalls);
    }

    /**
     * Verify that an unsatisfiable range is caught and does not fail the bootstrap path.
     */
    @SuppressWarnings("resource")
    @Test
    void bootstrapRangeNotSatisfiableIsCaught() {

        var provider = new TestPmTilesProvider();
        provider.minZoom = 0;
        provider.maxZoom = 3;
        var rangeReader = new RecordingRangeReader();
        rangeReader.failOnRead = 2;

        assertDoesNotThrow(() -> provider.runRemoteBootstrap(rangeReader, mockBootstrapReader(), header(true)));
    }

    /**
     * Saturate the executor so submission rejection can be tested.
     *
     * @param executor Executor to saturate.
     * @return Handle for releasing the blocked tasks.
     * @throws InterruptedException If waiting for task start is interrupted.
     */
    private static Blockers saturate(ThreadPoolExecutor executor) throws InterruptedException {

        int tasks = executor.getCorePoolSize() + executor.getQueue().remainingCapacity();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(executor.getCorePoolSize());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < tasks; i++) {
            futures.add(executor.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertEquals(0, executor.getQueue().remainingCapacity());
        return new Blockers(release, futures);
    }

    /**
     * Handles release and cancellation of the saturated executor tasks.
     */
    private record Blockers(CountDownLatch release, List<Future<?>> futures) {

        private void releaseAll() {
            release.countDown();
            futures.forEach(future -> future.cancel(true));
        }
    }

    /**
     * PMTiles provider with default behavior for bootstrap tests.
     */
    private static final class TestPmTilesProvider extends PMTilesProvider {
    }

    /**
     * PMTiles provider that counts load attempts.
     */
    private static final class CountingPmTilesProvider extends PMTilesProvider {

        private int loadCalls;

        @Override
        public void loadTile(PmTileKey key) {
            loadCalls++;
            super.loadTile(key);
        }

        @Override
        CompletableFuture<NativeImage> submitTileLoad(Supplier<NativeImage> supplier) {
            return CompletableFuture.completedFuture(supplier.get());
        }
    }

    /**
     * PMTiles provider that only records bootstrap load calls.
     */
    private static final class BootstrapPmTilesProvider extends PMTilesProvider {

        private int loadCalls;

        @Override
        public void loadTile(PmTileKey key) {
            loadCalls++;
            clearLoading(key);
        }
    }

    /**
     * Build a synthetic PMTiles header for bootstrap tests.
     *
     * @param clustered Whether the archive should report clustered layout.
     * @return Synthetic PMTiles header.
     */
    private static PMTilesHeader header(boolean clustered) {

        return new PMTilesHeader(
                127L,
                65,
                192L,
                0L,
                300L,
                50,
                1000,
                10_000L,
                0L,
                0L,
                0L,
                clustered,
                PMTilesHeader.COMPRESSION_NONE,
                PMTilesHeader.COMPRESSION_NONE,
                PMTilesHeader.TILETYPE_PNG,
                (byte) 0,
                (byte) 3,
                0,
                0,
                0,
                0,
                (byte) 0,
                0,
                0);
    }

    /**
     * Build a mock PMTiles reader with a scripted root and leaf directory.
     *
     * @param tileEntries Entries to place in the leaf directory.
     * @return Mocked PMTiles reader.
     */
    private static PMTilesReader mockBootstrapReader(PMTilesEntry... tileEntries) {

        PMTilesEntry rootLeaf = PMTilesEntry.of(0, 0, 50, 0);
        PMTilesDirectory root = directory(rootLeaf);
        PMTilesDirectory leaf = tileEntries.length == 0
                ? directory(PMTilesEntry.of(0, 0, 100, 1), PMTilesEntry.of(1, 100, 100, 1))
                : directory(tileEntries);

        PMTilesReader reader = Mockito.mock(PMTilesReader.class);
        when(reader.getRootDirectory()).thenReturn(root);
        when(reader.getDirectory(rootLeaf)).thenReturn(leaf);
        return reader;
    }

    /**
     * Build a mock PMTiles directory from a fixed set of entries.
     *
     * @param entries Entries exposed by the directory iterator.
     * @return Mocked PMTiles directory.
     */
    private static PMTilesDirectory directory(PMTilesEntry... entries) {

        PMTilesDirectory directory = Mockito.mock(PMTilesDirectory.class);
        when(directory.iterator()).thenAnswer(ignored -> List.of(entries).iterator());
        return directory;
    }

    /**
     * Captured range read parameters.
     */
    private record RangeRead(long offset, int length) {

    }

    /**
     * Range reader that records each request for later assertions.
     */
    private static final class RecordingRangeReader implements RangeReader {

        private final List<RangeRead> reads = new ArrayList<>();
        private int failOnRead = -1;

        @Override
        public int readRange(long offset, int length, ByteBuffer target) throws IOException {

            reads.add(new RangeRead(offset, length));
            if (reads.size() == failOnRead) throw new IOException("416 Range Not Satisfiable");
            target.put(new byte[Math.min(length, target.remaining())]);
            return length;
        }

        @Override
        public OptionalLong size() {
            return OptionalLong.empty();
        }

        @Override
        public String getSourceIdentifier() {
            return "test";
        }

        @Override
        public void close() {
        }
    }
}
