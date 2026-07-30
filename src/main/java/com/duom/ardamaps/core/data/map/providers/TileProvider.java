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
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.mojang.blaze3d.platform.NativeImage;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

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

    /** Debounce window for buffered get requests. */
    protected static final long REQUEST_BUFFER_TTL_MS = 500L;

    /** How long a transport/IO failure suppresses retries for a tile key. */
    protected static final long TRANSPORT_FAILURE_TTL_MS = 30_000L;

    /** Set of tile keys currently being loaded (thread-safe) */
    protected final Set<T> loading = ConcurrentHashMap.newKeySet();

    /** Keys seen once by get(); a second hit within the debounce window triggers loading. */
    protected final ConcurrentHashMap<T, Long> pendingRequests = new ConcurrentHashMap<>();

    /** Keys that hit transport/IO failures, mapped to failure timestamp. */
    protected final ConcurrentHashMap<T, Long> transportFailedKeys = new ConcurrentHashMap<>();

    /** Keys confirmed absent from the source (not a failure); bounded so a long session can't leak memory. */
    protected final Cache<T, Boolean> missingKeys = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    /** Set once {@link #close()} has run; lets in-flight async loads bail out instead of touching released state. */
    protected volatile boolean closed;

    /** Removal listener that destroys dynamic textures evicted from the in-memory cache. */
    private final RemovalListener<T, Identifier> textureRemovalListener =
            (ignoredKey, texture, ignoredCause) -> destroyTexture(texture);

    /** Caffeine LRU cache for tile textures */
    protected final Cache<T, Identifier> textures = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .removalListener(textureRemovalListener)
            .build();

    /** Minimum zoom level available in the PMTiles file */
    @Getter
    protected int minZoom = 0;

    /** Maximum zoom level available in the PMTiles file */
    @Getter
    protected int maxZoom = 0;

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

        Minecraft.getInstance().execute(() -> {
            String textureName = prefix + key.z + "_" + key.x + "_" + key.y;
            DynamicTexture tex = new DynamicTexture(() -> textureName, image);
            Identifier id = com.duom.ardamaps.gui.ModConstants.modId(textureName);
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, tex);

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

        if (missingKeys.getIfPresent(key) != null) return Optional.empty();

        long now = System.currentTimeMillis();
        if (isTransportFailed(key, now)) return Optional.empty();

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
     * Returns the cached texture for the given key, if already loaded, without triggering a load
     * or otherwise mutating any transient state. Intended for fallback lookups (e.g. coarser zoom
     * levels) that should not themselves count as tile requests.
     *
     * @param key The tile key.
     * @return The cached texture identifier, or empty if not currently loaded.
     */
    public Optional<Identifier> peek(T key) {

        return Optional.ofNullable(textures.getIfPresent(key));
    }

    /**
     * Returns whether the key is still in its transport-failure retry cooldown.
     *
     * @param key The tile key.
     * @param now Current epoch milliseconds.
     * @return Whether retry should be suppressed.
     */
    private boolean isTransportFailed(T key, long now) {

        Long failedAt = transportFailedKeys.get(key);
        if (failedAt == null) return false;

        if (now - failedAt <= TRANSPORT_FAILURE_TTL_MS) return true;

        transportFailedKeys.remove(key, failedAt);
        return false;
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
     * Marks a key as transport-failed (IO/network error): this provider instance will not retry it.
     */
    protected void markTransportFailure(T key) {

        transportFailedKeys.put(key, System.currentTimeMillis());
        clearLoading(key);
    }

    /**
     * Marks a key as confirmed absent from the source (e.g. outside the archive's tile set).
     * Unlike a transport failure, this is not time-limited: the source is not going to gain a
     * tile it does not have, so continuing to retry it every frame would be pure waste.
     *
     * @param key The tile key known not to exist in the source.
     */
    protected void markMissing(T key) {

        missingKeys.put(key, Boolean.TRUE);
        clearLoading(key);
    }

    /**
     * Clears transient async state for the given key.
     *
     * @param key The key whose in-flight/debounce state should be cleared.
     */
    protected void clearLoading(T key) {

        loading.remove(key);
        pendingRequests.remove(key);
    }

    /**
     * Returns a snapshot of the tile keys currently in flight (loading).
     *
     * @return An immutable snapshot of the currently-loading tile keys.
     */
    public Set<T> getLoadingTiles() {

        return Set.copyOf(loading);
    }

    /**
     * Returns the source URL for the given tile key, if this provider fetches tiles over HTTP.
     * Providers with no per-tile URL (e.g. PMTiles archives) return {@code null}.
     *
     * @param key The tile key.
     * @return The source URL for the tile, or {@code null} if not applicable.
     */
    public String getTileSourceUrl(T key) {

        return null;
    }

    /**
     * Eagerly and asynchronously loads a map tile for the given tile key.
     * This method bypass the debouncing on tile loading, essentially "force-loading" the tile.
     * This allows for preloading of tiles.
     *
     * @param key The tile key identifying the tile to load.
     */
    public void eagerLoadTile(T key) {
        loadTile(key);
    }

    /**
     * Releases registered tile textures and clears transient async state.
     */
    public void close() {

        closed = true;

        textures.invalidateAll();
        textures.cleanUp();
        loading.clear();
        pendingRequests.clear();
        transportFailedKeys.clear();
        missingKeys.invalidateAll();
    }

    /**
     * Destroys a dynamic texture on the client thread.
     *
     * @param texture The texture identifier to destroy.
     */
    protected void destroyTexture(Identifier texture) {

        if (texture == null) return;

        Minecraft client = Minecraft.getInstance();

        client.execute(() -> Minecraft.getInstance().getTextureManager().release(texture));
    }
}
