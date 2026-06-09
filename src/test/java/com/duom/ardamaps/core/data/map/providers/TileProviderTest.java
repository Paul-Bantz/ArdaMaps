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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TileProvider#get(TileKey)} debounce and transport-failure behaviour.
 * <p>
 * These tests verify that:
 * <ul>
 *   <li>The first request for a key is buffered (not loaded immediately).</li>
 *   <li>A second request inside the debounce window triggers exactly one load.</li>
 *   <li>Expired buffered requests are pruned and treated as a fresh first request.</li>
 *   <li>Keys marked as transport-failed are never retried for the lifetime of the provider instance.</li>
 * </ul>
 */
class TileProviderTest {

    /**
     * Verifies that the very first {@code get()} call for a key only records it in the
     * debounce buffer and does not start loading.
     */
    @Test
    void get_firstRequest_buffersWithoutLoading() {

        var provider = new TestTileProvider();
        var key = new TileKey(3, 10, 20);

        provider.get(key);

        assertEquals(0, provider.loadCalls, "First request should only buffer");
        assertTrue(provider.pendingRequests.containsKey(key), "First request should be remembered");
    }

    /**
     * Verifies that a second {@code get()} call for the same key, issued within the debounce
     * window, promotes that key to loading exactly once and removes it from the pending buffer.
     */
    @Test
    void get_secondRequestWithinDebounceWindow_loadsOnce() {

        var provider = new TestTileProvider();
        var key = new TileKey(4, 1, 2);

        provider.get(key);
        provider.get(key);

        assertEquals(1, provider.loadCalls, "Second request within debounce should trigger loading once");
        assertFalse(provider.pendingRequests.containsKey(key), "Key should be removed from pending once promoted");
    }

    /**
     * Verifies that pending entries older than {@link TileProvider#REQUEST_BUFFER_TTL_MS}
     * are pruned, so a later request is treated as a new first request (buffer-only).
     */
    @Test
    void get_stalePendingRequest_isPrunedAndBufferedAgain() {

        var provider = new TestTileProvider();
        var key = new TileKey(5, 3, 7);
        provider.pendingRequests.put(key, System.currentTimeMillis() - TileProvider.REQUEST_BUFFER_TTL_MS - 1);

        provider.get(key);

        assertEquals(0, provider.loadCalls, "Expired pending key should not trigger loading");
        assertTrue(provider.pendingRequests.containsKey(key), "Expired key should be re-buffered as first request");
    }

    /**
     * Verifies that keys marked with transport failure are permanently skipped by {@code get()}
     * for the current provider instance and never re-enter the debounce pipeline.
     */
    @Test
    void get_transportFailure_doesNotRetry() {

        var provider = new TestTileProvider();
        var key = new TileKey(2, 9, 9);

        provider.markTransportFailure(key);

        provider.get(key);
        provider.get(key);

        assertEquals(0, provider.loadCalls, "Transport-failed keys must never be retried for this provider instance");
        assertFalse(provider.pendingRequests.containsKey(key), "Transport-failed keys should not enter debounce buffer");
    }

    /**
     * Minimal test double for {@link TileProvider} that counts load attempts.
     * <p>
     * The implementation immediately clears the in-flight marker so repeated
     * tests can observe load scheduling behaviour deterministically.
     */
    private static final class TestTileProvider extends TileProvider<TileKey> {

        /** Number of times {@link #loadTile(TileKey)} was invoked. */
        private int loadCalls;

        /**
         * Records a load invocation for assertions.
         *
         * @param key tile key requested for loading
         */
        @Override
        public void loadTile(TileKey key) {
            loadCalls++;
            loading.remove(key);
        }
    }
}