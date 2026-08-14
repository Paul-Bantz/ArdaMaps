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
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests PMTiles renderer classification before draw submission. Rendering itself is client-bound,
 * but the correctness bugs were in how primary and fallback draw work was selected and ordered.
 */
class PmTilesRendererTest {

    /**
     * Verifies many primary tiles resolving to one loaded coarse ancestor produce only one fallback
     * draw entry.
     */
    @Test
    void classifyTiles_deduplicatesSharedFallbackTile() throws Exception {

        var renderer = rendererWithProvider(providerWithTexture(new PmTileKey(2, 0, 0), ModConstants.modId("test/fallback")));

        var tiles = new LinkedHashSet<PmTileKey>();
        tiles.add(new PmTileKey(4, 0, 0));
        tiles.add(new PmTileKey(4, 1, 0));
        tiles.add(new PmTileKey(4, 0, 1));
        tiles.add(new PmTileKey(4, 1, 1));

        PmTilesRenderer.RenderPlan plan = renderer.classifyTiles(tiles, 2, true);

        assertEquals(0, plan.primaryTiles().size());
        assertEquals(1, plan.fallbackMap().size(), "Shared coarse PMTiles ancestor should be drawn once");
        assertEquals(new PmTileKey(2, 0, 0), plan.fallbackMap().keySet().iterator().next());
    }

    /**
     * Verifies fallback draw work is separated ahead of primary draw work and remains deterministic
     * across repeated classifications.
     */
    @Test
    void classifyTiles_separatesFallbacksBeforePrimariesDeterministically() throws Exception {

        PmTileKey fallbackKey = new PmTileKey(2, 0, 0);
        PmTileKey primaryKey = new PmTileKey(4, 3, 3);
        var provider = new TestTileProvider();
        provider.put(fallbackKey, ModConstants.modId("test/fallback"));
        provider.put(primaryKey, ModConstants.modId("test/primary"));

        var renderer = rendererWithProvider(provider);
        var tiles = new LinkedHashSet<>(List.of(
                new PmTileKey(4, 0, 0),
                primaryKey,
                new PmTileKey(4, 1, 1)
        ));

        List<PmTileKey> first = plannedDrawOrder(renderer.classifyTiles(tiles, 2, true));
        List<PmTileKey> second = plannedDrawOrder(renderer.classifyTiles(tiles, 2, true));

        assertEquals(List.of(fallbackKey, primaryKey), first);
        assertEquals(first, second, "PMTiles draw ordering should not depend on hash iteration side effects");
    }

    /**
     * Verifies a bad PMTiles archive path records a layer-specific load error instead of leaving
     * the renderer in an indistinguishable loading state forever.
     */
    @Test
    void configure_badArchivePath_setsLoadError() throws Exception {

        PmTilesMapCamera camera = mock(PmTilesMapCamera.class);
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
                "/tmp/ardamaps-missing-test-archive.pmtiles",
                "",
                null);

        assertNull(loadError(renderer));

        renderer.configure(layer, 1.0);

