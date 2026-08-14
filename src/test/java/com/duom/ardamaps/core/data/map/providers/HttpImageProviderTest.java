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

import com.duom.ardamaps.core.data.ImageFileType;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HTTP image colour packing helpers.
 */
class HttpImageProviderTest {

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
     * Verifies PNG signature detection does not depend on the URL extension.
     */
    @Test
    void detectImageFileType_pngMagicBytes_winOverExtension() {

        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};

        assertEquals(ImageFileType.PNG, HttpImageProvider.detectImageFileType(bytes, URI.create("https://example.test/map.jpg")));
    }

    /**
     * Verifies JPEG signature detection for extension-less or query-string URLs.
     */
    @Test
    void detectImageFileType_jpegMagicBytes_doNotDefaultToPng() {

        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0};

        assertEquals(ImageFileType.JPEG, HttpImageProvider.detectImageFileType(bytes, URI.create("https://example.test/icon?id=1")));
    }

    /**
     * Verifies WebP RIFF container detection.
     */
    @Test
    void detectImageFileType_webpMagicBytes() {

        byte[] bytes = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertEquals(ImageFileType.WEBP, HttpImageProvider.detectImageFileType(bytes, URI.create("https://example.test/layer.png")));
    }

    /**
     * Verifies inconclusive bytes still use the existing extension fallback.
     */
    @Test
    void detectImageFileType_inconclusiveBytes_useExtensionFallback() {

        byte[] bytes = new byte[]{0, 1, 2};

        assertEquals(ImageFileType.JPEG, HttpImageProvider.detectImageFileType(bytes, URI.create("https://example.test/layer.jpeg")));
    }

    /**
     * Verifies a saturated image executor does not strand the URL in the loading set. The second
     * call must attempt submission again, which can only happen if the first rejection cleared it.
     */
    @Test
    void loadImage_executorRejection_clearsLoadingWithoutThrowing() {

        var client = new FakeHttpClient();
        client.enqueue(200, new byte[]{1}, Map.of("cache-control", "max-age=300"));
        client.enqueue(200, new byte[]{1}, Map.of("cache-control", "max-age=300"));
        var provider = new RejectingHttpImageProvider(tempDir, client);
        String url = "https://example.test/tile.png";

        assertDoesNotThrow(() -> provider.loadImage(url));
        assertDoesNotThrow(() -> provider.loadImage(url));

        assertEquals(2, provider.submitAttempts, "Rejected URL should be retriable on the next request");
    }

    /**
     * Verifies cache-control max-age parsing, defaults, and defensive clamping.
     */
    @Test
    void cacheControl_maxAgeParsingAndClamp() {

        assertEquals(86_400L, FetchResult.parseMaxAge("public, max-age=86400", 14_400L));
        assertEquals(14_400L, FetchResult.parseMaxAge("garbage", 14_400L));
        assertEquals(300L, FetchResult.parseMaxAge("max-age=0", 86_400L));
        assertEquals(604_800L, FetchResult.parseMaxAge("max-age=99999999", 86_400L));
    }

    /**
     * Verifies widened disk keys avoid collisions over a large synthetic tile URL set.
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
     * Verifies a fresh disk entry is served without a second network request.
     */
    @Test
    void loadImage_freshDiskEntry_skipsNetwork() throws Exception {

        byte[] bytes = new byte[]{1, 2, 3};
        var client = new FakeHttpClient();
        client.enqueue(200, bytes, Map.of("cache-control", "max-age=86400", "last-modified", "Fri, 14 Aug 2026 10:00:00 GMT"));
        var provider = new CapturingHttpImageProvider(tempDir, client);
        provider.setClock(() -> 1_000L);
        String url = "https://example.test/tile.png";

        awaitLoad(provider, url);
        provider.setClock(() -> 2_000L);
        awaitLoad(provider, url);

        assertEquals(1, client.requests.get(), "Fresh disk entry should not hit the network");
        assertArrayEquals(bytes, provider.decodedBytes.getFirst());
        assertArrayEquals(bytes, provider.decodedBytes.getLast());
    }

    /**
     * Verifies expired disk entries are refetched and rewritten.
     */
    @Test
    void loadImage_expiredDiskEntry_refetches() throws Exception {

        var client = new FakeHttpClient();
        client.enqueue(200, new byte[]{1}, Map.of("cache-control", "max-age=300"));
        client.enqueue(200, new byte[]{2}, Map.of("cache-control", "max-age=300"));
        var provider = new CapturingHttpImageProvider(tempDir, client);
        String url = "https://example.test/tile.png";

        provider.setClock(() -> 1_000L);
        awaitLoad(provider, url);
        provider.setClock(() -> 1_000L + 301_000L);
        awaitLoad(provider, url);

        assertEquals(2, client.requests.get());
        assertArrayEquals(new byte[]{2}, provider.decodedBytes.getLast());
    }

    /**
     * Verifies a 204 response takes the absent path and persists the negative cache for later calls.
     */
    @SuppressWarnings("resource")
    @Test
    void loadImage_absentResponse_usesNegativeCacheTtl() throws Exception {

        var client = new FakeHttpClient();
        client.enqueue(204, new byte[0], Map.of("cache-control", "max-age=14400"));
        var provider = new CapturingHttpImageProvider(tempDir, client);
        String url = "https://example.test/missing.png";

        long firstTtl = awaitAbsent(provider, url);
        long secondTtl = awaitAbsent(provider, url);

        assertEquals(14_400L, firstTtl);
        assertEquals(14_400L, secondTtl);
        assertEquals(1, client.requests.get(), "Fresh negative cache entry should suppress network");
        assertTrue(Files.list(tempDir).anyMatch(path -> path.getFileName().toString().endsWith(".1")),
                "Absent metadata should be persisted");
    }

    /**
     * Verifies an expired cached entry sends If-Modified-Since and a 304 refreshes metadata while
     * serving the cached bytes.
     */
    @Test
    void loadImage_expiredEntryWith304_usesCachedBytes() throws Exception {

        var client = new FakeHttpClient();
        client.enqueue(200, new byte[]{9}, Map.of(
                "cache-control", "max-age=300",
                "last-modified", "Fri, 14 Aug 2026 10:00:00 GMT"));
        client.enqueue(304, new byte[0], Map.of("cache-control", "max-age=300"));
        var provider = new CapturingHttpImageProvider(tempDir, client);
        String url = "https://example.test/tile.png";

        provider.setClock(() -> 1_000L);
        awaitLoad(provider, url);
        provider.setClock(() -> 1_000L + 301_000L);
        awaitLoad(provider, url);

        assertEquals(2, client.requests.get());
        assertEquals("Fri, 14 Aug 2026 10:00:00 GMT",
                client.lastRequest.headers().firstValue("If-Modified-Since").orElse(null));
        assertArrayEquals(new byte[]{9}, provider.decodedBytes.getLast());
    }

    private static final class RejectingHttpImageProvider extends HttpImageProvider {

        private int submitAttempts;

        private RejectingHttpImageProvider(Path diskCacheDirectory, FakeHttpClient client) {

            super(diskCacheDirectory, new DelegatingHttpClient(client));
        }

        @Override
        CompletableFuture<NativeImage> submitImageLoad(Supplier<NativeImage> supplier) {
            submitAttempts++;
            throw new RejectedExecutionException("full");
        }
    }

    private static void awaitLoad(CapturingHttpImageProvider provider, String url) throws Exception {

        CountDownLatch latch = new CountDownLatch(1);
        provider.loadImage(url, _ -> latch.countDown(), () -> fail("Unexpected IO failure"));
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Image load did not complete");
    }

    private static long awaitAbsent(CapturingHttpImageProvider provider, String url) throws Exception {

        CountDownLatch latch = new CountDownLatch(1);
        long[] ttl = new long[1];
        provider.loadImage(url, _ -> latch.countDown(), () -> fail("Unexpected IO failure"), maxAge -> {
            ttl[0] = maxAge;
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Absent load did not complete");
        return ttl[0];
    }

    private static final class CapturingHttpImageProvider extends HttpImageProvider {

        private final ArrayDeque<byte[]> decodedBytes = new ArrayDeque<>();

        private CapturingHttpImageProvider(Path diskCacheDirectory, FakeHttpClient client) {

            super(diskCacheDirectory, new DelegatingHttpClient(client));
        }

        @Override
        CompletableFuture<NativeImage> submitImageLoad(Supplier<NativeImage> supplier) {

            try {
                supplier.get();
            } catch (RuntimeException ignored) {
                // Tests use arbitrary bytes and only need to observe cache transport behaviour.
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        NativeImage decodeImage(byte[] rawImageData, URI uri) {

            decodedBytes.add(rawImageData);
            return null;
        }
    }

    private static final class FakeHttpClient extends HttpClient {

        private final ArrayDeque<HttpResponse<byte[]>> responses = new ArrayDeque<>();
        private final AtomicInteger requests = new AtomicInteger();
        private HttpRequest lastRequest;

        private void enqueue(int status, byte[] body, Map<String, String> headers) {

            responses.add(new FakeResponse(status, body, headers));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {

            requests.incrementAndGet();
            lastRequest = request;
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) responses.removeFirst();
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }

        @Override
        public Optional<Duration> connectTimeout() { return Optional.empty(); }

        @Override
        public Redirect followRedirects() { return Redirect.NEVER; }

        @Override
        public Optional<ProxySelector> proxy() { return Optional.empty(); }

        @Override
        public SSLContext sslContext() { return null; }

        @Override
        public SSLParameters sslParameters() { return null; }

        @Override
        public Optional<Authenticator> authenticator() { return Optional.empty(); }

        @Override
        public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }

        @Override
        public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

    }

    private record FakeResponse(int statusCode, byte[] body, Map<String, String> headerMap) implements HttpResponse<byte[]> {

        @Override
        public HttpRequest request() { return null; }

        @Override
        public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }

        @Override
        public HttpHeaders headers() {
            Map<String, java.util.List<String>> values = new HashMap<>();
            headerMap.forEach((key, value) -> values.put(key, java.util.List.of(value)));
            return HttpHeaders.of(values, (_, _) -> true);
        }

        @Override
        public byte[] body() { return body; }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }

        @Override
        public URI uri() { return URI.create("https://example.test"); }

        @Override
        public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    }
}
