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
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.mojang.blaze3d.platform.NativeImage;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

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

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(TileProvider.class);

    /** Maximum negative-cache entry count for small key-only caches. */
    protected static final int MAX_CACHE_SIZE = 256;

    /** Default decoded texture memory budget: 128 MiB. */
    protected static final long DEFAULT_TEXTURE_CACHE_BUDGET_BYTES = 128L << 20;

    /** Priority band for primary-zoom viewport tiles. */
    public static final int PRIMARY_VIEWPORT_PRIORITY_BASE = 0;

    /** Priority band for primary-zoom tiles one tile beyond the viewport. */
    public static final int PRIMARY_PREFETCH_PRIORITY_BASE = 5_000;

    /** Priority band for coarse tiles backing the current viewport fallback. */
    public static final int VIEWPORT_FALLBACK_PRIORITY_BASE = 10_000;

    /** Priority band for the settled-camera adjacent zoom-step viewport. */
    public static final int ZOOM_STEP_PRIORITY_BASE = 15_000;

    /** Maximum number of completed NativeImages uploaded to GL during one render frame. */
    protected static final int MAX_TEXTURE_UPLOADS_PER_FRAME = 4;

    /** Speculative zoom-step prefetch may consume at most this fraction of the texture budget. */
    private static final int ZOOM_STEP_BUDGET_DIVISOR = 4;

    /** Records the first decoded tile dimensions once per provider classloader. */
    private static final AtomicBoolean LOGGED_TEXTURE_DIMENSIONS = new AtomicBoolean();

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

    /** Default absent-tile retry TTL: 4 hours, matching BlueMap's measured 204 cache lifetime. */
    protected static final long DEFAULT_MISSING_TTL_MS = 4L * 60 * 60 * 1000;

    /** Keys confirmed absent from the source (not a failure), mapped to their retry-after timestamp. */
    protected final Cache<T, Long> missingKeys = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    /** Decode failures by key. Once a key reaches the retry cap it is marked missing permanently. */
    protected final ConcurrentHashMap<T, Integer> decodeFailureCounts = new ConcurrentHashMap<>();

    /** Keys abandoned after repeated decode failures; unlike generic missing keys, this is not LRU-evicted. */
    protected final Set<T> decodeAbandonedKeys = ConcurrentHashMap.newKeySet();

    /** Set once {@link #close()} has run; lets in-flight async loads bail out instead of touching released state. */
    protected volatile boolean closed;

    /** Time source for retry TTL checks; overridable in tests. */
    private LongSupplier clock = System::currentTimeMillis;

    /**
     * Textures for tiles drawn this frame, held outside the LRU so on-screen primary tiles cannot
     * be evicted by speculative prefetch churn. Refreshed every frame via {@link #protectDrawnTiles}.
     */
    private final Map<T, TextureData> protectedTextures = new ConcurrentHashMap<>();

    /**
     * Removal listener that destroys dynamic textures evicted from the in-memory cache.
     * <p>
     * Two removals are ownership transfers, not real destruction points: deterministic texture-id
     * overwrites are reported as {@link RemovalCause#REPLACED}, and {@link #protectDrawnTiles(Set)}
     * removes an entry from the LRU only after first publishing it to {@link #protectedTextures}.
     * Destroying either would release a GL id that is still live.
     * </p>
     */
    private final RemovalListener<T, TextureData> textureRemovalListener =
            (key, texture, cause) -> {
                if (texture == null) return;
                if (cause == RemovalCause.REPLACED) return;
                if (key != null && protectedTextures.containsKey(key)) return;
                destroyTexture(texture.id());
            };

    /** Caffeine LRU cache for tile textures, weighted by decoded RGBA bytes. */
    protected final Cache<T, TextureData> textures = Caffeine.newBuilder()
            .maximumWeight(textureCacheBudgetBytes())
            .weigher((T ignoredKey, TextureData texture) -> texture.byteWeight())
            .removalListener(textureRemovalListener)
            .build();

    /** Completed image decodes waiting for a bounded render-thread GL upload slot. */
    private final Queue<PendingTexture<T>> pendingTextureUploads = new ConcurrentLinkedQueue<>();

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

    /**
     * Background bootstrap queue for coarse pyramid tiles. This deliberately does not feed
     * {@link #frameRequests}: bootstrap work is issued only when normal viewport loading is fully
     * idle, so it cannot occupy executor slots ahead of interactive requests.
     */
    private final Queue<T> bootstrapRequests = new ArrayDeque<>();

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

        pendingTextureUploads.add(new PendingTexture<>(prefix, image, key));
    }

    /**
     * Clears all frame-scoped request candidates. Renderers must call this exactly once at the
     * start of each frame, before any {@link #request(TileKey, int)} calls for that frame.
     */
    public void beginFrame() {
        drainTextureUploads();
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

        if (isMissing(key, clock.getAsLong())) return Optional.empty();

        if (decodeAbandonedKeys.contains(key)) return Optional.empty();

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

        if (frameRequests.isEmpty()) {
            pumpBootstrap();
            return;
        }

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

        pumpBootstrap();
    }

    /**
     * Adds coarse pyramid tiles to the background bootstrap queue.
     *
     * @param keys Tile keys to load opportunistically after viewport work is idle.
     */
    public void enqueueBootstrapTiles(Collection<T> keys) {

        for (T key : keys) {
            if (peek(key).isPresent()) continue;
            if (decodeAbandonedKeys.contains(key)) continue;
            bootstrapRequests.add(key);
        }
    }

    private void pumpBootstrap() {

        if (!loading.isEmpty()) return;

        while (!bootstrapRequests.isEmpty()) {
            T key = bootstrapRequests.poll();
            if (key == null) return;
            if (peek(key).isPresent()) continue;
            if (isMissing(key, clock.getAsLong())) continue;
            if (decodeAbandonedKeys.contains(key)) continue;
            if (isTransportFailed(key, clock.getAsLong())) continue;

            if (loading.add(key)) loadTile(key);
            return;
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

        TextureData protectedData = protectedTextures.get(key);
        if (protectedData != null) return Optional.of(protectedData.id());

        TextureData data = textures.getIfPresent(key);
        return data == null ? Optional.empty() : Optional.of(data.id());
    }

    protected void cacheTexture(T key, Identifier texture) {

        textures.put(key, new TextureData(texture, 1, 1));
    }

    /**
     * Keeps the currently drawn tile textures outside the LRU for the next frame, while returning
     * no-longer-drawn textures to normal byte-weighted eviction. This protects only real draw work:
     * speculative prefetches remain evictable and cannot push visible primary tiles back to a
     * coarse fallback loop.
     *
     * @param keys Tile keys actually drawn this frame.
     */
    public void protectDrawnTiles(Set<T> keys) {

        Set<T> drawnKeys = keys == null ? Set.of() : keys;

        for (T key : drawnKeys) {
            if (pinnedTextures.containsKey(key)) continue;
            if (protectedTextures.containsKey(key)) continue;

            TextureData data = textures.getIfPresent(key);
            if (data == null) continue;

            protectedTextures.put(key, data);
            textures.asMap().remove(key);
        }

        for (T key : new ArrayList<>(protectedTextures.keySet())) {
            if (drawnKeys.contains(key)) continue;

            TextureData data = protectedTextures.remove(key);
            if (data != null) textures.put(key, data);
        }
    }

    /**
     * Uploads a bounded number of completed tile images to GL. Renderers call {@link #beginFrame()}
     * from the render thread, so draining here coalesces bursts of async completions into a small,
     * predictable per-frame cost instead of posting one client task per completed tile.
     */
    void drainTextureUploads() {

        for (int i = 0; i < MAX_TEXTURE_UPLOADS_PER_FRAME; i++) {
            PendingTexture<T> pending = pendingTextureUploads.poll();
            if (pending == null) return;
            uploadTexture(pending);
        }
    }

    private void uploadTexture(PendingTexture<T> pending) {

        T key = pending.key();
        String textureName = pending.prefix() + key.z + "_" + key.x + "_" + key.y;
        NativeImage image = pending.image();
        Identifier id = com.duom.ardamaps.gui.ModConstants.modId(textureName);
        if (LOGGED_TEXTURE_DIMENSIONS.compareAndSet(false, true)) {
            LOGGER.info("[ArdaMaps] First decoded tile texture is {}x{} px; decoded texture cache budget is {} bytes.",
                    image.getWidth(), image.getHeight(), textureCacheBudgetBytes());
        }
        uploadNativeTexture(textureName, image, id);

        if (key.z == pinnedZoom) {
            pinnedTextures.put(key, id);
        } else if (protectedTextures.containsKey(key)) {
            protectedTextures.put(key, new TextureData(id, image.getWidth(), image.getHeight()));
        } else {
            textures.put(key, new TextureData(id, image.getWidth(), image.getHeight()));
        }
        loading.remove(key);
    }

    protected void uploadNativeTexture(String textureName, NativeImage image, Identifier id) {

        DynamicTexture tex = new DynamicTexture(() -> textureName, image);
        Minecraft.getInstance()
                .getTextureManager()
                .register(id, tex);
    }

    static long textureCacheBudgetBytes() {

        String configured = System.getProperty("ardamaps.textureCacheBudgetBytes");
        if (configured == null || configured.isBlank()) return DEFAULT_TEXTURE_CACHE_BUDGET_BYTES;

        try {
            return Math.max(1L, Long.parseLong(configured));
        } catch (NumberFormatException ignored) {
            return DEFAULT_TEXTURE_CACHE_BUDGET_BYTES;
        }
    }

    /** Byte ceiling a settled-frame zoom-step layer may request before it is skipped. */
    public static long zoomStepByteCeiling() {

        return textureCacheBudgetBytes() / ZOOM_STEP_BUDGET_DIVISOR;
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

    private boolean isMissing(T key, long now) {

        Long retryAfter = missingKeys.getIfPresent(key);
        if (retryAfter == null) return false;

        if (now < retryAfter) return true;

        missingKeys.invalidate(key);
        return false;
    }

    /**
     * Asynchronously loads a map tile for the given tile key.
     * <p>
     * When this method is invoked by {@link #endFrame()}, the key has already been added to
     * {@link #loading}. Implementors must <em>not</em> call {@code loading.add(key)} again; doing
     * so would always return {@code false} and silently abort the fetch.
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
     * Records a decode failure for a key. Decode failures are deterministic for a given source
     * byte payload, so after three failed decode attempts the key is abandoned as permanently
     * missing instead of being retried every frame.
     *
     * @param key The key whose tile bytes could not be decoded.
     */
    protected void markDecodeFailure(T key) {

        int failures = decodeFailureCounts.merge(key, 1, Integer::sum);
        if (failures >= 3) {
            decodeFailureCounts.remove(key);
            decodeAbandonedKeys.add(key);
            markMissing(key);
        } else {
            clearLoading(key);
        }
    }

    /**
     * Marks a key as confirmed absent from the source (e.g. outside the archive's tile set).
     * Absence is cached for a bounded TTL: BlueMap regions can be rendered later, and PMTiles
     * still benefits from suppressing repeated archive misses without making the marker permanent.
     *
     * @param key The tile key known not to exist in the source.
     */
    protected void markMissing(T key) {

        markMissing(key, DEFAULT_MISSING_TTL_MS);
    }

    /**
     * Marks a key as absent until its source-declared TTL expires.
     *
     * @param key   The tile key known not to exist right now.
     * @param ttlMs How long retry should be suppressed.
     */
    protected void markMissing(T key, long ttlMs) {

        missingKeys.put(key, clock.getAsLong() + Math.max(0L, ttlMs));
        clearLoading(key);
    }

    void setClock(LongSupplier clock) {

        this.clock = clock == null ? System::currentTimeMillis : clock;
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
     * Releases registered tile textures and clears transient async state.
     */
    public void close() {

        closed = true;

        textures.invalidateAll();
        textures.cleanUp();
        pinnedTextures.values().forEach(this::destroyTexture);
        pinnedTextures.clear();
        protectedTextures.values().forEach(texture -> destroyTexture(texture.id()));
        protectedTextures.clear();
        PendingTexture<T> pending;
        while ((pending = pendingTextureUploads.poll()) != null) {
            pending.image().close();
        }
        loading.clear();
        bootstrapRequests.clear();
        frameRequests.clear();
        transportFailedKeys.clear();
        decodeFailureCounts.clear();
        decodeAbandonedKeys.clear();
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

    /**
     * A cached tile texture with its dimensions, used for calculating byte weight in the texture cache.
     *
     * @param id The texture identifier registered in Minecraft.
     * @param width The decoded texture width in pixels.
     * @param height The decoded texture height in pixels.
     */
    protected record TextureData(Identifier id, int width, int height) {

        /**
         * Returns the byte weight of this texture for cache eviction priority (RGBA: 4 bytes per pixel).
         *
         * @return The weight in bytes, clamped to {@link Integer#MAX_VALUE}.
         */
        int byteWeight() {
            long weight = (long) width * height * 4L;
            return weight > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1L, weight);
        }
    }

    /**
     * A completed image decode waiting for a bounded GL upload to the render thread.
     *
     * @param prefix The texture name prefix (e.g. "bluemap_" or "pmtiles_").
     * @param image The decoded NativeImage ready for GPU upload.
     * @param key The tile key this image corresponds to.
     */
    private record PendingTexture<T extends TileKey>(String prefix, NativeImage image, T key) {

    }
}