        assertEquals("Broken Layer", loadError(renderer));
    }

    /**
     * Documents the Phase 4 PMTiles zoom-step direction: when settled, request one finer viewport
     * level ({@code primaryZ + 1}), not the BlueMap direction.
     */
    @Test
    void requestTilesForFrame_pmtilesZoomStepTargetsPrimaryPlusOne() throws Exception {

        var provider = new TestTileProvider();
        provider.setMaxZoom();
        PmTilesMapCamera camera = mock(PmTilesMapCamera.class);
        when(camera.getVisibleTiles(2)).thenReturn(Set.of(new PmTileKey(2, 0, 0)));
        when(camera.getTileSourceClampedZoom()).thenReturn(5);
        when(camera.getRequestTiles(5, 1)).thenReturn(Set.of(new PmTileKey(5, 3, 3)));
        when(camera.getVisibleTiles(6)).thenReturn(Set.of(new PmTileKey(6, 7, 7)));
        when(camera.centerTileDistance(anyInt(), anyInt(), anyInt())).thenReturn(0);

        var renderer = rendererWithProvider(camera, provider);

        renderer.requestTilesForFrame(2, true);

        assertEquals(TileProvider.ZOOM_STEP_PRIORITY_BASE, provider.requested.get(new PmTileKey(6, 7, 7)));
    }

    /**
     * Oversized PMTiles zoom-step viewports are skipped so speculative finer tiles cannot evict
     * visible primary tiles.
     */
    @Test
    void requestTilesForFrame_pmtilesZoomStepSkippedWhenOverBudget() throws Exception {

        var provider = new TestTileProvider();
        provider.setMaxZoom();
        PmTilesMapCamera camera = mock(PmTilesMapCamera.class);
        when(camera.getVisibleTiles(2)).thenReturn(Set.of(new PmTileKey(2, 0, 0)));
        when(camera.getTileSourceClampedZoom()).thenReturn(5);
        when(camera.getRequestTiles(5, 1)).thenReturn(Set.of(new PmTileKey(5, 3, 3)));
        when(camera.getVisibleTiles(6)).thenReturn(manyTiles());
        when(camera.centerTileDistance(anyInt(), anyInt(), anyInt())).thenReturn(0);

        var renderer = rendererWithProvider(camera, provider);

        renderer.requestTilesForFrame(2, true);

        assertTrue(provider.requested.keySet().stream().noneMatch(key -> key.z == 6),
                "Oversized PMTiles zoom-step set should not be requested");
        assertTrue(provider.requested.containsKey(new PmTileKey(5, 3, 3)),
                "Same-zoom prefetch ring should still be requested");
        assertTrue(provider.requested.containsKey(new PmTileKey(2, 0, 0)),
                "Coarse fallback viewport should still be requested");
    }

    /**
     * Ring tiles are low-priority request candidates but are not part of the draw classification
     * unless they are in the viewport-visible set.
     */
    @Test
    void requestTilesForFrame_prefetchRingIsRequestedButNotDrawn() throws Exception {

        PmTileKey visible = new PmTileKey(5, 3, 3);
        PmTileKey ring = new PmTileKey(5, 4, 3);
        var provider = new TestTileProvider();
        PmTilesMapCamera camera = mock(PmTilesMapCamera.class);
        when(camera.getVisibleTiles(2)).thenReturn(Set.of(new PmTileKey(2, 0, 0)));
        when(camera.getTileSourceClampedZoom()).thenReturn(5);
        when(camera.getRequestTiles(5, 1)).thenReturn(new LinkedHashSet<>(List.of(visible, ring)));
        when(camera.getVisibleTiles(6)).thenReturn(Set.of());
        when(camera.centerTileDistance(anyInt(), anyInt(), anyInt())).thenReturn(0);
        when(camera.tilePositionOnViewport(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                new Vec2d(invocation.getArgument(0, Integer.class) * 256.0,
                        invocation.getArgument(1, Integer.class) * 256.0));

        var renderer = rendererWithProvider(camera, provider);

        renderer.requestTilesForFrame(2, true);
        PmTilesRenderer.RenderPlan plan = renderer.classifyTiles(Set.of(visible), 2, true);

        assertEquals(TileProvider.PRIMARY_PREFETCH_PRIORITY_BASE, provider.requested.get(ring));
        assertTrue(plan.primaryTiles().stream().noneMatch(tile -> tile.key().equals(ring)));
        assertFalse(plan.fallbackMap().containsKey(ring));
    }

    private static List<PmTileKey> plannedDrawOrder(PmTilesRenderer.RenderPlan plan) {

        List<PmTileKey> order = new ArrayList<>();
        plan.fallbackMap().values().forEach(tile -> order.add(tile.key()));
        plan.primaryTiles().forEach(tile -> order.add(tile.key()));
        return order;
    }

    private static String loadError(PmTilesRenderer renderer) throws Exception {

        Field field = PmTilesRenderer.class.getDeclaredField("loadError");
        field.setAccessible(true);
        return (String) field.get(renderer);
    }

    private static PmTilesRenderer rendererWithProvider(TestTileProvider provider) throws Exception {

        PmTilesMapCamera camera = mock(PmTilesMapCamera.class);
        when(camera.centerTileDistance(anyInt(), anyInt(), anyInt())).thenReturn(0);
        when(camera.tilePositionOnViewport(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                new Vec2d(invocation.getArgument(0, Integer.class) * 256.0,
                        invocation.getArgument(1, Integer.class) * 256.0));

        var renderer = new PmTilesRenderer(camera, null, null);
        Field field = PmTilesRenderer.class.getDeclaredField("tileProvider");
        field.setAccessible(true);
        field.set(renderer, provider);
        return renderer;
    }

    private static PmTilesRenderer rendererWithProvider(PmTilesMapCamera camera, TestTileProvider provider) throws Exception {

        var renderer = new PmTilesRenderer(camera, null, null);
        Field field = PmTilesRenderer.class.getDeclaredField("tileProvider");
        field.setAccessible(true);
        field.set(renderer, provider);
        return renderer;
    }

    private static TestTileProvider providerWithTexture(PmTileKey key, Identifier texture) {

        var provider = new TestTileProvider();
        provider.put(key, texture);
        return provider;
    }

    private static Set<PmTileKey> manyTiles() {

        Set<PmTileKey> keys = new LinkedHashSet<>();
        for (int i = 0; i < 200; i++) {
            keys.add(new PmTileKey(6, i, 0));
        }
        return keys;
    }

    private static final class TestTileProvider extends TileProvider<PmTileKey> {

        private final Map<PmTileKey, Integer> requested = new HashMap<>();

        private void setMaxZoom() {

            this.maxZoom = 6;
        }

        private void put(PmTileKey key, Identifier texture) {

            cacheTexture(key, texture);
        }

        @Override
        public Optional<Identifier> request(PmTileKey key, int priority) {

            requested.merge(key, priority, Math::min);
            return Optional.empty();
        }

        @Override
        protected void loadTile(PmTileKey key) {
            clearLoading(key);
        }
    }
}
