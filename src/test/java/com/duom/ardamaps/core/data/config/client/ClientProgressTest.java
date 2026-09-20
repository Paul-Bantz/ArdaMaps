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

package com.duom.ardamaps.core.data.config.client;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.config.MapLayerSource;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.trail.MovementMode;
import com.google.gson.Gson;
import com.duom.ardamaps.gui.map.rendering.FogMaskTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ranged exploration-key handling in {@link ClientProgress}.
 */
class ClientProgressTest {

    /** Lower exploration band used by the ranged test dimension. */
    private static final MapLayerRange LOW = new MapLayerRange(0, "low.pmtiles", -64, 0);

    /** Upper exploration band used by the ranged test dimension. */
    private static final MapLayerRange HIGH = new MapLayerRange(1, "high.pmtiles", 1, 128);

    /** Gson instance used to exercise field-level progress adapters. */
    private static final Gson GSON = new Gson();

    /** Mocked fog-mask construction used to isolate texture registration from the Minecraft runtime. */
    private MockedConstruction<FogMaskTexture> mockedFogMaskTexture;

    /** Mocked static accessor for {@link Minecraft} so tests can provide a fake texture manager. */
    private MockedStatic<Minecraft> mockedMinecraftClient;

    /**
     * Installs the minimal mocked Minecraft client environment required for exploration texture creation.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    void setUp() {

        mockedFogMaskTexture = Mockito.mockConstruction(FogMaskTexture.class);

        Minecraft mockClient = Mockito.mock(Minecraft.class);
        TextureManager mockTextureManager = Mockito.mock(TextureManager.class);
        Mockito.when(mockClient.getTextureManager()).thenReturn(mockTextureManager);
        mockedMinecraftClient = Mockito.mockStatic(Minecraft.class);
        mockedMinecraftClient.when(Minecraft::getInstance).thenReturn(mockClient);

        ClientConfig config = new ClientConfig();
        config.setDimensions(List.of(rangedDimension()));
        LocationConfig<LocationClient> locationConfig = new LocationConfig<>();
        locationConfig.setLocations(List.of());
        config.setLocationConfig(locationConfig);
        ArdaMapsClient.CONFIG = config;
    }

    /**
     * Builds a dimension whose first layer exposes the canonical exploration ranges used by these tests.
     *
     * @return A ranged dimension definition suitable for {@link ClientProgress#reset(boolean)}.
     */
    private static Dimension rangedDimension() {

        Dimension dimension = new Dimension("Test", "test:dimension", 1f, 0, 1000, 0, 1000, false);
        dimension.getMapLayers().add(new MapLayerDefinition("Ranged", MapLayerSource.PMTILES, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, "fallback.pmtiles", "fallback.png", List.of(LOW, HIGH)));
        return dimension;
    }

    /**
     * Releases mocked native-resource wrappers and restores the shared test config state.
     */
    @AfterEach
    void tearDown() {

        mockedFogMaskTexture.close();
        mockedMinecraftClient.close();
        ArdaMapsClient.CONFIG = null;
    }

    /**
     * Verifies that ranged dimensions seed one exploration entry per canonical range rather than one flat entry per dimension.
     * This guards the reset path that now keys exploration state by composite dimension/range identifiers.
     */
    @Test
    void reset_initializesOneExplorationEntryPerRange() {

        ClientProgress progress = new ClientProgress();

        progress.reset(true);

        assertTrue(progress.getExplorationState().containsKey(ClientProgress.explorationKey("test:dimension", 0)));
        assertTrue(progress.getExplorationState().containsKey(ClientProgress.explorationKey("test:dimension", 1)));
        assertFalse(progress.getExplorationState().containsKey("test:dimension"));
    }

    /**
     * Verifies that the range-aware accessor resolves and stores exploration state under the composite key.
     * This protects callers that depend on direct map lookup returning the same instance the overload creates.
     */
    @Test
    void getExplorationState_rangeOverloadUsesCompositeKey() {

        ClientProgress progress = new ClientProgress();

        var low = progress.getExplorationState("test:dimension", 0, true);

        assertNotNull(low);
        assertSame(low, progress.getExplorationState().get("test:dimension#0"));
        assertEquals(0, low.getRangeIndex());
    }

    /**
     * Verifies auto-generated exploration resets preserve long-lived movement history.
     */
    @Test
    void reset_autoGeneratedOnly_preservesMovementHistory() {

        ClientProgress progress = new ClientProgress();
        progress.trail("test:dimension", true).record(1, 2, MovementMode.WALK);
        progress.getMovementStats().add(MovementMode.WALK, 10d);

        progress.reset(true);

        assertFalse(progress.getTrails().isEmpty());
        assertEquals(10d, progress.getMovementStats().getWalkedMetres());
    }

    /**
     * Verifies a full progress reset clears movement trails and distance counters.
     */
    @Test
    void reset_fullReset_clearsMovementHistory() {

        ClientProgress progress = new ClientProgress();
        progress.trail("test:dimension", true).record(1, 2, MovementMode.WALK);
        progress.getMovementStats().add(MovementMode.WALK, 10d);

        progress.reset(false);

        assertTrue(progress.getTrails().isEmpty());
        assertEquals(0d, progress.getMovementStats().getWalkedMetres());
    }

    /**
     * Verifies the movement-tracking reset clears only movement counters and trail geometry.
     */
    @Test
    void resetMovementTracking_clearsMovementHistory() {

        ClientProgress progress = new ClientProgress();
        progress.trail("test:dimension", true).record(1, 2, MovementMode.WALK);
        progress.getMovementStats().add(MovementMode.WALK, 10d);

        progress.resetMovementTracking();

        assertTrue(progress.getTrails().isEmpty());
        assertEquals(0d, progress.getMovementStats().getWalkedMetres());
    }

    /**
     * Verifies movement trails persist through ClientProgress JSON serialization.
     */
    @Test
    void gson_trails_roundTripThroughProgress() {

        ClientProgress progress = new ClientProgress();
        progress.trail("test:dimension", true).record(1, 2, MovementMode.WALK);

        ClientProgress decoded = GSON.fromJson(GSON.toJson(progress, ClientProgress.class), ClientProgress.class);

        assertInstanceOf(ConcurrentHashMap.class, decoded.getTrails());
        assertEquals(1, decoded.trail("test:dimension", false).pointCount());
    }
}
