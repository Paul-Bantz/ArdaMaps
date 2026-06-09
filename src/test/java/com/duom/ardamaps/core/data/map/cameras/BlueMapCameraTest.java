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

package com.duom.ardamaps.core.data.map.cameras;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.client.ClientConfig;
import com.duom.ardamaps.core.data.config.client.ClientProgress;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BlueMapCamera#getVisibleTiles(int)}.
 *
 * <p>Covers three main scenarios:
 * <ul>
 *   <li>No exploration data available (null) — tiles should remain visible.</li>
 *   <li>Exploration data present but all tiles unexplored — tiles should be filtered out.</li>
 *   <li>The "reveal all" flag is set — exploration filter should be bypassed entirely.</li>
 * </ul>
 *
 * <p>{@link ArdaMapsClient#CONFIG} is replaced with a Mockito mock before each test and
 * restored to its original value after, so the global state is not permanently modified.</p>
 */
class BlueMapCameraTest {

    /**
     * Shared test dimension spanning from (-2000, -2000) to (2001, 2001) in world coordinates,
     * used to construct {@link BlueMapCamera} instances under test.
     */
    private static final Dimension DIMENSION =
            new Dimension("Test", "test:blue", 1f, -2000, 2001, -2000, 2001, false);

    /** Holds the real {@link ClientConfig} so it can be restored after each test. */
    private ClientConfig previousConfig;

    /**
     * Saves the current {@link ArdaMapsClient#CONFIG} before each test so it can be
     * restored in {@link #tearDown()}.
     */
    @BeforeEach
    void setUp() {
        previousConfig = ArdaMapsClient.CONFIG;
    }

    /**
     * Restores {@link ArdaMapsClient#CONFIG} to its original value after each test,
     * preventing mock leakage between tests.
     */
    @AfterEach
    void tearDown() {
        ArdaMapsClient.CONFIG = previousConfig;
    }

    /**
     * Verifies that when {@link ClientProgress#getExplorationState(String, boolean)} returns
     * {@code null} (i.e. no exploration data is available for the dimension),
     * {@link BlueMapCamera#getVisibleTiles(int)} still returns a non-empty set.
     *
     * <p>This ensures that a missing exploration state is treated as "all visible" rather
     * than "all hidden", preventing a blank map when exploration data has not yet been loaded.</p>
     */
    @Test
    void getVisibleTiles_nullExploration_keepsTilesVisible() {

        var config = Mockito.mock(ClientConfig.class);
        var progress = Mockito.mock(ClientProgress.class);

        Mockito.when(config.isMapRevealAll()).thenReturn(false);
        Mockito.when(config.getClientProgress()).thenReturn(progress);
        Mockito.when(progress.getExplorationState(DIMENSION.getId(), false)).thenReturn(null);

        ArdaMapsClient.CONFIG = config;

        BlueMapCamera camera = new BlueMapCamera(640, 480, 0, 0);
        camera.setDimension(DIMENSION);

        Set<PmTileKey> tiles = camera.getVisibleTiles(1);

        assertFalse(tiles.isEmpty());
    }

    /**
     * Verifies that tiles whose corresponding world region is entirely unexplored are excluded
     * from the result of {@link BlueMapCamera#getVisibleTiles(int)}.
     *
     * <p>{@link PlayerExploration#regionExplored(double, double, double, double, int)} is stubbed
     * to always return {@code false}, simulating a dimension where no area has been explored yet.
     * The expected outcome is an empty tile set.</p>
     */
    @Test
    void getVisibleTiles_unexploredTiles_areFilteredOut() {

        var config = Mockito.mock(ClientConfig.class);
        var progress = Mockito.mock(ClientProgress.class);
        var exploration = Mockito.mock(PlayerExploration.class);

        Mockito.when(config.isMapRevealAll()).thenReturn(false);
        Mockito.when(config.getClientProgress()).thenReturn(progress);
        Mockito.when(progress.getExplorationState(DIMENSION.getId(), false)).thenReturn(exploration);
        Mockito.when(exploration.regionExplored(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyInt()))
                .thenReturn(false);

        ArdaMapsClient.CONFIG = config;

        BlueMapCamera camera = new BlueMapCamera(640, 480, 0, 0);
        camera.setDimension(DIMENSION);

        Set<PmTileKey> tiles = camera.getVisibleTiles(1);

        assertTrue(tiles.isEmpty());
    }

    /**
     * Verifies that when {@link ClientConfig#isMapRevealAll()} returns {@code true}, the
     * exploration filter is completely bypassed and all visible tiles are returned regardless
     * of exploration state.
     *
     * <p>No {@link ClientProgress} or {@link PlayerExploration} mock is needed here, since
     * the reveal-all path should short-circuit before any exploration lookup occurs.</p>
     */
    @Test
    void getVisibleTiles_revealAllBypassesExplorationFilter() {

        var config = Mockito.mock(ClientConfig.class);

        Mockito.when(config.isMapRevealAll()).thenReturn(true);

        ArdaMapsClient.CONFIG = config;

        BlueMapCamera camera = new BlueMapCamera(640, 480, 0, 0);
        camera.setDimension(DIMENSION);

        Set<PmTileKey> tiles = camera.getVisibleTiles(1);

        assertFalse(tiles.isEmpty());
    }
}