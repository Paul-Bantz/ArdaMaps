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
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;

/**
 * Interface for providing map tiles based on tile keys.
 * This class manages an in-memory cache of tile textures and handles asynchronous loading of tiles.
 */
public abstract class TileProvider<T extends TileKey> {

    /** Maximum negative-cache entry count for small key-only caches. */
    protected static final int MAX_CACHE_SIZE = 256;

    /** Default decoded texture memory budget: 128 MiB. */
    protected static final long DEFAULT_TEXTURE_CACHE_BUDGET_BYTES = 128L << 20;

    /** Maximum number of concurrently loading tiles per provider. */
    public static final int MAX_IN_FLIGHT = 8;

    /** Maximum number of new tile loads submitted by one frame. */
    public static final int MAX_SUBMITS_PER_FRAME = 4;

    /** Default time to suppress known-missing tiles before probing again. */
    public static final long DEFAULT_MISSING_TTL_MS = 4L * 60 * 60 * 1000;

    /** Priority band for primary-zoom viewport tiles. */
    public static final int PRIMARY_VIEWPORT_PRIORITY_BASE = 0;

    /** Priority band for primary-zoom tiles one tile beyond the viewport. */
    public static final int PRIMARY_PREFETCH_PRIORITY_BASE = 5_000;

    /** Priority band for coarse tiles backing the current viewport fallback. */
    public static final int VIEWPORT_FALLBACK_PRIORITY_BASE = 10_000;

    /** Priority band for the settled-camera adjacent zoom-step viewport. */
    public static final int ZOOM_STEP_PRIORITY_BASE = 15_000;

    /** Caffeine LRU cache for tile textures */
    protected final Cache<T, TextureData> textures;

    /** Set of tile keys currently being loaded (thread-safe) */
    protected final Set<T> loading = ConcurrentHashMap.newKeySet();

    /** Per-frame request accumulator. Lower values mean higher priority. */
    protected final Map<T, Integer> frameRequests = new LinkedHashMap<>();

    /** Tiles kept outside the LRU because they are the configured coarse fallback level. */
    protected final ConcurrentHashMap<T, Identifier> pinnedTextures = new ConcurrentHashMap<>();

    /**
     * Textures drawn this frame, kept outside the LRU so visible tiles cannot be evicted by
     * speculative prefetch churn.
     */
    private final Map<T, TextureData> protectedTextures = new ConcurrentHashMap<>();

    /** Background bootstrap queue; pumped only when normal visible loading is idle. */
    private final Queue<T> bootstrapRequests = new ConcurrentLinkedQueue<>();

    /** Tile zoom to pin, or {@code null} when pinning is disabled. */
    protected volatile Integer pinnedZoom;

    /** Bounded negative cache for tiles known to be absent, mapped to retry-after epoch ms. */
    protected final Cache<T, Long> missingKeys;

    /** Decode failures by key. Once a key reaches the retry cap it is marked missing permanently. */
    protected final ConcurrentHashMap<T, Integer> decodeFailureCounts = new ConcurrentHashMap<>();

    /** Keys abandoned after repeated decode failures; unlike TTL-based misses, these never expire. */
    protected final Set<T> decodeAbandonedKeys = ConcurrentHashMap.newKeySet();

    /** Minimum zoom level available in the PMTiles file */
    @Getter
    protected int minZoom = 0;

    /** Maximum zoom level available in the PMTiles file */
    @Getter
    protected int maxZoom = 0;

    /** How long a transport/IO failure suppresses retries for a tile key. */
    protected static final long TRANSPORT_FAILURE_TTL_MS = 30_000L;

    /** Keys that hit transport/IO failures, mapped to failure timestamp. */
    protected final ConcurrentHashMap<T, Long> transportFailedKeys = new ConcurrentHashMap<>();

    /** Time source for retry TTL checks; overridable in tests. */
    private LongSupplier clock = System::currentTimeMillis;

    protected TileProvider() {
        this(Ticker.systemTicker());
    }

