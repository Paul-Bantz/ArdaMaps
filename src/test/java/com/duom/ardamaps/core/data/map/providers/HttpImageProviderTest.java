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
import net.minecraft.client.texture.NativeImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HTTP image color packing helpers.
 */
class HttpImageProviderTest {

    /** Temporary directory used for disk-cache assertions. */
    @TempDir
    private Path tempDir;

    /**
     * Verifies that Scrimage ARGB pixels are converted to the ABGR packing expected by NativeImage.
     */
    @Test
    void argbToAbgr_swapsRedAndBlueChannels() {

        assertEquals(0xFF332211, HttpImageProvider.argbToAbgr(0xFF112233));
    }

    /**
     * Verify that executor rejection still completes the load path without throwing.
     */
    @Test
    void loadImage_executorRejectionInvokesCompletionCallback() throws Exception {

        assertInstanceOf(ThreadPoolExecutor.class, ArdaMapsClient.IMAGE_EXECUTOR);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) ArdaMapsClient.IMAGE_EXECUTOR;
        var provider = new HttpImageProvider(tempDir, (uri, lastModified) ->
                CompletableFuture.completedFuture(FetchResult.fromConnection(200, new byte[]{1}, Map.of())));
        var blockers = saturate(executor);
        var ioFailure = new AtomicBoolean(false);

