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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Provider for loading images over HTTP.
 * <p>
 * This class implements a two-tier caching strategy using Caffeine for in-memory caching and DiskLruCache
 * for persistent on-disk caching. It supports asynchronous loading of images and handles common image formats
 * including WebP, PNG, and JPEG.
 */
public class HttpImageProvider {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpImageProvider.class);

    /** Maximum in-memory texture cache size */
    private static final int MAX_MEMORY_CACHE_SIZE = 256;

    /** Maximum disk cache size: 512 Mb */
    private static final long DISK_CACHE_MAX_SIZE = 512L << 20;

    /** Disk cache app version - bump when cache format changes */
    private static final int DISK_CACHE_APP_VERSION = 1;

    /** How long BlueMap tiles are cached on disk before a forced refresh */
    private static final long BLUEMAP_REFRESH_INTERVAL_MINUTES = 15;

    /** How long a disk entry must go un-accessed before it is evicted (1 day) */
    private static final long STALE_ENTRY_TTL_MS = 24L * 60 * 60 * 1000;

    /** Set of URLs currently being loaded (thread-safe) */
    private final Set<String> loading = ConcurrentHashMap.newKeySet();

    /** Caffeine in-memory LRU cache for registered textures */
    private final Cache<String, TextureData> textures = Caffeine.newBuilder()
            .maximumSize(MAX_MEMORY_CACHE_SIZE)
            .build();
    /**
     * Maps every disk-cache key to their original URL.
     * Used to locate and invalidate entries by URL prefix (e.g. all BlueMap tiles).
     */
    private final ConcurrentHashMap<String, String> diskKeyToUrl = new ConcurrentHashMap<>();
    /**
     * Tracks the last time (epoch ms) each disk-cache key was accessed.
     * Used by the stale-entry eviction job.
     */
    private final ConcurrentHashMap<String, Long> lastAccessTimes = new ConcurrentHashMap<>();
    /**
     * Holds the {@link ScheduledFuture} for each URL prefix registered for periodic BlueMap refresh.
     * Keyed by the prefix string so duplicate calls are no-ops.
     */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> refreshSchedules = new ConcurrentHashMap<>();
    /** Single-threaded daemon scheduler for periodic cache-maintenance tasks */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ardamaps-cache-scheduler");
        t.setDaemon(true);
        return t;
    });
    /** Lazily-initialised DiskLruCache */
    private volatile DiskLruCache diskCache;

    public HttpImageProvider() {
        // Evict stale entries once per hour since the client process lives less than a day,
        // this single sweep keeps disk use bounded without needing cross-session timestamps.
        scheduler.scheduleAtFixedRate(this::evictStaleEntries, 60, 60, TimeUnit.MINUTES);
    }

    /**
     * Evicts disk-cached entries not accessed within {@value STALE_ENTRY_TTL_MS} ms (1 day).
     * Runs hourly via the scheduler. Because the client process lives less than a day,
     * only entries left over from a previously interrupted session will be evicted here.
     */
    private void evictStaleEntries() {

        long cutoff = System.currentTimeMillis() - STALE_ENTRY_TTL_MS;
        int count = 0;

        for (var entry : lastAccessTimes.entrySet()) {
            if (entry.getValue() > cutoff) continue;

            String diskKey = entry.getKey();
            String url = diskKeyToUrl.get(diskKey);

            try {
                DiskLruCache cache = getDiskCache();
                if (cache != null) cache.remove(diskKey);
            } catch (IOException e) {
                LOGGER.warn("[ArdaMaps] Failed to remove stale disk entry {}", diskKey, e);
            }

            if (url != null) {
                textures.invalidate(url);
                loading.remove(url);
                diskKeyToUrl.remove(diskKey);
            }
            lastAccessTimes.remove(diskKey);
            count++;
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
                                Client.cacheDirectory().toFile(),
                                DISK_CACHE_APP_VERSION,
                                1,
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

    // -------------------------------------------------------------------------
    // Cache maintenance - BlueMap refresh & stale eviction
    // -------------------------------------------------------------------------

    /**
     * Schedules a periodic task that invalidates all disk-cached entries whose
     * original URL starts with {@code urlPrefix} every {@value BLUEMAP_REFRESH_INTERVAL_MINUTES}
     * minutes.  Calling this a second time with the same prefix is a no-op.
     *
     * @param urlPrefix URL prefix that identifies BlueMap tile URLs
     *                  (e.g. {@code "https://example.com/maps/arda/tiles"}).
     */
    public void scheduleBlueMapRefresh(String urlPrefix) {
        refreshSchedules.computeIfAbsent(urlPrefix, prefix ->
                scheduler.scheduleAtFixedRate(
                        () -> invalidateDiskEntriesByPrefix(prefix),
                        BLUEMAP_REFRESH_INTERVAL_MINUTES,
                        BLUEMAP_REFRESH_INTERVAL_MINUTES,
                        TimeUnit.MINUTES
                )
        );
    }

    /**
     * Removes every disk entry and in-memory texture whose URL starts with {@code urlPrefix},
     * so the next {@link #getTexture} call triggers a fresh HTTP fetch.
     */
    private void invalidateDiskEntriesByPrefix(String urlPrefix) {

        LOGGER.info("[ArdaMaps] Refreshing BlueMap disk-cache entries");
        int count = 0;

        for (var entry : diskKeyToUrl.entrySet()) {
            if (!entry.getValue().startsWith(urlPrefix)) continue;

            String diskKey = entry.getKey();
            String url = entry.getValue();

            try {
                DiskLruCache cache = getDiskCache();
                if (cache != null) cache.remove(diskKey);
            } catch (IOException e) {
                LOGGER.warn("[ArdaMaps] Failed to remove disk entry {} during BlueMap refresh", diskKey, e);
            }

            textures.invalidate(url);
            loading.remove(url);
            diskKeyToUrl.remove(diskKey);
            lastAccessTimes.remove(diskKey);
            count++;
        }

        LOGGER.info("[ArdaMaps] BlueMap refresh complete - invalidated {} entries.", count);
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
        }, () -> loading.remove(url));
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

        CompletableFuture.supplyAsync(() -> {

            Pair<NativeImage, String> loadedImage = null;

            try {
                var uri = URI.create(url);
                byte[] rawImageData = loadBytes(uri);

                if (rawImageData != null) {
                    var fileExt = getFileExtension(uri);
                    loadedImage = switch (fileExt) {
                        case WEBP -> new Pair<>(loadWebpImage(rawImageData), url);
                        case PNG, JPG, JPEG -> new Pair<>(
                                NativeImage.read(new ByteArrayInputStream(rawImageData)), url);
                    };
                }

            } catch (IOException e) {
                LOGGER.warn("Failed to load image @\"{}\"", url);
                if (onIoFailure != null) onIoFailure.run();
            }

            return loadedImage;

        }, ArdaMapsClient.IMAGE_EXECUTOR).thenAccept(onComplete);
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
     * Loads raw image bytes for {@code uri}, consulting the DiskLruCache first
     * and falling back to an HTTP request on a miss.
     *
     * @param uri The URI of the image to load
     * @return The raw image bytes, or null if loading failed
     */
    private byte @Nullable [] loadBytes(URI uri) throws IOException {

        DiskLruCache cache = getDiskCache();
        String key = getDiskCacheKey(uri);
        String url = uri.toString();

        // Look for a disk hit
        if (cache != null) {

            DiskLruCache.Snapshot snapshot = cache.get(key);

            if (snapshot != null) {
                try (snapshot) {
                    // Update access metadata
                    diskKeyToUrl.put(key, url);
                    lastAccessTimes.put(key, System.currentTimeMillis());
                    return snapshot.getInputStream(0).readAllBytes();
                }
            }
        }

        // Network fetch on cache miss
        byte[] data = fetchFromUrl(uri);

        // Write-through to disk
        if (cache != null) {
            DiskLruCache.Editor editor = cache.edit(key);
            if (editor != null) {
                try (OutputStream out = editor.newOutputStream(0)) {
                    out.write(data);
                    editor.commit();
                    // Register metadata after successful write
                    diskKeyToUrl.put(key, url);
                    lastAccessTimes.put(key, System.currentTimeMillis());
                } catch (IOException e) {
                    editor.abort();
                    throw e;
                }
            }
        }

        return data;
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

    /* Disk I/O */

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
     * Returns a DiskLruCache-safe key for {@code uri} (lowercase alphanumeric + '-',
     * max 64 characters as required by DiskLruCache).
     *
     * @param uri The URI to generate a cache key for
     * @return A sanitized cache key derived from the URI
     */
    private @NotNull String getDiskCacheKey(URI uri) {

        String path = URLDecoder.decode(uri.getPath(), StandardCharsets.UTF_8);
        int hash = path.hashCode() & 0x7fffffff;
        String hashedString = Integer.toString(hash, 36);

        String filename = path.substring(path.lastIndexOf('/') + 1);
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex != -1) filename = filename.substring(0, dotIndex);

        String sanitized = filename.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String key = sanitized + "-" + hashedString;

        // DiskLruCache keys must be at most 64 characters
        return key.length() > 64 ? key.substring(key.length() - 64) : key;
    }

    /* Helpers */

    /**
     * Opens an HTTP connection and reads all bytes from the response body.
     *
     * @param uri The URI to fetch
     * @return The response body bytes
     */
    private byte[] fetchFromUrl(URI uri) throws IOException {

        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Minecraft-Fabric");

        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        }
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
                nativeImage.setColor(x, y, pixels[y * w + x].argb);
        return nativeImage;
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

    /** Record to hold texture data */
    private record TextureData(Identifier image, int width, int height) {
    }
}