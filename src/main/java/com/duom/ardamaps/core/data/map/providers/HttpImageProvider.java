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
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.ImageFileType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.google.gson.Gson;
import com.jakewharton.disklrucache.DiskLruCache;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.pixels.Pixel;
import com.sksamuel.scrimage.webp.WebpImageReader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Provider for loading images over HTTP.
 * <p>
 * This class implements a two-tier cache: registered textures in memory, and a two-slot disk cache
 * where slot 0 stores bytes and slot 1 stores freshness metadata.
 * </p>
 */
public class HttpImageProvider {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpImageProvider.class);

    /** Maximum disk cache size: 512 Mb */
    private static final long DISK_CACHE_MAX_SIZE = 512L << 20;

    /** Disk cache app version - bumped for SHA-256 keys and two-slot metadata. */
    private static final int DISK_CACHE_APP_VERSION = 2;

    /** Number of values per disk entry: slot 0 is raw bytes, slot 1 is JSON metadata. */
    private static final int DISK_CACHE_VALUE_COUNT = 2;

    /** Disk entries older than this are removed by periodic maintenance. */
    private static final long STALE_ENTRY_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private static final Gson GSON = new Gson();

    /** Set of URLs currently being loaded (thread-safe) */
    private final Set<String> loading = ConcurrentHashMap.newKeySet();

    /** Removal listener that destroys dynamic textures evicted from the in-memory cache. */
    private final RemovalListener<String, TextureData> textureRemovalListener =
            (ignoredUrl, data, ignoredCause) -> destroyTexture(data);

    /** Caffeine in-memory cache for registered textures, weighted by decoded RGBA bytes. */
    private final Cache<String, TextureData> textures = Caffeine.newBuilder()
            .maximumWeight(TileProvider.textureCacheBudgetBytes())
            .weigher((String ignoredUrl, TextureData data) -> data.byteWeight())
            .removalListener(textureRemovalListener)
            .build();

    /** Single-threaded daemon scheduler for periodic cache-maintenance tasks */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ardamaps-cache-scheduler");
        t.setDaemon(true);
        return t;
    });

    /** Directory used for the on-disk HTTP image cache. */
    private final Path diskCacheDirectory;
    /** Strategy used to fetch image bytes and headers. */
    private final Fetcher fetcher;

    /** Lazily-initialised DiskLruCache */
    private volatile DiskLruCache diskCache;

    /** Time source for cache freshness; overridable in tests. */
    private LongSupplier clock = System::currentTimeMillis;

    public HttpImageProvider() {

        this(Client.cacheDirectory().resolve("http-images"), HttpImageProvider::fetchFromUrl);
    }

    /**
     * Create a provider with an explicit disk cache directory and fetch strategy.
     *
     * @param diskCacheDirectory Directory used for the on-disk cache.
     * @param fetcher Fetch strategy for HTTP requests.
     */
    HttpImageProvider(Path diskCacheDirectory, Fetcher fetcher) {

        this.diskCacheDirectory = diskCacheDirectory;
        this.fetcher = fetcher;
        scheduler.scheduleAtFixedRate(this::evictStaleEntries, 60, 60, TimeUnit.MINUTES);
    }

    /**
     * Evicts disk-cached entries whose persisted fetch timestamp is older than
     * {@value STALE_ENTRY_TTL_MS} ms. Metadata lives on disk, so this reaches previous sessions.
     */
    private void evictStaleEntries() {

        DiskLruCache cache = getDiskCache();
        if (cache == null) return;

        long cutoff = clock.getAsLong() - STALE_ENTRY_TTL_MS;
        int count = 0;

        try (var files = Files.list(diskCacheDirectory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".1")).toList()) {
                String filename = file.getFileName().toString();
                String key = filename.substring(0, filename.length() - 2);
                CacheMetadata metadata = readMetadata(file);
                if (metadata != null && metadata.fetchedAt() >= cutoff) continue;
                if (cache.remove(key)) count++;
            }
        } catch (IOException e) {
            LOGGER.warn("[ArdaMaps] Failed to evict stale HTTP image disk entries", e);
        }

        if (count > 0)
            LOGGER.info("[ArdaMaps] Stale-entry eviction complete ; removed {} entries.", count);
    }

    /**
     * Returns the DiskLruCache, initializing it on first call.
     * Returns {@code null} if the cache could not be opened.
     */
    private @Nullable DiskLruCache getDiskCache() {
        if (diskCache == null) {
            synchronized (this) {
                if (diskCache == null) {
                    try {
                        diskCache = DiskLruCache.open(
                                diskCacheDirectory.toFile(),
                                DISK_CACHE_APP_VERSION,
                                DISK_CACHE_VALUE_COUNT,
                                DISK_CACHE_MAX_SIZE
                        );
                    } catch (IOException e) {
                        LOGGER.error("Failed to open DiskLruCache", e);
                    }
                }
            }
        }
        return diskCache;
    }

    /**
     * Closes the underlying DiskLruCache and the maintenance scheduler.
     * Call during mod shutdown.
     */
    public void close() {

        textures.invalidateAll();
        textures.cleanUp();
        loading.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS))
                scheduler.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (diskCache != null) {
            try {
                diskCache.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close DiskLruCache", e);
            }
        }
    }

    /**
     * Destroys a registered dynamic texture on the client thread.
     *
     * @param data Cached texture data.
     */
    private void destroyTexture(TextureData data) {

        if (data == null || data.image() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> MinecraftClient.getInstance().getTextureManager().destroyTexture(data.image()));
    }

    /**
     * Gets the texture identifier for the specified URL.
     * Initiates loading if the texture is not yet cached.
     *
     * @param path The URL of the texture
     * @return The texture identifier, or null if not yet loaded
     */
    public Identifier getTexture(String path) {

        if (path == null || path.isEmpty()) return null;

        TextureData data = textures.getIfPresent(path);

        if (data == null) {

            loadImage(path);
            return null;
        }

        return data.image();
    }

    /**
     * Asynchronously loads an image from the specified URL using the internal
     * Caffeine and DiskLruCache pipeline.
     *
     * @param url The URL of the image to load
     */
    public void loadImage(String url) {

        if (!loading.add(url)) return; // already in-flight

        loadImage(url, loadedTexture -> {
            if (loadedTexture != null)
                MinecraftClient.getInstance().execute(() -> registerTexture(loadedTexture));
            else
                loading.remove(url);
        }, () -> loading.remove(url), ignored -> loading.remove(url));
    }

    /**
     * Asynchronously loads an image and delivers the result to {@code onComplete}.
     * The optional {@code onIoFailure} callback is invoked only for transport/IO failures.
     *
     * @param url         The URL of the image to load
     * @param onComplete  Callback that receives the loaded image and URL, or null if loading failed
     * @param onIoFailure Callback for IO/network failures only
     */
    public void loadImage(String url, Consumer<Pair<NativeImage, String>> onComplete, Runnable onIoFailure) {

        loadImage(url, onComplete, onIoFailure, null);
    }

    /**
     * Asynchronously loads an image, distinguishing confirmed absence from transport failure.
     *
     * @param url         The URL of the image to load.
     * @param onComplete  Callback that receives the loaded image and URL, or null if loading failed.
     * @param onIoFailure Callback for IO/network failures only.
     * @param onAbsent    Callback for 204/404/empty-body absent responses, with max-age seconds.
     */
    public void loadImage(String url,
                          Consumer<Pair<NativeImage, String>> onComplete,
                          Runnable onIoFailure,
                          LongConsumer onAbsent) {

        try {
            URI uri = URI.create(url);
            loadBytesAsync(uri)
                    .thenCompose(result -> {
                        if (result.absent()) {
                            if (onAbsent != null) onAbsent.accept(result.absentTtlSeconds());
                            return CompletableFuture.completedFuture(null);
                        }
                        if (result.bytes() == null) return CompletableFuture.completedFuture(null);

                        return CompletableFuture.supplyAsync(
                                () -> decodeImage(result.bytes(), result.uri()),
                                ArdaMapsClient.IMAGE_EXECUTOR);
                    })
                    .whenComplete((image, ex) -> {
                if (ex != null) {
                    if (ex instanceof CompletionException completion
                            && completion.getCause() instanceof RejectedExecutionException) {
                        LOGGER.warn("Image executor rejected image @\"{}\", retrying next request", url);
                    } else {
                        LOGGER.error("Unexpected async failure loading image @\"{}\"", url, ex);
                        if (onIoFailure != null) onIoFailure.run();
                    }
                    loading.remove(url);
                    return;
                }

                try {
                    onComplete.accept(image == null ? null : new Pair<>(image, url));
                } catch (RuntimeException e) {
                    LOGGER.error("Image completion callback failed for @\"{}\"", url, e);
                    loading.remove(url);
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Image executor rejected image @\"{}\"", url, e);
            loading.remove(url);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error scheduling image @\"{}\"", url, e);
            loading.remove(url);
        }
    }

    /**
     * Decode the supplied image bytes into a native Minecraft image.
     *
     * @param rawImageData Encoded image bytes.
     * @param uri Source URI, used for format selection and diagnostics.
     * @return Decoded image, or null if decoding failed.
     */
    NativeImage decodeImage(byte[] rawImageData, URI uri) {

        try {
            var fileExt = getFileExtension(uri);
            return switch (fileExt) {
                case WEBP -> loadWebpImage(rawImageData);
                case PNG, JPG, JPEG -> NativeImage.read(new ByteArrayInputStream(rawImageData));
            };
        } catch (IOException e) {
            LOGGER.warn("Failed to decode image @\"{}\"", uri, e);
            removeDiskCacheEntry(uri);
            return null;
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error decoding image @\"{}\"", uri, e);
            removeDiskCacheEntry(uri);
            return null;
        }
    }

    /**
     * Registers the given image as a texture in Minecraft and updates the cache.
     *
     * @param imageKey A pair containing the NativeImage and its associated URL
     */
    private void registerTexture(Pair<NativeImage, String> imageKey) {

        var url = imageKey.getRight();
        var idName = getDiskCacheKey(URI.create(url));
        var imageData = imageKey.getLeft();

        NativeImageBackedTexture nativeImage = new NativeImageBackedTexture(imageData);
        Identifier texture = MinecraftClient.getInstance()
                .getTextureManager()
                .registerDynamicTexture(idName, nativeImage);

        textures.put(url, new TextureData(texture, imageData.getWidth(), imageData.getHeight()));
        loading.remove(url);
    }

    /**
     * Loads raw image bytes for {@code uri}, consulting the DiskLruCache first and falling back to
     * an HTTP request when the entry is missing or stale.
     *
     * @param uri The URI of the image to load
     * @return The load result
     */
    private CompletableFuture<LoadResult> loadBytesAsync(URI uri) {

        DiskLruCache cache = getDiskCache();
        String key = getDiskCacheKey(uri);
        long now = clock.getAsLong();

        DiskEntry diskEntry = readDiskEntry(cache, key);
        if (diskEntry != null && diskEntry.isFresh(now))
            return CompletableFuture.completedFuture(LoadResult.fromDisk(uri, diskEntry));

        return fetcher.fetch(uri, diskEntry == null ? null : diskEntry.metadata().lastModified())
                .thenApply(fetch -> {
                    try {
                        return handleNetworkResponse(uri, key, cache, diskEntry, fetch);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    private LoadResult handleNetworkResponse(URI uri,
                                             String key,
                                             @Nullable DiskLruCache cache,
                                             @Nullable DiskEntry previous,
                                             FetchResult fetch) throws IOException {

        if (fetch.isNotModified()) {
            if (previous == null) {
                LOGGER.warn("Received 304 for uncached image @\"{}\"", uri);
                return LoadResult.empty(uri);
            }
            CacheMetadata metadata = previous.metadata().refreshed(clock.getAsLong(), fetch);
            writeDiskEntry(cache, key, previous.bytes(), metadata);
            return LoadResult.fromBytes(uri, previous.bytes());
        }

        if (fetch.status() < 200 || fetch.status() >= 300)
            throw new IOException("Unexpected HTTP status " + fetch.status() + " for " + uri);

        CacheMetadata metadata = CacheMetadata.fromFetch(fetch, clock.getAsLong());
        if (fetch.isAbsent()) {
            writeDiskEntry(cache, key, new byte[]{0}, metadata);
            return LoadResult.absent(uri, fetch.maxAgeSeconds());
        }

        writeDiskEntry(cache, key, fetch.bytes(), metadata);
        return LoadResult.fromBytes(uri, fetch.bytes());
    }

    /**
     * Read one cached disk entry if it exists.
     *
     * @param cache Open disk cache.
     * @param key Disk-cache key.
     * @return Cached entry, or null if absent or unreadable.
     */
    private @Nullable DiskEntry readDiskEntry(@Nullable DiskLruCache cache, String key) {

        if (cache == null) return null;

        try {
            DiskLruCache.Snapshot snapshot = cache.get(key);
            if (snapshot == null) return null;
            try (snapshot) {
                CacheMetadata metadata = readMetadata(snapshot);
                if (metadata == null) return null;
                byte[] bytes = metadata.isAbsent() ? new byte[0] : snapshot.getInputStream(0).readAllBytes();
                return new DiskEntry(bytes, metadata);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Failed to read disk cache entry {}", key, e);
            return null;
        }
    }

    /**
     * Read persisted cache metadata from a disk-cache snapshot.
     *
     * @param snapshot Open disk-cache snapshot.
     * @return Parsed metadata, or null when unreadable.
     */
    private @Nullable CacheMetadata readMetadata(DiskLruCache.Snapshot snapshot) {

        try {
            String json = new String(snapshot.getInputStream(1).readAllBytes(), StandardCharsets.UTF_8);
            return GSON.fromJson(json, CacheMetadata.class);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Failed to read disk cache metadata", e);
            return null;
        }
    }

    /**
     * Read persisted cache metadata from a metadata file.
     *
     * @param path Metadata file path.
     * @return Parsed metadata, or null when unreadable.
     */
    private @Nullable CacheMetadata readMetadata(Path path) {

        try {
            return GSON.fromJson(Files.readString(path), CacheMetadata.class);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Failed to read disk cache metadata {}", path, e);
            return null;
        }
    }

    /**
     * Write one disk-cache entry and its metadata payload.
     *
     * @param cache Open disk cache.
     * @param key Disk-cache key.
     * @param bytes Raw image bytes, or null for absent entries.
     * @param metadata Serialized freshness metadata.
     */
    private void writeDiskEntry(@Nullable DiskLruCache cache, String key, byte @Nullable [] bytes, CacheMetadata metadata) {

        if (cache == null) return;
        if (bytes != null && bytes.length == 0) return;

        DiskLruCache.Editor editor = null;
        try {
            editor = cache.edit(key);
            if (editor == null) return;

            try (OutputStream out = editor.newOutputStream(0)) {
                if (bytes != null) out.write(bytes);
            }
            try (OutputStream out = editor.newOutputStream(1)) {
                out.write(GSON.toJson(metadata).getBytes(StandardCharsets.UTF_8));
            }
            editor.commit();
        } catch (IOException e) {
            if (editor != null) {
                try {
                    editor.abort();
                } catch (IOException ignored) {
                    // Preserve the original failure.
                }
            }
            LOGGER.warn("Failed to write disk cache entry {}", key, e);
        }
    }

    /**
     * Extracts the file extension from the URI path and maps it to an ImageFileType.
     * Defaults to PNG if the extension is missing or unrecognized.
     *
     * @param uri The URI to extract the file extension from
     * @return The corresponding ImageFileType, or PNG if unrecognized
     */
    public static @NotNull ImageFileType getFileExtension(URI uri) {

        ImageFileType fileExtension = ImageFileType.PNG;
        try {
            String decodedPath = URLDecoder.decode(uri.getPath(), StandardCharsets.UTF_8);
            int lastDot = decodedPath.lastIndexOf('.');
            if (lastDot != -1)
                fileExtension = ImageFileType.fromString(decodedPath.substring(lastDot + 1).toLowerCase());
        } catch (Exception e) {
            LOGGER.error("Failed to get file extension for URI: {}", uri, e);
        }
        return fileExtension;
    }

    /**
     * Loads a WebP image from raw bytes using Scrimage and converts it to a NativeImage.
     *
     * @param imageData The raw bytes of the WebP image
     * @return A NativeImage containing the decoded image data
     * @throws IOException if the image data cannot be decoded
     */
    private @NotNull NativeImage loadWebpImage(byte[] imageData) throws IOException {

        WebpImageReader reader = new WebpImageReader();
        ImmutableImage image = reader.read(imageData);
        return webpToNativeImage(image);
    }

    /**
     * Returns a DiskLruCache-safe key for {@code uri}: a short filename hint plus 128 bits of
     * SHA-256 over the full URL.
     *
     * @param uri The URI to generate a cache key for
     * @return A sanitized cache key derived from the URI
     */
    static @NotNull String getDiskCacheKey(URI uri) {

        String path = URLDecoder.decode(uri.getPath(), StandardCharsets.UTF_8);
        String filename = path.substring(path.lastIndexOf('/') + 1);
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex != -1) filename = filename.substring(0, dotIndex);

        String sanitized = filename.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (sanitized.isEmpty()) sanitized = "url";
        if (sanitized.length() > 24) sanitized = sanitized.substring(0, 24);

        return sanitized + "-" + sha256Prefix(uri.toString());
    }

    /**
     * Compute the leading 128 bits of a SHA-256 hash as hex.
     *
     * @param value Input string to hash.
     * @return Hex-encoded SHA-256 prefix.
     */
    private static String sha256Prefix(String value) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Fetch a URI with the shared HTTP client.
     *
     * @param uri URI to request.
     * @param lastModified Optional If-Modified-Since value.
     * @return Future that resolves to the adapted fetch result.
     */
    private static CompletableFuture<FetchResult> fetchFromUrl(URI uri, @Nullable String lastModified) {

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", "Minecraft-Fabric");
        if (lastModified != null) builder.header("If-Modified-Since", lastModified);

        return ArdaMapsHttpClient.CLIENT
                .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(FetchResult::fromResponse);
    }

    /**
     * Converts a Scrimage ImmutableImage (used for WebP decoding) to a Minecraft NativeImage.
     *
     * @param img The ImmutableImage to convert
     * @return A NativeImage containing the same pixel data as the input image
     */
    private NativeImage webpToNativeImage(ImmutableImage img) {

        int w = img.width;
        int h = img.height;
        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        Pixel[] pixels = img.pixels();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                nativeImage.setColor(x, y, argbToAbgr(pixels[y * w + x].argb));
        return nativeImage;
    }

    /**
     * Remove a cached disk entry after a decode failure.
     *
     * @param uri Source URI for the failed image.
     */
    private void removeDiskCacheEntry(URI uri) {

        try {
            DiskLruCache cache = getDiskCache();
            if (cache != null) cache.remove(getDiskCacheKey(uri));
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Failed to remove undecodable image cache entry @\"{}\"", uri, e);
        }
    }

    /**
     * Convert Scrimage ARGB pixels to Minecraft's ABGR packing.
     *
     * @param argb ARGB pixel value.
     * @return ABGR pixel value.
     */
    static int argbToAbgr(int argb) {

        // Scrimage uses ARGB, while NativeImage.Format.RGBA expects ABGR-packed colors.
        return (argb & 0xFF00FF00)
                | ((argb & 0x00FF0000) >>> 16)
                | ((argb & 0x000000FF) << 16);
    }

    /**
     * Gets the width of the cached texture for the specified URL.
     *
     * @param path The URL of the texture
     * @return the width of the cached texture, or 0 if not yet loaded.
     */
    public int getTextureWidth(String path) {

        if (path == null || path.isEmpty()) return 0;
        TextureData data = textures.getIfPresent(path);
        return data != null ? data.width() : 0;
    }

    /**
     * Gets the height of the cached texture for the specified URL.
     *
     * @param path The URL of the texture
     * @return the height of the cached texture, or 0 if not yet loaded.
     */
    public int getTextureHeight(String path) {

        if (path == null || path.isEmpty()) return 0;
        TextureData data = textures.getIfPresent(path);
        return data != null ? data.height() : 0;
    }

    /**
     * Override the time source used for cache-expiry checks.
     *
     * @param clock Clock supplier, or null to restore the system clock.
     */
    void setClock(LongSupplier clock) {

        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** Cached texture data and decoded dimensions. */
    private record TextureData(Identifier image, int width, int height) {

        /**
         * Estimate the memory cost of this texture in bytes.
         *
         * @return Approximate RGBA byte weight.
         */
        int byteWeight() {
            long weight = (long) width * height * 4L;
            return weight > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1L, weight);
        }
    }

    /**
     * Persisted freshness metadata for one disk-cache entry.
     */
    private record CacheMetadata(String lastModified, long fetchedAt, long maxAgeSeconds, int status) {

        /**
         * Build persisted metadata from a fresh fetch result.
         *
         * @param fetch Fetch result.
         * @param now Current epoch milliseconds.
         * @return Persisted cache metadata.
         */
        static CacheMetadata fromFetch(FetchResult fetch, long now) {

            int persistedStatus = fetch.isAbsent() ? 204 : fetch.status();
            return new CacheMetadata(fetch.lastModified(), now, fetch.maxAgeSeconds(), persistedStatus);
        }

        /**
         * Refresh this metadata after a 304 response.
         *
         * @param now Current epoch milliseconds.
         * @param fetch Fetch result.
         * @return Updated metadata.
         */
        CacheMetadata refreshed(long now, FetchResult fetch) {

            return new CacheMetadata(
                    fetch.lastModified() == null ? lastModified : fetch.lastModified(),
                    now,
                    fetch.maxAgeSeconds(),
                    status);
        }

        /**
         * Determine whether this entry represents an absent resource.
         *
         * @return True for absent responses.
         */
        boolean isAbsent() {

            return status == 204 || status == 404;
        }
    }

    /**
     * Disk-cache payload combined with its metadata.
     */
    private record DiskEntry(byte[] bytes, CacheMetadata metadata) {

        /**
         * Determine whether this entry is still fresh at the given time.
         *
         * @param now Current epoch milliseconds.
         * @return True when the entry is still fresh.
         */
        boolean isFresh(long now) {

            return now - metadata.fetchedAt() < metadata.maxAgeSeconds() * 1000L;
        }
    }

    /**
     * Result of loading a URL from cache or network.
     */
    private record LoadResult(URI uri, byte @Nullable [] bytes, boolean absent, long absentTtlSeconds) {

        /**
         * Build a load result from a disk entry.
         *
         * @param uri Source URI.
         * @param entry Disk entry.
         * @return Load result for the entry.
         */
        static LoadResult fromDisk(URI uri, DiskEntry entry) {

            if (entry.metadata().isAbsent()) return absent(uri, entry.metadata().maxAgeSeconds());
            return fromBytes(uri, entry.bytes());
        }

        /**
         * Build a load result from raw bytes.
         *
         * @param uri Source URI.
         * @param bytes Decoded bytes.
         * @return Load result with bytes.
         */
        static LoadResult fromBytes(URI uri, byte[] bytes) {

            return new LoadResult(uri, bytes, false, 0L);
        }

        /**
         * Build a load result for an absent resource.
         *
         * @param uri Source URI.
         * @param absentTtlSeconds Negative-cache TTL.
         * @return Load result flagged as absent.
         */
        static LoadResult absent(URI uri, long absentTtlSeconds) {

            return new LoadResult(uri, null, true, absentTtlSeconds);
        }

        /**
         * Build an empty load result for a 304 without cached bytes.
         *
         * @param uri Source URI.
         * @return Empty load result.
         */
        static LoadResult empty(URI uri) {

            return new LoadResult(uri, null, false, 0L);
        }
    }

    /**
     * Strategy for retrieving a URI and its cache headers.
     */
    @FunctionalInterface
    interface Fetcher {

        CompletableFuture<FetchResult> fetch(URI uri, @Nullable String lastModified);
    }
}
