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

package com.duom.ardamaps.gui.map.rendering;

import com.duom.ardamaps.core.data.map.cameras.BlueMapCamera;
import com.duom.ardamaps.core.data.map.providers.BlueMapTileProvider;
import com.duom.ardamaps.core.data.map.providers.TileProvider;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlueMapRendererTest {

    /**
     * Verify that BlueMap requests the zoom-step viewport one level finer than the primary zoom.
     */
    @Test
    void requestTilesForFrame_blueMapZoomStepTargetsPrimaryMinusOne() throws Exception {

        var camera = mock(BlueMapCamera.class);
        var provider = new TestBlueMapTileProvider();
        var renderer = renderer(camera, provider);
        var fallback = new PmTileKey(3, 0, 0);
        var primary = new PmTileKey(2, 1, 1);
        var zoomStep = new PmTileKey(1, 2, 2);

        when(camera.getVisibleTiles(3)).thenReturn(Set.of(fallback));
        when(camera.getRequestTiles(2, 1)).thenReturn(Set.of(primary));
        when(camera.getVisibleTiles(1)).thenReturn(Set.of(zoomStep));
        when(camera.centerTileDistance(org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt()))
                .thenReturn(0);

        renderer.requestTilesForFrame(3, 2, true);

        assertTrue(provider.requests.contains(new Request(zoomStep, TileProvider.ZOOM_STEP_PRIORITY_BASE)));
        assertTrue(provider.requests.contains(new Request(fallback, TileProvider.VIEWPORT_FALLBACK_PRIORITY_BASE)));
        assertTrue(provider.requests.contains(new Request(primary, TileProvider.PRIMARY_PREFETCH_PRIORITY_BASE)));
    }

    /**
     * Verify that oversized BlueMap zoom-step viewports are skipped.
     */
    @Test
    void requestTilesForFrame_blueMapZoomStepSkippedWhenOverBudget() throws Exception {

        var camera = mock(BlueMapCamera.class);
        var provider = new TestBlueMapTileProvider();
        var renderer = renderer(camera, provider);
        var fallback = new PmTileKey(3, 0, 0);
        var primary = new PmTileKey(2, 1, 1);

        when(camera.getVisibleTiles(3)).thenReturn(Set.of(fallback));
        when(camera.getRequestTiles(2, 1)).thenReturn(Set.of(primary));
        when(camera.getVisibleTiles(1)).thenReturn(manyTiles());
        when(camera.centerTileDistance(org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt()))
                .thenReturn(0);

        renderer.requestTilesForFrame(3, 2, true);

        assertTrue(provider.requests.stream().noneMatch(request -> request.key().z == 1));
        assertTrue(provider.requests.contains(new Request(fallback, TileProvider.VIEWPORT_FALLBACK_PRIORITY_BASE)));
        assertTrue(provider.requests.contains(new Request(primary, TileProvider.PRIMARY_PREFETCH_PRIORITY_BASE)));
    }

    /**
     * Create a renderer with a test provider injected by reflection.
     *
     * @param camera Camera under test.
     * @param provider Provider to inject.
     * @return Renderer configured for the test.
     * @throws Exception If reflection fails.
     */
    private static BlueMapRenderer renderer(BlueMapCamera camera, TestBlueMapTileProvider provider) throws Exception {

        var renderer = new BlueMapRenderer(camera, null, null);
        Field field = BlueMapRenderer.class.getDeclaredField("provider");
        field.setAccessible(true);
        field.set(renderer, provider);
        return renderer;
    }

    private static Set<PmTileKey> manyTiles() {

        Set<PmTileKey> keys = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 200; i++) {
            keys.add(new PmTileKey(1, i, 0));
        }
        return keys;
    }

    /**
     * Tile provider that records request priorities.
     */
    private static final class TestBlueMapTileProvider extends BlueMapTileProvider {

        /** Recorded tile requests. */
        private final List<Request> requests = new ArrayList<>();

        /**
         * Create a provider with a fixed zoom range.
         */
        private TestBlueMapTileProvider() {
            super("", 1, 4);
        }

        /**
         * Record the request before delegating to the base provider.
         *
         * @param key Tile key.
         * @param priority Request priority.
         */
        @Override
        public void request(PmTileKey key, int priority) {
            requests.add(new Request(key, priority));
            super.request(key, priority);
        }

        /**
         * Complete load requests immediately for tests.
         *
         * @param key Tile key.
         */
        @Override
        protected void loadTile(PmTileKey key) {
            clearLoading(key);
        }
    }

    /**
     * Recorded request tuple.
     */
    private record Request(PmTileKey key, int priority) {

    }
}
