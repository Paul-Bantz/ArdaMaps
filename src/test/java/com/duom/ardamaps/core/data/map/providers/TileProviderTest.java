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
import com.github.benmanes.caffeine.cache.Ticker;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TileProviderTest {

    /**
     * Verify that the per-frame submission cap is never exceeded.
     */
    @Test
    void endFrame_neverExceedsInFlightCeiling() {

        var provider = new TestTileProvider(false);

        for (int frame = 0; frame < 4; frame++) {
            provider.beginFrame();
            for (int i = 0; i < 20; i++) {
                provider.request(new TileKey(3, i, frame), i);
            }
            provider.endFrame();
            assertTrue(provider.loading.size() <= TileProvider.MAX_IN_FLIGHT);
        }

        assertEquals(TileProvider.MAX_IN_FLIGHT, provider.loading.size());
        assertEquals(TileProvider.MAX_IN_FLIGHT, provider.loadedKeys.size());
    }

    /**
     * Verify that at most the per-frame submission limit is sent.
     */
    @Test
    void endFrame_submitsAtMostPerFrameCap() {

        var provider = new TestTileProvider(false);

        provider.beginFrame();
        for (int i = 0; i < 20; i++) {
            provider.request(new TileKey(3, i, 0), i);
        }
        provider.endFrame();

        assertEquals(TileProvider.MAX_SUBMITS_PER_FRAME, provider.loadedKeys.size());
    }

    /**
     * Verify that lower priority values are submitted first.
     */
    @Test
    void endFrame_submitsLowestPriorityValuesFirst() {

        var provider = new TestTileProvider(true);
        var low = new TileKey(3, 1, 0);
        var high = new TileKey(3, 2, 0);
        var mid = new TileKey(3, 3, 0);

        provider.beginFrame();
        provider.request(low, 50);
        provider.request(high, 5);
        provider.request(mid, 20);
        provider.endFrame();

        assertEquals(List.of(high, mid, low), provider.loadedKeys);
    }

    /**
     * Verify that repeated requests keep the best priority for a key.
     */
    @Test
    void request_sameKeyKeepsBetterPriority() {

        var provider = new TestTileProvider(true);
        var merged = new TileKey(3, 1, 0);
        var first = new TileKey(3, 2, 0);

        provider.beginFrame();
        provider.request(merged, 100);
        provider.request(first, 20);
        provider.request(merged, 1);
        provider.endFrame();

        assertEquals(List.of(merged, first), provider.loadedKeys);
    }

    /**
     * Verify that unsubmitted candidates are dropped on the next frame.
     */
    @Test
    void beginFrame_dropsUnsubmittedCandidates() {

        var provider = new TestTileProvider(false);

        provider.beginFrame();
        for (int i = 0; i < 20; i++) {
            provider.request(new TileKey(3, i, 0), i);
        }
        provider.endFrame();

        provider.loading.clear();
        provider.endFrame();

        assertEquals(TileProvider.MAX_SUBMITS_PER_FRAME, provider.loadedKeys.size());
    }

    /**
     * Verify that peek is read-only.
     */
    @Test
    void peek_isPureReadOnlyProbe() {

        var provider = new TestTileProvider(true);
        var key = new TileKey(3, 1, 0);

        assertTrue(provider.peek(key).isEmpty());

        assertTrue(provider.loadedKeys.isEmpty());
        assertTrue(provider.loading.isEmpty());
        assertTrue(provider.frameRequests.isEmpty());
    }

    /**
     * Verify that pinned textures survive LRU churn.
     */
    @Test
    void pinnedTexturesSurviveLruChurn() {

        var provider = new TestTileProvider(true);
        var pinnedKey = new TileKey(1, 0, 0);
        var pinnedTexture = Identifier.of("ardamaps", "pinned");

        provider.setPinnedZoom(1);
        assertNotNull(pinnedTexture);
        provider.pinnedTextures.put(pinnedKey, pinnedTexture);
        for (int i = 0; i < TileProvider.MAX_CACHE_SIZE * 2; i++) {
            provider.publish(new TileKey(3, i, 0), Objects.requireNonNull(Identifier.of("ardamaps", "tile_" + i)));
        }
        provider.textures.cleanUp();

        assertEquals(pinnedTexture, provider.peek(pinnedKey).orElseThrow());
    }

    /**
     * Verify that drawn textures remain resident when speculative churn exceeds the budget.
     */
    @Test
    void protectDrawnTiles_keepsDrawnTextureResidentPastBudget() {

        String previousBudget = System.getProperty("ardamaps.textureCacheBudgetBytes");
        System.setProperty("ardamaps.textureCacheBudgetBytes", "500");
        try {
            var provider = new TestTileProvider(true);
            var drawn = new TileKey(3, 1, 1);
            provider.publish(drawn, Identifier.of("ardamaps", "drawn"), 10, 10);
            provider.protectDrawnTiles(Set.of(drawn));

            for (int i = 0; i < TileProvider.MAX_CACHE_SIZE; i++) {
                provider.publish(new TileKey(3, i + 10, 0), Identifier.of("ardamaps", "tile_" + i), 10, 10);
            }
            provider.textures.cleanUp();

            assertEquals(Identifier.of("ardamaps", "drawn"), provider.peek(drawn).orElseThrow());
        } finally {
            if (previousBudget == null) System.clearProperty("ardamaps.textureCacheBudgetBytes");
            else System.setProperty("ardamaps.textureCacheBudgetBytes", previousBudget);
        }
    }

    /**
     * Verify that an old drawn texture returns to the LRU when it is no longer drawn.
     */
    @Test
    void protectDrawnTiles_releasesOldDrawnTextureBackToLru() {

        String previousBudget = System.getProperty("ardamaps.textureCacheBudgetBytes");
        System.setProperty("ardamaps.textureCacheBudgetBytes", "500");
        try {
            var provider = new TestTileProvider(true);
            var oldDrawn = new TileKey(3, 1, 1);
            var currentDrawn = new TileKey(3, 2, 2);
            provider.publish(oldDrawn, Identifier.of("ardamaps", "old"), 10, 10);
            provider.publish(currentDrawn, Identifier.of("ardamaps", "current"), 10, 10);

            provider.protectDrawnTiles(Set.of(oldDrawn));
            provider.protectDrawnTiles(Set.of(currentDrawn));

            for (int i = 0; i < TileProvider.MAX_CACHE_SIZE; i++) {
                provider.publish(new TileKey(3, i + 100, 0), Identifier.of("ardamaps", "churn_" + i), 10, 10);
            }
            provider.textures.cleanUp();

            assertTrue(provider.peek(oldDrawn).isEmpty());
            assertEquals(Identifier.of("ardamaps", "current"), provider.peek(currentDrawn).orElseThrow());
        } finally {
            if (previousBudget == null) System.clearProperty("ardamaps.textureCacheBudgetBytes");
            else System.setProperty("ardamaps.textureCacheBudgetBytes", previousBudget);
        }
    }

    /**
     * Verify that moving a texture out of the LRU does not destroy the GL texture.
     */
    @Test
    void protectDrawnTiles_moveDoesNotDestroyTexture() {

        var provider = new TestTileProvider(true);
        var drawn = new TileKey(3, 4, 4);
        provider.publish(drawn, Identifier.of("ardamaps", "drawn"), 10, 10);

        provider.protectDrawnTiles(Set.of(drawn));

        assertTrue(provider.destroyedTextures.isEmpty());
        assertEquals(Identifier.of("ardamaps", "drawn"), provider.peek(drawn).orElseThrow());
    }

    /**
     * Verify that the negative cache suppresses retries until its TTL expires.
     */
    @Test
    void negativeCacheSuppressesUntilTtlExpires() {

        var clock = new FakeClock();
        var provider = new TestTileProvider(true, new FakeTicker());
        provider.setClock(clock::millis);
        var key = new TileKey(3, 1, 0);

        provider.markMissing(key);

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertTrue(provider.loadedKeys.isEmpty());

        clock.advance();
        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertEquals(List.of(key), provider.loadedKeys);
    }

    /**
     * Verify that decode-abandoned keys do not expire with the missing TTL.
     */
    @Test
    void decodeAbandonedKeysDoNotExpireWithMissingTtl() {

        var clock = new FakeClock();
        var provider = new TestTileProvider(true);
        provider.setClock(clock::millis);
        var key = new TileKey(3, 5, 5);

        provider.markDecodeFailure(key);
        provider.markDecodeFailure(key);
        provider.markDecodeFailure(key);

        clock.advance();
        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertTrue(provider.loadedKeys.isEmpty());
    }

    /**
     * Verify that transport failures are suppressed only during the cooldown window.
     */
    @Test
    void transportFailureSuppressesOnlyDuringCooldown() {

        var provider = new TestTileProvider(true);
        var key = new TileKey(2, 9, 9);

        provider.markTransportFailure(key);
        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertTrue(provider.loadedKeys.isEmpty());

        provider.transportFailedKeys.put(key, System.currentTimeMillis() - TileProvider.TRANSPORT_FAILURE_TTL_MS - 1);
        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertEquals(List.of(key), provider.loadedKeys);
    }

    /**
     * Verify that bootstrap pumping waits for viewport loading to become idle.
     */
    @Test
    void bootstrapPumpYieldsWhileViewportLoadIsInFlight() {

        var provider = new TestTileProvider(false);
        var visible = new TileKey(3, 1, 1);
        var bootstrap = new TileKey(1, 0, 0);

        provider.enqueueBootstrapTiles(List.of(bootstrap));
        provider.loading.add(visible);
        provider.endFrame();

        assertTrue(provider.loadedKeys.isEmpty());

        provider.loading.clear();
        provider.endFrame();

        assertEquals(List.of(bootstrap), provider.loadedKeys);
    }

    /**
     * Verify that texture eviction is weighted by decoded byte size.
     */
    @Test
    void textureCacheEvictsByByteWeight() {

        String previousBudget = System.getProperty("ardamaps.textureCacheBudgetBytes");
        System.setProperty("ardamaps.textureCacheBudgetBytes", "500");
        try {
            var provider = new TestTileProvider(true);

            provider.publish(new TileKey(3, 1, 1), Identifier.of("ardamaps", "large_a"), 10, 10);
            provider.publish(new TileKey(3, 2, 2), Identifier.of("ardamaps", "large_b"), 10, 10);
            provider.textures.cleanUp();

            assertTrue(provider.textures.estimatedSize() < 2);
        } finally {
            if (previousBudget == null) System.clearProperty("ardamaps.textureCacheBudgetBytes");
            else System.setProperty("ardamaps.textureCacheBudgetBytes", previousBudget);
        }
    }

    /**
     * Test tile provider that records submitted keys.
     */
    private static final class TestTileProvider extends TileProvider<TileKey> {

        /** Whether submitted loads should complete immediately. */
        private final boolean completeImmediately;
        /** Keys submitted to the provider. */
        private final List<TileKey> loadedKeys = new ArrayList<>();
        /** Texture ids destroyed by the provider. */
        private final List<Identifier> destroyedTextures = new ArrayList<>();

        /**
         * Create a provider with the given completion behavior.
         *
         * @param completeImmediately Whether load calls should clear in-flight state immediately.
         */
        private TestTileProvider(boolean completeImmediately) {
            this.completeImmediately = completeImmediately;
        }

        /**
         * Create a provider with a custom ticker.
         *
         * @param completeImmediately Whether load calls should clear in-flight state immediately.
         * @param ticker Cache ticker.
         */
        private TestTileProvider(boolean completeImmediately, Ticker ticker) {
            super(ticker);
            this.completeImmediately = completeImmediately;
        }

        /**
         * Record the key as loaded.
         *
         * @param key Tile key to record.
         */
        @Override
        public void loadTile(TileKey key) {
            loadedKeys.add(key);
            if (completeImmediately) clearLoading(key);
        }

        @Override
        protected void destroyTexture(Identifier texture) {
            if (texture != null) destroyedTextures.add(texture);
        }

        /**
         * Publish a 1x1 texture for the given key.
         *
         * @param key Tile key.
         * @param texture Texture identifier.
         */
        private void publish(TileKey key, Identifier texture) {
            publish(key, texture, 1, 1);
        }

        /**
         * Publish a texture with explicit dimensions.
         *
         * @param key Tile key.
         * @param texture Texture identifier.
         * @param width Texture width.
         * @param height Texture height.
         */
        private void publish(TileKey key, Identifier texture, int width, int height) {
            textures.put(key, new TextureData(texture, width, height));
        }
    }

    /**
     * Minimal ticker used to stabilize cache timing in tests.
     */
    private static final class FakeTicker implements Ticker {

        /**
         * Return a stable tick value.
         *
         * @return Constant ticker value.
         */
        @Override
        public long read() {
            return 1;
        }
    }

    /**
     * Simple monotonic clock used for TTL tests.
     */
    private static final class FakeClock {

        private long millis;

        /**
         * Return the current synthetic time.
         *
         * @return Synthetic epoch milliseconds.
         */
        private long millis() {
            return millis;
        }

        /**
         * Advance the clock beyond the missing-TTL window.
         */
        private void advance() {
            this.millis += 14400001L;
        }
    }
}