    protected TileProvider(Ticker ticker) {

        /* Removal listener that destroys dynamic textures evicted from the in-memory cache. */
        RemovalListener<T, TextureData> textureRemovalListener = (ignoredKey, texture, ignoredCause) -> {
            if (texture == null) return;
            if (ignoredKey != null && protectedTextures.containsKey(ignoredKey)) return;
            destroyTexture(texture.id());
        };
        this.textures = Caffeine.newBuilder()
                .maximumWeight(textureCacheBudgetBytes())
                .weigher((T ignoredKey, TextureData texture) -> texture.byteWeight())
                .removalListener(textureRemovalListener)
                .build();
        this.missingKeys = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .ticker(ticker)
                .build();
    }

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

        MinecraftClient.getInstance().execute(() -> {
            NativeImageBackedTexture tex = new NativeImageBackedTexture(image);

            Identifier id = MinecraftClient.getInstance()
                    .getTextureManager()
                    .registerDynamicTexture(prefix + key.z + "_" + key.x + "_" + key.y, tex);

            if (pinnedZoom != null && key.z == pinnedZoom) {
                Identifier old = pinnedTextures.put(key, id);
                if (old != null) destroyTexture(old);
            } else if (protectedTextures.containsKey(key)) {
                TextureData old = protectedTextures.put(key, new TextureData(id, image.getWidth(), image.getHeight()));
                if (old != null) destroyTexture(old.id());
            } else {
                textures.put(key, new TextureData(id, image.getWidth(), image.getHeight()));
            }
            loading.remove(key);
        });
    }

    /**
     * Starts a new tile request frame.
     */
    public void beginFrame() {

        frameRequests.clear();
    }

    /**
     * Requests a tile for the current frame.
     * Missing, cached, and in-flight tiles are ignored without mutating load state.
     *
     * @param key The tile key
     * @param priority Lower values are submitted first.
     */
    public void request(T key, int priority) {

        if (peek(key).isPresent()) return;
        if (isMissing(key, clock.getAsLong())) return;
        if (decodeAbandonedKeys.contains(key)) return;

        long now = System.currentTimeMillis();
        if (isTransportFailed(key, now)) return;
        if (loading.contains(key)) return;

        frameRequests.merge(key, priority, Math::min);
    }

    /**
     * Submits this frame's best visible tile requests within the per-provider budgets.
     */
    public void endFrame() {

        List<Map.Entry<T, Integer>> requests = frameRequests.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .toList();

        int submitted = 0;
        for (Map.Entry<T, Integer> entry : requests) {
            if (submitted >= MAX_SUBMITS_PER_FRAME || loading.size() >= MAX_IN_FLIGHT) break;

            T key = entry.getKey();
            if (peek(key).isPresent() || isMissing(key, clock.getAsLong()) || decodeAbandonedKeys.contains(key) || loading.contains(key)) continue;

            long now = System.currentTimeMillis();
            if (isTransportFailed(key, now)) continue;

            if (!loading.add(key)) continue;
            loadTile(key);
            submitted++;
        }

        frameRequests.clear();
        if (submitted == 0) pumpBootstrap();
    }

    /**
     * Read-only texture lookup. Does not initiate loads or update request state.
     *
     * @param key The tile key
     * @return An Optional containing the texture identifier if loaded.
     */
    public Optional<Identifier> peek(T key) {

        Identifier pinned = pinnedTextures.get(key);
        if (pinned != null) return Optional.of(pinned);

        TextureData protectedData = protectedTextures.get(key);
        if (protectedData != null) return Optional.of(protectedData.id());

        TextureData data = textures.getIfPresent(key);
        return data == null ? Optional.empty() : Optional.of(data.id());
    }

    /**
     * Cache a texture identifier without changing load state.
     *
     * @param key Tile key.
     * @param texture Texture identifier.
     */
    protected void cacheTexture(T key, Identifier texture) {

        textures.put(key, new TextureData(texture, 1, 1));
    }

    /**
     * Keeps currently drawn tile textures out of the LRU for the next frame, and returns
     * no-longer-drawn textures to normal byte-weighted eviction.
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
     * Adds coarse pyramid tiles to a background bootstrap queue. The queue is pumped one tile at a
     * time only when normal visible-tile loading is fully idle.
     *
     * @param keys Tile keys to load opportunistically.
     */
    public void enqueueBootstrapTiles(Collection<T> keys) {

        for (T key : keys) {
            if (peek(key).isPresent()) continue;
            if (decodeAbandonedKeys.contains(key)) continue;
            bootstrapRequests.add(key);
        }
    }

    /**
     * Pump one queued bootstrap tile if the normal request queue is idle.
     */
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
     * Configures which tile zoom should stay pinned outside the LRU texture cache.
     *
     * @param pinnedZoom tile zoom to pin.
     */
    public void setPinnedZoom(int pinnedZoom) {
        this.pinnedZoom = pinnedZoom;
    }

    /**
     * Marks a key as missing. The key will be retried after the negative-cache TTL expires.
     *
     * @param key missing tile key.
     */
    protected void markMissing(T key) {

        markMissing(key, DEFAULT_MISSING_TTL_MS);
    }

    /**
     * Marks a key as absent until its source-declared TTL expires.
     *
     * @param key   missing tile key.
     * @param ttlMs how long retry should be suppressed.
     */
    protected void markMissing(T key, long ttlMs) {

        missingKeys.put(key, clock.getAsLong() + Math.max(0L, ttlMs));
        clearLoading(key);
    }

    /**
     * Records a decode failure for a key. After three failures, the key is abandoned permanently.
     *
     * @param key tile key whose bytes could not be decoded.
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
     * Marks a key as transport-failed (IO/network error): this provider instance will not retry it.
     */
    protected void markTransportFailure(T key) {

        transportFailedKeys.put(key, System.currentTimeMillis());
        clearLoading(key);
    }

    /**
     * Clears transient async state for the given key.
     *
     * @param key The key whose in-flight/debounce state should be cleared.
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
     * Override the time source used for cache expiry checks.
     *
     * @param clock Clock supplier, or null to restore the system clock.
     */
    void setClock(LongSupplier clock) {

        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /**
     * Asynchronously loads a map tile for the given tile key.
     * <p>
     * When this method is invoked by {@link #endFrame()}, the key has already been added
     * to {@link #loading}. Implementors must <em>not</em> call {@code loading.add(key)} again;
     * doing so would always return {@code false} and silently abort the fetch.
     * </p>
     *
     * @param key The tile key identifying the tile to load.
     */
    protected abstract void loadTile(T key);

    /**
     * Resolve the in-memory texture cache budget in bytes.
     *
     * @return Configured budget, or the default budget when unset or invalid.
     */
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

        return textureCacheBudgetBytes() / 4L;
    }

    /**
     * Releases registered tile textures and clears transient async state.
     */
    public void close() {

        textures.invalidateAll();
        textures.cleanUp();
        pinnedTextures.forEach((ignoredKey, texture) -> destroyTexture(texture));
        pinnedTextures.clear();
        protectedTextures.forEach((ignoredKey, texture) -> destroyTexture(texture.id()));
        protectedTextures.clear();
        loading.clear();
        bootstrapRequests.clear();
        frameRequests.clear();
        missingKeys.invalidateAll();
        transportFailedKeys.clear();
        decodeFailureCounts.clear();
        decodeAbandonedKeys.clear();
    }

    /**
     * Destroys a dynamic texture on the client thread.
     *
     * @param texture The texture identifier to destroy.
     */
    protected void destroyTexture(Identifier texture) {

        if (texture == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> MinecraftClient.getInstance().getTextureManager().destroyTexture(texture));
    }

    /**
     * A cached tile texture with its decoded dimensions for byte-weighted eviction.
     *
     * @param id     The texture identifier registered in Minecraft.
     * @param width  Decoded texture width in pixels.
     * @param height Decoded texture height in pixels.
     */
    protected record TextureData(Identifier id, int width, int height) {

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
}
