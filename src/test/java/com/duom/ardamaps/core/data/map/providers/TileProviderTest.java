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
 * Unit tests for {@link TileProvider}'s frame-scoped, bounded, prioritised load dispatch, and its
 * transport-failure/missing-key suppression.
 * <p>
 * These tests verify that:
 * <ul>
 *   <li>{@link TileProvider#request} registers a candidate but never loads synchronously.</li>
 *   <li>{@link TileProvider#endFrame()} submits candidates in priority order, up to
 *       {@link TileProvider#MAX_SUBMITS_PER_FRAME} per call and {@link TileProvider#MAX_IN_FLIGHT}
 *       concurrently.</li>
 *   <li>Candidates not submitted in a frame are dropped, not deferred.</li>
 *   <li>{@link TileProvider#peek} never enqueues or loads.</li>
 *   <li>Keys marked as transport-failed are skipped only during the retry cooldown.</li>
 *   <li>Keys marked missing are never retried.</li>
 * </ul>
 */
class TileProviderTest {

    /**
     * Verifies that {@code request()} only records a candidate for the frame and never loads
     * synchronously - the fetch is only started by a subsequent {@code endFrame()}.
     */
    @Test
    void request_doesNotLoadSynchronously() {

        var provider = new TestTileProvider();
        var key = new TileKey(3, 10, 20);

        provider.beginFrame();
        provider.request(key, 0);

        assertEquals(0, provider.loadCalls, "request() must not load synchronously");
    }

    /**
     * Verifies that a candidate registered via {@code request()} is actually submitted once
     * {@code endFrame()} runs.
     */
    @Test
    void endFrame_submitsRegisteredCandidate() {

        var provider = new TestTileProvider();
        var key = new TileKey(4, 1, 2);

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertEquals(1, provider.loadCalls, "endFrame() should submit the registered candidate");
    }

    /**
     * Verifies that {@code endFrame()} submits candidates in ascending priority order (lower value
     * = more urgent) and stops once {@link TileProvider#MAX_SUBMITS_PER_FRAME} have been submitted.
     */
    @Test
    void endFrame_submitsInPriorityOrder_upToPerFrameCap() {

        var provider = new TestTileProvider();

        provider.beginFrame();
        // Register more candidates than MAX_SUBMITS_PER_FRAME, in reverse priority order.
        for (int i = TileProvider.MAX_SUBMITS_PER_FRAME + 2; i >= 0; i--) {
            provider.request(new TileKey(1, i, 0), i);
        }
        provider.endFrame();

        assertEquals(TileProvider.MAX_SUBMITS_PER_FRAME, provider.loadCalls,
                "Only MAX_SUBMITS_PER_FRAME candidates should be submitted in one frame");

        for (int i = 0; i < TileProvider.MAX_SUBMITS_PER_FRAME; i++) {
            assertTrue(provider.loadedKeys.contains(new TileKey(1, i, 0)),
                    "The lowest-priority-value candidates should be the ones submitted");
        }
    }

    /**
     * Verifies that {@code endFrame()} never lets the number of concurrently in-flight loads
     * exceed {@link TileProvider#MAX_IN_FLIGHT}, even across repeated frames.
     */
    @Test
    void endFrame_neverExceedsMaxInFlight() {

        var provider = new NonCompletingTileProvider();

        for (int frame = 0; frame < 5; frame++) {
            provider.beginFrame();
            for (int i = 0; i < TileProvider.MAX_SUBMITS_PER_FRAME; i++) {
                provider.request(new TileKey(1, frame * 10 + i, 0), 0);
            }
            provider.endFrame();
            assertTrue(provider.loading.size() <= TileProvider.MAX_IN_FLIGHT,
                    "In-flight count must never exceed MAX_IN_FLIGHT");
        }
    }

    /**
     * Verifies that a candidate registered but not submitted (because the frame budget was
     * exhausted) is dropped rather than retried automatically on a later {@code endFrame()} - it
     * must be re-registered via {@code request()} to be considered again. This is the cancellation
     * mechanism for tiles that scroll off screen before they're ever submitted.
     */
    @Test
    void unsubmittedCandidate_isDroppedNotDeferred() {

        var provider = new TestTileProvider();
        var overflowKey = new TileKey(1, 999, 0);

        provider.beginFrame();
        for (int i = 0; i < TileProvider.MAX_SUBMITS_PER_FRAME; i++) {
            provider.request(new TileKey(1, i, 0), 0);
        }
        provider.request(overflowKey, 1); // lower priority than the others -> not submitted
        provider.endFrame();

        assertFalse(provider.loadedKeys.contains(overflowKey), "Overflow candidate should not have loaded");

        // A later frame that does not re-request it must not load it either.
        provider.beginFrame();
        provider.endFrame();

        assertFalse(provider.loadedKeys.contains(overflowKey), "Dropped candidate must not be retried automatically");
    }

    /**
     * Verifies that keys marked with transport failure are skipped during the retry cooldown.
     */
    @Test
    void request_recentTransportFailure_doesNotRetry() {

        var provider = new TestTileProvider();
        var key = new TileKey(2, 9, 9);

        provider.markTransportFailure(key);

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertEquals(0, provider.loadCalls, "Recent transport-failed keys must not be retried");
    }

    /**
     * Verifies that transport-failure suppression expires, so a transient network blip does not blacklist a tile forever.
     */
    @Test
    void request_expiredTransportFailure_reentersPipeline() {

        var provider = new TestTileProvider();
        var key = new TileKey(2, 9, 10);
        provider.transportFailedKeys.put(key, System.currentTimeMillis() - TileProvider.TRANSPORT_FAILURE_TTL_MS - 1);

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertEquals(1, provider.loadCalls, "Expired failure should be treated as a fresh candidate");
    }

    /**
     * Verifies {@link TileProvider#peek(TileKey)} never enqueues or triggers a load, even repeatedly.
     */
    @Test
    void peek_neverTriggersLoading() {

        var provider = new TestTileProvider();
        var key = new TileKey(6, 1, 1);

        provider.peek(key);
        provider.peek(key);
        provider.peek(key);
        provider.beginFrame();
        provider.endFrame();

        assertEquals(0, provider.loadCalls, "peek() must never trigger a load");
    }

    /**
     * Verifies a key marked missing is skipped by {@code request()} without expiring, unlike a
     * transport failure.
     */
    @Test
    void markMissing_suppressesFurtherRequests() {

        var provider = new TestTileProvider();
        var key = new TileKey(7, 2, 3);

        provider.markMissing(key);

        provider.beginFrame();
        provider.request(key, 0);
        provider.endFrame();

        assertEquals(0, provider.loadCalls, "Keys marked missing must never be retried");
        assertTrue(provider.peek(key).isEmpty(), "A missing key should not have a cached texture");
    }

    /**
     * Minimal test double for {@link TileProvider} that counts load attempts and immediately
     * clears the in-flight marker so repeated tests can observe load scheduling deterministically.
     */
    private static class TestTileProvider extends TileProvider<TileKey> {

        /** Number of times {@link #loadTile(TileKey)} was invoked. */
        private int loadCalls;

        /** Keys that have been passed to {@link #loadTile(TileKey)}. */
        private final java.util.Set<TileKey> loadedKeys = new java.util.HashSet<>();

        @Override
        public void loadTile(TileKey key) {
            loadCalls++;
            loadedKeys.add(key);
            loading.remove(key);
        }
    }

    /**
     * Test double whose loads never complete (does not clear {@link TileProvider#loading}), used
     * to verify the in-flight budget is respected across multiple frames.
     */
    private static final class NonCompletingTileProvider extends TileProvider<TileKey> {

        @Override
        public void loadTile(TileKey key) {
            // Intentionally left in-flight.
        }
    }
}
