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

import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import com.mojang.blaze3d.platform.NativeImage;
import io.tileverse.pmtiles.PMTilesDirectory;
import io.tileverse.pmtiles.PMTilesEntry;
import io.tileverse.pmtiles.PMTilesHeader;
import io.tileverse.pmtiles.PMTilesReader;
import io.tileverse.rangereader.RangeReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PMTilesProvider}'s tile-loading lifecycle: the reader/closed race that used to
 * surface as a repeating {@link NullPointerException}, and the transport-failure/missing-tile
 * bookkeeping that keeps a broken or sparse source from being retried every frame.
 */
class PMTilesProviderTest {

    /**
     * Verifies an {@link IOException} from the reader marks the key transport-failed rather than
     * leaving it immediately retriable.
     */
    @Test
    @Timeout(5)
    void loadTile_readerIOException_marksTransportFailure() throws IOException {

        var provider = new TestPMTilesProvider();
        var reader = mock(PMTilesReader.class);
        var key = new PmTileKey(4, 1, 1);

        when(reader.getTile(anyLong())).thenThrow(new IOException("boom"));
        provider.reader = reader;

        provider.loadTile(key);

        awaitTrue(() -> !provider.loading.contains(key));

        assertTrue(provider.peek(key).isEmpty());

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertTrue(provider.peek(key).isEmpty(), "Transport-failed key must not load");
    }

    /**
     * Verifies an empty {@link Optional} tile (present in range but absent from the archive) marks
     * the key as missing so it is never retried, distinguishing it from a transport failure.
     */
    @Test
    @Timeout(5)
    void loadTile_emptyOptional_marksMissing() throws IOException {

        var provider = new TestPMTilesProvider();
        var reader = mock(PMTilesReader.class);
        var key = new PmTileKey(4, 2, 2);

        when(reader.getTile(anyLong())).thenReturn(Optional.empty());
        provider.reader = reader;

        provider.loadTile(key);

        awaitTrue(() -> provider.missingKeys.getIfPresent(key) != null);
    }

    /**
     * Verifies {@code close()} while a load is queued does not throw and leaves the provider's
     * transient state clean, reproducing the conditions that used to surface as a repeating NPE.
     */
    @Test
    @Timeout(5)
    void close_whileLoadInFlight_doesNotThrowAndClearsLoadingState() throws IOException {

        var provider = new TestPMTilesProvider();
        var reader = mock(PMTilesReader.class);
        var key = new PmTileKey(4, 3, 3);

        // Block the reader call so close() can race the in-flight task deterministically.
        var releaseLatch = new CountDownLatch(1);
        when(reader.getTile(anyLong())).thenAnswer(_ -> {
            releaseLatch.await();
            return Optional.empty();
        });
        provider.reader = reader;

        assertTrue(provider.loading.add(key));
        provider.loadTile(key);

        assertDoesNotThrow(provider::close);
        releaseLatch.countDown();

        awaitTrue(() -> !provider.loading.contains(key));
    }

