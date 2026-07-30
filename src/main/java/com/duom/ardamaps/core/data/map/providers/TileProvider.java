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
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface for providing map tiles based on tile keys.
 * This class manages an in-memory cache of tile textures and handles asynchronous loading of tiles.
 * <p>
 * Loading is bounded and prioritised on a per-frame basis: renderers call {@link #beginFrame()} once
 * per frame, register every tile they'd like loaded via {@link #request(TileKey, int)} (lower priority
 * value = more urgent), then call {@link #endFrame()} to submit at most {@link #MAX_SUBMITS_PER_FRAME}
 * of the highest-priority candidates, never exceeding {@link #MAX_IN_FLIGHT} concurrent loads. Any
 * candidate not submitted is simply dropped - it will be re-registered next frame if still visible,
 * which is an exact and free cancellation mechanism for work that never started.
 * </p>
 */
public abstract class TileProvider<T extends TileKey> {

    /** Maximum in-memory texture cache size */
    protected static final int MAX_CACHE_SIZE = 256;

    /** How long a transport/IO failure suppresses retries for a tile key. */
    protected static final long TRANSPORT_FAILURE_TTL_MS = 30_000L;

    /** Hard cap on fetches in flight at any moment, across all priority tiers. */
    protected static final int MAX_IN_FLIGHT = 8;

    /** Tiles submitted per {@link #endFrame()} call, so a burst of newly-visible tiles can't be issued in one frame. */
    protected static final int MAX_SUBMITS_PER_FRAME = 4;

    /** Set of tile keys currently being loaded (thread-safe) */
    protected final Set<T> loading = ConcurrentHashMap.newKeySet();

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

    /**
     * Textures for the pinned (coarsest) zoom level, held outside the LRU so a fallback tile is
     * always available and can never be evicted by churn at other zoom levels.
     */
    protected final Map<T, Identifier> pinnedTextures = new ConcurrentHashMap<>();

    /** Zoom level whose textures are routed into {@link #pinnedTextures} instead of the LRU cache. */
    @Setter
    protected volatile int pinnedZoom = Integer.MIN_VALUE;

    /**
     * Candidates registered this frame via {@link #request(TileKey, int)}, mapped to their best
     * (lowest) requested priority. Not thread-safe: frame methods must only be called from the
     * render thread, matching how renderers already drive this class.
     */
    private final Map<T, Integer> frameRequests = new LinkedHashMap<>();

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
            return;
        }

        Minecraft.getInstance().execute(() -> {
            String textureName = prefix + key.z + "_" + key.x + "_" + key.y;
            DynamicTexture tex = new DynamicTexture(() -> textureName, image);
            Identifier id = com.duom.ardamaps.gui.ModConstants.modId(textureName);
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, tex);

            if (key.z == pinnedZoom) {
                pinnedTextures.put(key, id);
            } else {
                textures.put(key, id);
            }
            loading.remove(key);
        });
    }

    /**
     * Clears all frame-scoped request candidates. Renderers must call this exactly once at the
     * start of each frame, before any {@link #request(TileKey, int)} calls for that frame.
     */
    public void beginFrame() {
        frameRequests.clear();
    }

    /**
     * Looks up the texture for the given tile key. If not cached, registers the key as a load
     * candidate for this frame at the given priority (lower value = more urgent); the actual fetch
     * is only started when {@link #endFrame()} runs, and only if it fits within this frame's and
     * the provider's overall budget.
     *
     * @param key      The tile key.
     * @param priority Requested priority; lower values are serviced first. If the key is requested
     *                 multiple times in the same frame, the lowest (most urgent) priority wins.
     * @return An Optional containing the texture identifier if already loaded, or empty otherwise.
     */
    public Optional<Identifier> request(T key, int priority) {

        Optional<Identifier> cached = peek(key);
        if (cached.isPresent()) return cached;

        if (missingKeys.getIfPresent(key) != null) return Optional.empty();

        if (isTransportFailed(key, System.currentTimeMillis())) return Optional.empty();

        if (!loading.contains(key)) {
            frameRequests.merge(key, priority, Math::min);
        }

        return Optional.empty();
    }

    /**
     * Submits the highest-priority candidates registered this frame for loading, respecting both
     * the per-frame submission cap and the overall in-flight budget. Candidates that don't fit are
     * dropped - not deferred - and will simply be re-requested next frame if still relevant. This
     * bounds the total number of concurrent fetches regardless of how many tiles become visible at
     * once (e.g. during a fast pan/zoom).
     */
    public void endFrame() {

        if (frameRequests.isEmpty()) return;

        List<Map.Entry<T, Integer>> candidates = new ArrayList<>(frameRequests.entrySet());
        candidates.sort(Map.Entry.comparingByValue());

        int submitted = 0;
        for (Map.Entry<T, Integer> entry : candidates) {

            if (submitted >= MAX_SUBMITS_PER_FRAME) break;
            if (loading.size() >= MAX_IN_FLIGHT) break;

            T key = entry.getKey();
            if (loading.add(key)) {
                loadTile(key);
                submitted++;
            }
        }
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

        Identifier pinned = pinnedTextures.get(key);
        if (pinned != null) return Optional.of(pinned);

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
     * Asynchronously loads a map tile for the given tile key.
     * <p>
     * When this method is invoked by {@link #endFrame()} or {@link #eagerLoadTile(TileKey)}, the key
     * has already been added to {@link #loading}. Implementors must <em>not</em> call
     * {@code loading.add(key)} again; doing so would always return {@code false} and silently abort
     * the fetch.
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
     * @param key The key whose in-flight state should be cleared.
     */
    protected void clearLoading(T key) {

        loading.remove(key);
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
     * Eagerly and asynchronously loads a map tile for the given tile key, bypassing the per-frame
     * budget. Used sparingly for one-off preloads (e.g. the coarsest LOD at configure time) - callers
     * are responsible for not calling this so often that it defeats {@link #MAX_IN_FLIGHT}.
     *
     * @param key The tile key identifying the tile to load.
     */
    public void eagerLoadTile(T key) {

        if (peek(key).isPresent()) return;
        if (loading.add(key)) {
            loadTile(key);
        }
    }

    /**
     * Releases registered tile textures and clears transient async state.
     */
    public void close() {

        closed = true;

        textures.invalidateAll();
        textures.cleanUp();
        pinnedTextures.values().forEach(this::destroyTexture);
        pinnedTextures.clear();
        loading.clear();
        frameRequests.clear();
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