        try {
            assertDoesNotThrow(() -> provider.loadImage(
                    "https://example.com/tile.png",
                    image -> fail("Rejected decode should retry on a later request"),
                    () -> ioFailure.set(true)
            ));

            assertFalse(ioFailure.get());
        } finally {
            blockers.releaseAll();
            provider.close();
        }
    }

    /**
     * Verify Cache-Control max-age parsing and clamping.
     */
    @Test
    void cacheControl_maxAgeParsingAndClamp() {

        assertEquals(86_400L, FetchResult.parseMaxAge("public, max-age=86400", 14_400L));
        assertEquals(14_400L, FetchResult.parseMaxAge("garbage", 14_400L));
        assertEquals(300L, FetchResult.parseMaxAge("max-age=0", 86_400L));
        assertEquals(604_800L, FetchResult.parseMaxAge("max-age=99999999", 86_400L));
    }

    /**
     * Verify that disk-cache keys remain collision-resistant across a large tile set.
     */
    @Test
    void diskCacheKey_noCollisionsAcrossLargeTileSet() {

        Set<String> keys = new HashSet<>();

        for (int i = 0; i < 100_000; i++) {
            URI uri = URI.create("https://example.test/maps/arda/tiles/%d/x%d/z%d.png".formatted(i % 9, i, i / 9));
            keys.add(HttpImageProvider.getDiskCacheKey(uri));
        }

        assertEquals(100_000, keys.size());
    }

    /**
     * Verify that a fresh disk entry is reused without a second network fetch.
     */
    @Test
    void loadImage_freshDiskEntry_skipsNetwork() throws Exception {

        byte[] bytes = new byte[]{1, 2, 3};
        var fetcher = new FakeFetcher();
        fetcher.enqueue(200, bytes, Map.of("cache-control", "max-age=86400", "last-modified", "Fri, 14 Aug 2026 10:00:00 GMT"));
        var provider = new CapturingHttpImageProvider(tempDir, fetcher);
        provider.setClock(() -> 1_000L);
        String url = "https://example.test/tile.png";

        awaitLoad(provider, url);
        provider.setClock(() -> 2_000L);
        awaitLoad(provider, url);

        assertEquals(1, fetcher.requests.get());
        assertArrayEquals(bytes, provider.decodedBytes.getFirst());
        assertArrayEquals(bytes, provider.decodedBytes.getLast());
    }

    /**
     * Verify that an expired disk entry triggers a refetch.
     */
    @Test
    void loadImage_expiredDiskEntry_refetches() throws Exception {

        var fetcher = new FakeFetcher();
        fetcher.enqueue(200, new byte[]{1}, Map.of("cache-control", "max-age=300"));
        fetcher.enqueue(200, new byte[]{2}, Map.of("cache-control", "max-age=300"));
        var provider = new CapturingHttpImageProvider(tempDir, fetcher);
        String url = "https://example.test/tile.png";

        provider.setClock(() -> 1_000L);
        awaitLoad(provider, url);
        provider.setClock(() -> 1_000L + 301_000L);
        awaitLoad(provider, url);

        assertEquals(2, fetcher.requests.get());
        assertArrayEquals(new byte[]{2}, provider.decodedBytes.getLast());
    }

    /**
     * Verify that absent responses are cached using the negative-cache TTL.
     */
    @SuppressWarnings("resource")
    @Test
    void loadImage_absentResponse_usesNegativeCacheTtl() throws Exception {

        var fetcher = new FakeFetcher();
        fetcher.enqueue(204, new byte[0], Map.of("cache-control", "max-age=14400"));
        var provider = new CapturingHttpImageProvider(tempDir, fetcher);
        String url = "https://example.test/missing.png";

        long firstTtl = awaitAbsent(provider, url);
        long secondTtl = awaitAbsent(provider, url);

        assertEquals(14_400L, firstTtl);
        assertEquals(14_400L, secondTtl);
        assertEquals(1, fetcher.requests.get());
        assertTrue(Files.list(tempDir).anyMatch(path -> path.getFileName().toString().endsWith(".1")));
    }

    /**
     * Verify that empty response bodies are persisted as absent results.
     */
    @Test
    void loadImage_emptyBodyResponse_persistsAsAbsent() throws Exception {

        var fetcher = new FakeFetcher();
        fetcher.enqueue(200, new byte[0], Map.of("cache-control", "max-age=14400"));
        var provider = new CapturingHttpImageProvider(tempDir, fetcher);
        String url = "https://example.test/empty.png";

        assertEquals(14_400L, awaitAbsent(provider, url));

        var secondProvider = new CapturingHttpImageProvider(tempDir, fetcher);
        assertEquals(14_400L, awaitAbsent(secondProvider, url));

        assertEquals(1, fetcher.requests.get());
        assertTrue(secondProvider.decodedBytes.isEmpty());
    }

    /**
     * Verify that a 304 response reuses the cached bytes.
     */
    @Test
    void loadImage_expiredEntryWith304_usesCachedBytes() throws Exception {

        var fetcher = new FakeFetcher();
        fetcher.enqueue(200, new byte[]{9}, Map.of(
                "cache-control", "max-age=300",
                "last-modified", "Fri, 14 Aug 2026 10:00:00 GMT"));
        fetcher.enqueue(304, new byte[0], Map.of("cache-control", "max-age=300"));
        var provider = new CapturingHttpImageProvider(tempDir, fetcher);
        String url = "https://example.test/tile.png";

        provider.setClock(() -> 1_000L);
        awaitLoad(provider, url);
        provider.setClock(() -> 1_000L + 301_000L);
        awaitLoad(provider, url);

        assertEquals(2, fetcher.requests.get());
        assertEquals("Fri, 14 Aug 2026 10:00:00 GMT", fetcher.lastModifiedHeaders.get(fetcher.lastModifiedHeaders.size() - 1));
        assertArrayEquals(new byte[]{9}, provider.decodedBytes.getLast());
    }

    /**
     * Wait for a successful image load to complete.
     *
     * @param provider Provider under test.
     * @param url Image URL.
     * @throws Exception If the latch times out.
     */
    private static void awaitLoad(CapturingHttpImageProvider provider, String url) throws Exception {

        CountDownLatch latch = new CountDownLatch(1);
        provider.loadImage(url, ignored -> latch.countDown(), () -> fail("Unexpected IO failure"));
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Image load did not complete");
    }

    /**
     * Wait for an absent image load to complete and return the reported TTL.
     *
     * @param provider Provider under test.
     * @param url Image URL.
     * @return Reported negative-cache TTL.
     * @throws Exception If the latch times out.
     */
    private static long awaitAbsent(CapturingHttpImageProvider provider, String url) throws Exception {

        CountDownLatch latch = new CountDownLatch(1);
        long[] ttl = new long[1];
        provider.loadImage(url, ignored -> latch.countDown(), () -> fail("Unexpected IO failure"), maxAge -> {
            ttl[0] = maxAge;
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Absent load did not complete");
        return ttl[0];
    }

    /**
     * Saturate the given executor so rejected submissions can be exercised.
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
     * Test provider that records decoded bytes instead of creating textures.
     */
    private static final class CapturingHttpImageProvider extends HttpImageProvider {

        /** Bytes passed to the decoder in call order. */
        private final ArrayDeque<byte[]> decodedBytes = new ArrayDeque<>();

        /**
         * Create a capturing provider for tests.
         *
         * @param diskCacheDirectory Disk cache directory.
         * @param fetcher Response source.
         */
        private CapturingHttpImageProvider(Path diskCacheDirectory, FakeFetcher fetcher) {

            super(diskCacheDirectory, fetcher);
        }

        /**
         * Record the raw bytes and skip actual decoding.
         *
         * @param rawImageData Encoded image bytes.
         * @param uri Source URI.
         * @return Always null to avoid creating textures in the test.
         */
        @Override
        NativeImage decodeImage(byte[] rawImageData, URI uri) {

            decodedBytes.add(rawImageData);
            return null;
        }
    }

    /**
     * Deterministic fetcher used to feed scripted responses into the provider.
     */
    private static final class FakeFetcher implements HttpImageProvider.Fetcher {

        /** Scripted responses returned in order. */
        private final ArrayDeque<FetchResult> responses = new ArrayDeque<>();
        /** Count of fetch invocations. */
        private final AtomicInteger requests = new AtomicInteger();
        /** Last-Modified values passed to each request. */
        private final List<String> lastModifiedHeaders = new ArrayList<>();

        /**
         * Queue a synthetic fetch response.
         *
         * @param status HTTP status code.
         * @param body Response body bytes.
         * @param headers Response headers.
         */
        private void enqueue(int status, byte[] body, Map<String, String> headers) {

            Map<String, List<String>> values = new HashMap<>();
            headers.forEach((key, value) -> values.put(key, List.of(value)));
            responses.add(FetchResult.fromConnection(status, body, values));
        }

        /**
         * Return the next scripted response.
         *
         * @param uri Requested URI.
         * @param lastModified Last-Modified value from the caller.
         * @return Completed future for the next scripted response.
         */
        @Override
        public CompletableFuture<FetchResult> fetch(URI uri, String lastModified) {

            requests.incrementAndGet();
            lastModifiedHeaders.add(lastModified);
            return CompletableFuture.completedFuture(responses.removeFirst());
        }
    }
}