    /**
     * Polls the given condition until it becomes true, failing the test after a bounded timeout
     * instead of hanging (the surrounding {@code @Timeout} is the hard backstop).
     *
     * @param condition The condition to poll.
     */
    @SuppressWarnings("BusyWait")
    private static void awaitTrue(BooleanSupplier condition) {

        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for async condition");
            }
        }
        fail("Condition not met within timeout");
    }

    /**
     * Verifies a null reader (e.g. never configured, or already closed) is a no-op rather than an NPE.
     */
    @Test
    void loadTile_nullReader_clearsLoadingWithoutThrowing() {

        var provider = new TestPMTilesProvider();
        var key = new PmTileKey(4, 4, 4);

        provider.loading.add(key);

        assertDoesNotThrow(() -> provider.loadTile(key));

        assertFalse(provider.loading.contains(key));
    }

    /**
     * Verifies undecodable tile bytes are attempted exactly three times, then abandoned as missing.
     */
    @Test
    @Timeout(5)
    void loadTile_undecodableBytes_attemptedThreeTimesThenMarkedMissing() throws IOException {

        var provider = new CountingPMTilesProvider();
        var reader = mock(PMTilesReader.class);
        var key = new PmTileKey(4, 5, 5);

        when(reader.getTile(anyLong())).thenReturn(Optional.of(ByteBuffer.wrap(new byte[]{1, 2, 3, 4})));
        provider.reader = reader;

        for (int i = 0; i < 3; i++) {
            provider.loading.add(key);
            provider.loadTile(key);
            awaitTrue(() -> !provider.loading.contains(key));
        }

        assertEquals(3, provider.loadCalls);
        assertNotNull(provider.missingKeys.getIfPresent(key), "Third decode failure should mark the key missing");

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertEquals(3, provider.loadCalls, "Decode-abandoned key must not be loaded again");
    }

    /**
     * Verifies a rejected tile executor submission cannot escape the provider or strand the key in
     * the in-flight set.
     */
    @Test
    void endFrame_executorRejection_clearsLoadingWithoutThrowing() {

        var provider = new RejectingPMTilesProvider();
        provider.reader = mock(PMTilesReader.class);
        var key = new PmTileKey(4, 6, 6);

        provider.beginFrame();
        provider.request(key, 0);

        assertDoesNotThrow(provider::endFrame);
        assertFalse(provider.loading.contains(key), "Rejected submission should be retriable next frame");
    }

    /**
     * Verifies remote PMTiles bootstrap reads exactly the leaf-directory section and then one
     * contiguous coarse tile-data span through the supplied shared range reader.
     */
    @Test
    void bootstrap_clusteredArchive_readsLeafSectionThenCoarseExtent() {

        var provider = new TestPMTilesProvider();
        provider.minZoom = 0;
        provider.maxZoom = 3;
        var rangeReader = new RecordingRangeReader();
        var reader = mockBootstrapReader();

        provider.runRemoteBootstrap(rangeReader, reader, header(true));

        assertEquals(List.of(new RangeRead(300, 50), new RangeRead(1_000, 200)), rangeReader.reads);
    }

    /**
     * Verifies an unclustered archive skips bootstrap reads entirely.
     */
    @Test
    void bootstrap_unclusteredArchive_skipsReads() {

        var provider = new TestPMTilesProvider();
        provider.minZoom = 0;
        provider.maxZoom = 3;
        var rangeReader = new RecordingRangeReader();

        provider.runRemoteBootstrap(rangeReader, mockBootstrapReader(), header(false));

        assertTrue(rangeReader.reads.isEmpty());
    }

    /**
     * Verifies oversized coarse extents abort the data prewarm but still enqueue pump work.
     */
    @Test
    void bootstrap_extentGuard_abortsDataReadButPumpStillRuns() {

        var provider = new BootstrapPMTilesProvider();
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
     * Verifies a 416-like range failure aborts the prewarm without escaping to configure callers.
     */
    @SuppressWarnings("resource")
    @Test
    void bootstrap_rangeNotSatisfiable_isCaught() {

        var provider = new TestPMTilesProvider();
        provider.minZoom = 0;
        provider.maxZoom = 3;
        var rangeReader = new RecordingRangeReader();
        rangeReader.failOnRead = 2;

        assertDoesNotThrow(() -> provider.runRemoteBootstrap(rangeReader, mockBootstrapReader(), header(true)));
    }

    /**
     * Minimal concrete {@link PMTilesProvider} for testing; PMTilesProvider itself is abstract only
     * to force construction through the file/HTTP {@code init(...)} factories.
     */
    private static final class TestPMTilesProvider extends PMTilesProvider {
    }

    private static final class CountingPMTilesProvider extends PMTilesProvider {

        private int loadCalls;

        @Override
        public void loadTile(PmTileKey key) {
            loadCalls++;
            super.loadTile(key);
        }
    }

    private static final class RejectingPMTilesProvider extends PMTilesProvider {

        @Override
        CompletableFuture<NativeImage> submitTileLoad(Supplier<NativeImage> supplier) {
            throw new RejectedExecutionException("full");
        }
    }

    private static final class BootstrapPMTilesProvider extends PMTilesProvider {

        private int loadCalls;

        @Override
        public void loadTile(PmTileKey key) {
            loadCalls++;
            clearLoading(key);
        }
    }

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

    private static PMTilesReader mockBootstrapReader(PMTilesEntry... tileEntries) {

        PMTilesEntry rootLeaf = PMTilesEntry.of(0, 0, 50, 0);
        PMTilesDirectory root = directory(rootLeaf);
        PMTilesDirectory leaf = tileEntries.length == 0
                ? directory(PMTilesEntry.of(0, 0, 100, 1), PMTilesEntry.of(1, 100, 100, 1))
                : directory(tileEntries);

        PMTilesReader reader = mock(PMTilesReader.class);
        when(reader.getRootDirectory()).thenReturn(root);
        when(reader.getDirectory(rootLeaf)).thenReturn(leaf);
        return reader;
    }

    private static PMTilesDirectory directory(PMTilesEntry... entries) {

        PMTilesDirectory directory = mock(PMTilesDirectory.class);
        when(directory.iterator()).thenAnswer(_ -> List.of(entries).iterator());
        return directory;
    }

    private record RangeRead(long offset, int length) {

    }

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
