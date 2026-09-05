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

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerSource;
import com.duom.ardamaps.core.data.map.cameras.PmTilesMapCamera;
import com.duom.ardamaps.core.data.map.providers.TileProvider;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PmTilesRendererTest {

    /**
     * Verify that shared fallbacks are deduplicated.
     */
    @Test
    void classifyTiles_deduplicatesSharedFallback() throws Exception {

        var camera = mockCamera();
        var provider = new TestTileProvider();
        var fallback = new PmTileKey(1, 0, 0);
        provider.publish(fallback, Identifier.of("ardamaps", "fallback"));
        var renderer = renderer(camera, provider);

        var plan = renderer.classifyTiles(Set.of(
                new PmTileKey(3, 0, 0),
                new PmTileKey(3, 1, 0),
                new PmTileKey(3, 0, 1),
                new PmTileKey(3, 1, 1)
        ), 1, false);

        assertTrue(plan.primaryTiles().isEmpty());
        assertEquals(List.of(fallback), List.copyOf(plan.fallbackMap().keySet()));
    }

    /**
     * Verify that fallback tiles stay separate from primaries.
     */
    @Test
    void classifyTiles_keepsFallbacksSeparateFromPrimaries() throws Exception {

        var camera = mockCamera();
        var provider = new TestTileProvider();
        var fallback = new PmTileKey(1, 0, 0);
        var primary = new PmTileKey(3, 2, 2);
        provider.publish(fallback, Identifier.of("ardamaps", "fallback"));
        provider.publish(primary, Identifier.of("ardamaps", "primary"));
        var renderer = renderer(camera, provider);

        var plan = renderer.classifyTiles(Set.of(new PmTileKey(3, 0, 0), primary), 1, false);

        assertEquals(List.of(fallback), List.copyOf(plan.fallbackMap().keySet()));
        assertEquals(List.of(primary), plan.primaryTiles().stream().map(PmTilesRenderer.TileDraw::key).toList());
    }

    /**
     * Verify that fallback drawing happens coarsest-first.
     */
    @Test
    void orderedFallbacks_drawsCoarsestFirst() throws Exception {

        var camera = mockCamera();
        var provider = new TestTileProvider();
        var renderer = renderer(camera, provider);

        // Insert fallbacks in a deliberately wrong (finer-before-coarser) order.
        var fine = new PmTileKey(3, 4, 4);
        var mid = new PmTileKey(2, 2, 2);
        var coarse = new PmTileKey(1, 1, 1);
        var fallbackMap = new java.util.LinkedHashMap<PmTileKey, PmTilesRenderer.TileDraw>();
        fallbackMap.put(fine, new PmTilesRenderer.TileDraw(Identifier.of("ardamaps", "fine"), 0, 0, fine));
        fallbackMap.put(coarse, new PmTilesRenderer.TileDraw(Identifier.of("ardamaps", "coarse"), 0, 0, coarse));
        fallbackMap.put(mid, new PmTilesRenderer.TileDraw(Identifier.of("ardamaps", "mid"), 0, 0, mid));

        var ordered = renderer.orderedFallbacks(new PmTilesRenderer.RenderPlan(List.of(), fallbackMap));

        // Ascending z => coarsest (lowest z) painted first, so finer fallbacks overpaint it.
        assertEquals(List.of(coarse, mid, fine), ordered.stream().map(PmTilesRenderer.TileDraw::key).toList());
    }

    /**
     * Verify that frame requests include prefetch rings and adjacent zoom levels.
     */
    @Test
    void requestTilesForFrame_requestsPrefetchRingAndFinerZoomStepWithoutDrawingRing() throws Exception {

        var camera = mockCamera();
        var provider = new TestTileProvider();
        var renderer = renderer(camera, provider);
        var fallback = new PmTileKey(1, 0, 0);
        var primary = new PmTileKey(3, 1, 1);
        var ring = new PmTileKey(3, 2, 1);
        var zoomStep = new PmTileKey(4, 2, 2);

        when(camera.getTileSourceClampedZoom()).thenReturn(3);
        when(camera.getVisibleTiles(1)).thenReturn(Set.of(fallback));
        when(camera.getRequestTiles(3, 1)).thenReturn(Set.of(primary, ring));
        when(camera.getVisibleTiles(4)).thenReturn(Set.of(zoomStep));
        when(camera.centerTileDistance(org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt()))
                .thenReturn(0);

        provider.publish(primary, Identifier.of("ardamaps", "primary"));
        provider.publish(ring, Identifier.of("ardamaps", "ring"));

        renderer.requestTilesForFrame(1, true);
        var plan = renderer.classifyTiles(Set.of(primary), 1, true);

        assertTrue(provider.requests.contains(new Request(fallback, TileProvider.VIEWPORT_FALLBACK_PRIORITY_BASE)));
        assertTrue(provider.requests.contains(new Request(ring, TileProvider.PRIMARY_PREFETCH_PRIORITY_BASE)));
        assertTrue(provider.requests.contains(new Request(zoomStep, TileProvider.ZOOM_STEP_PRIORITY_BASE)));
        assertEquals(List.of(primary), plan.primaryTiles().stream().map(PmTilesRenderer.TileDraw::key).toList());
    }

    /**
     * Verify that a bad archive path records a load error.
     */
    @Test
    void configure_badArchivePathSetsLoadError() throws Exception {

        var camera = mockCamera();
        var renderer = new PmTilesRenderer(camera, null, null);
        var layer = new MapLayerDefinition(
                "Broken Layer",
                MapLayerSource.PMTILES,
                false,
                8,
                null,
                1.0,
                1,
                3,
                1,
                14,
                256,
                1.0,
                "/definitely/not/a/real/archive.pmtiles",
                "",
                null);

        renderer.configure(layer, 1.0);

        Field field = PmTilesRenderer.class.getDeclaredField("loadError");
        field.setAccessible(true);
        assertEquals("Broken Layer", field.get(renderer));
    }

    /**
     * Create a renderer with a test provider injected by reflection.
     *
     * @param camera Camera under test.
     * @param provider Provider to inject.
     * @return Renderer configured for the test.
     * @throws Exception If reflection fails.
     */
    private static PmTilesRenderer renderer(PmTilesMapCamera camera, TestTileProvider provider) throws Exception {

        var renderer = new PmTilesRenderer(camera, null, null);
        Field field = PmTilesRenderer.class.getDeclaredField("tileProvider");
        field.setAccessible(true);
        field.set(renderer, provider);
        return renderer;
    }

    /**
     * Build a camera mock that returns deterministic screen positions.
     *
     * @return Mock camera.
     */
    private static PmTilesMapCamera mockCamera() {

        var camera = mock(PmTilesMapCamera.class);
        when(camera.tilePositionOnViewport(org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt()))
                .thenAnswer(invocation -> new Vec2d(invocation.getArgument(1, Integer.class) * 10.0, invocation.getArgument(2, Integer.class) * 10.0));
        return camera;
    }

    /**
     * Tile provider that records requests and allows direct cache publication.
     */
    private static final class TestTileProvider extends TileProvider<PmTileKey> {

        /** Recorded tile requests. */
        private final List<Request> requests = new ArrayList<>();

        /**
         * Create a provider with a fixed zoom range.
         */
        private TestTileProvider() {
            this.minZoom = 1;
            this.maxZoom = 4;
        }

        /**
         * Publish a texture for a tile key.
         *
         * @param key Tile key.
         * @param texture Texture identifier.
         */
        private void publish(PmTileKey key, Identifier texture) {
            cacheTexture(key, texture);
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
