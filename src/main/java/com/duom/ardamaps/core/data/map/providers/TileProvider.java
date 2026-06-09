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

import com.duom.ardamaps.core.data.map.tiles.TileKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface for providing map tiles based on tile keys.
 * This class manages an in-memory cache of tile textures and handles asynchronous loading of tiles.
 */
public abstract class TileProvider<T extends TileKey> {

    /** Maximum in-memory texture cache size */
    protected static final int MAX_CACHE_SIZE = 256;

    /** Caffeine LRU cache for tile textures */
    protected final Cache<T, Identifier> textures = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    /** Set of tile keys currently being loaded (thread-safe) */
    protected final Set<T> loading = ConcurrentHashMap.newKeySet();

    /** Minimum zoom level available in the PMTiles file */
    @Getter
    protected int minZoom = 0;

    /** Maximum zoom level available in the PMTiles file */
    @Getter
    protected int maxZoom = 0;

    /** Debounce window for buffered get requests. */
    protected static final long REQUEST_BUFFER_TTL_MS = 500L;

    /** Keys seen once by get(); a second hit within the debounce window triggers loading. */
    protected final ConcurrentHashMap<T, Long> pendingRequests = new ConcurrentHashMap<>();

    /** Keys that hit transport/IO failures and must not be retried for this provider instance. */
    protected final Set<T> transportFailedKeys = ConcurrentHashMap.newKeySet();

    /**
     * Registers the given NativeImage as a texture in Minecraft and associates it with the tile key.
     *
     * @param prefix A prefix for the texture identifier.
     * @param image  The NativeImage to register.
     * @param key    The tile key associated with the image.
     */
    protected void registerTexture(String prefix, NativeImage image, T key) {

        if (image == null) {
            // Null images can happen for non-transport reasons (missing tile, out-of-range, decode miss).
            loading.remove(key);
            pendingRequests.remove(key);
            return;
        }

        MinecraftClient.getInstance().execute(() -> {
            NativeImageBackedTexture tex = new NativeImageBackedTexture(image);

            Identifier id = MinecraftClient.getInstance()
                    .getTextureManager()
                    .registerDynamicTexture(prefix + key.z + "_" + key.x + "_" + key.y, tex);

            textures.put(key, id);
            loading.remove(key);
            pendingRequests.remove(key);
        });
    }

    /**
     * Get the texture identifier for the given tile key.
     * If the tile is not yet loaded, initiates loading and returns an empty Optional.
     *
     * @param key The tile key
     * @return An Optional containing the texture identifier if loaded, or empty if loading is initiated
     */
    public Optional<Identifier> get(T key) {

        Identifier cached = textures.getIfPresent(key);
        if (cached != null) return Optional.of(cached);
        if (transportFailedKeys.contains(key)) return Optional.empty();

        long now = System.currentTimeMillis();
        pruneExpiredPendingRequests(now);

        Long firstSeenAt = pendingRequests.remove(key);
        if (firstSeenAt == null || now - firstSeenAt > REQUEST_BUFFER_TTL_MS) {
            pendingRequests.put(key, now);
            return Optional.empty();
        }

        if (loading.add(key)) {
            loadTile(key);
        }

        return Optional.ofNullable(textures.getIfPresent(key));
    }

    /**
     * Marks a key as transport-failed (IO/network error): this provider instance will not retry it.
     */
    protected void markTransportFailure(T key) {

        transportFailedKeys.add(key);
        loading.remove(key);
        pendingRequests.remove(key);
    }

    /**
     * Removes first-seen entries that aged out without receiving a second request.
     */
    private void pruneExpiredPendingRequests(long now) {

        pendingRequests.forEach((pendingKey, firstSeenAt) -> {
            if (now - firstSeenAt > REQUEST_BUFFER_TTL_MS)
                pendingRequests.remove(pendingKey, firstSeenAt);
        });
    }

    /**
     * Asynchronously loads a map tile for the given tile key.
     * <p>
     * When this method is invoked by {@link #get(TileKey)}, the key has already been added
     * to {@link #loading}. Implementors must <em>not</em> call {@code loading.add(key)} again;
     * doing so would always return {@code false} and silently abort the fetch.
     * </p>
     *
     * @param key The tile key identifying the tile to load.
     */
    protected abstract void loadTile(T key);

    /**
     * Eagerly and asynchronously loads a map tile for the given tile key.
     * This method bypass the debouncing on tile loading, essentially "force-loading" the tile.
     * This allows for preloading of tiles.
     * @param key The tile key identifying the tile to load.
     */
    public void eagerLoadTile(T key) {
        loadTile(key);
    }
}