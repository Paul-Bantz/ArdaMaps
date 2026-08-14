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
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests BlueMap request scheduling decisions without invoking GUI rendering.
 */
class BlueMapRendererTest {

    /**
     * Documents the Phase 4 BlueMap zoom-step direction: when settled, request one finer LOD
     * viewport level ({@code primaryZ - 1}), not the PMTiles direction.
     */
    @Test
    void requestTilesForFrame_blueMapZoomStepTargetsPrimaryMinusOne() throws Exception {

        var provider = new TestBlueMapTileProvider();
        BlueMapCamera camera = mock(BlueMapCamera.class);
        when(camera.getVisibleTiles(3)).thenReturn(Set.of(new PmTileKey(3, 0, 0)));
        when(camera.getRequestTiles(2, 1)).thenReturn(Set.of(new PmTileKey(2, 1, 1)));
        when(camera.getVisibleTiles(1)).thenReturn(Set.of(new PmTileKey(1, 2, 2)));
        when(camera.centerTileDistance(anyInt(), anyInt(), anyInt())).thenReturn(0);

        var renderer = rendererWithProvider(camera, provider);

        renderer.requestTilesForFrame(3, 2, true);

        assertEquals(TileProvider.ZOOM_STEP_PRIORITY_BASE, provider.requested.get(new PmTileKey(1, 2, 2)));
    }

    /**
     * Oversized finer BlueMap LOD viewports are skipped so speculative work cannot evict visible
     * primary tiles; the useful same-LOD ring and coarse fallback requests remain.
     */
    @Test
    void requestTilesForFrame_blueMapZoomStepSkippedWhenOverBudget() throws Exception {

        var provider = new TestBlueMapTileProvider();
        BlueMapCamera camera = mock(BlueMapCamera.class);
        when(camera.getVisibleTiles(3)).thenReturn(Set.of(new PmTileKey(3, 0, 0)));
        when(camera.getRequestTiles(2, 1)).thenReturn(Set.of(new PmTileKey(2, 1, 1)));
        when(camera.getVisibleTiles(1)).thenReturn(manyTiles());
        when(camera.centerTileDistance(anyInt(), anyInt(), anyInt())).thenReturn(0);

        var renderer = rendererWithProvider(camera, provider);

        renderer.requestTilesForFrame(3, 2, true);

        assertTrue(provider.requested.keySet().stream().noneMatch(key -> key.z == 1),
                "Oversized BlueMap zoom-step set should not be requested");
        assertTrue(provider.requested.containsKey(new PmTileKey(2, 1, 1)),
                "Same-LOD prefetch ring should still be requested");
        assertTrue(provider.requested.containsKey(new PmTileKey(3, 0, 0)),
                "Coarse fallback viewport should still be requested");
    }

    private static BlueMapRenderer rendererWithProvider(BlueMapCamera camera, TestBlueMapTileProvider provider)
            throws Exception {

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

    private static final class TestBlueMapTileProvider extends BlueMapTileProvider {

        private final Map<PmTileKey, Integer> requested = new HashMap<>();

        private TestBlueMapTileProvider() {

            super("https://example.invalid", 3, 1);
        }

        @Override
        public Optional<Identifier> request(PmTileKey key, int priority) {

            requested.merge(key, priority, Math::min);
            return Optional.empty();
        }
    }
}
