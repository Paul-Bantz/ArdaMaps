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
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.DimensionRemap;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.config.MapLayerSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static com.duom.ardamaps.core.data.ExplorationState.HIDDEN;
import static com.duom.ardamaps.core.data.ExplorationState.REVEALED;
import static com.duom.ardamaps.core.data.ExplorationState.VISIBLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ranged exploration-key handling in {@link ClientProgress}.
 */
class ClientProgressTest {

    /** Lower exploration band used by the ranged test dimension. */
    private static final MapLayerRange LOW = new MapLayerRange(0, "low.pmtiles", -64, 0);

    /** Upper exploration band used by the ranged test dimension. */
    private static final MapLayerRange HIGH = new MapLayerRange(1, "high.pmtiles", 1, 128);

    /** Mocked image construction used to isolate texture-backed exploration setup from native resources. */
    private MockedConstruction<NativeImage> mockedNativeImage;

    /** Mocked texture construction used to isolate dynamic texture registration from the Minecraft runtime. */
    private MockedConstruction<NativeImageBackedTexture> mockedNativeImageBackedTexture;

    /** Mocked static accessor for {@link MinecraftClient} so tests can provide a fake texture manager. */
    private MockedStatic<MinecraftClient> mockedMinecraftClient;

    /**
     * Installs the minimal mocked Minecraft client environment required for exploration texture creation.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    void setUp() {

        mockedNativeImage = Mockito.mockConstruction(NativeImage.class);
        mockedNativeImageBackedTexture = Mockito.mockConstruction(NativeImageBackedTexture.class);

        MinecraftClient mockClient = Mockito.mock(MinecraftClient.class);
        TextureManager mockTextureManager = Mockito.mock(TextureManager.class);
        Mockito.when(mockClient.getTextureManager()).thenReturn(mockTextureManager);
        Mockito.when(mockTextureManager.registerDynamicTexture(Mockito.any(), Mockito.any()))
                .thenReturn(Identifier.of("ardamaps", "dummy"));

        mockedMinecraftClient = Mockito.mockStatic(MinecraftClient.class);
        mockedMinecraftClient.when(MinecraftClient::getInstance).thenReturn(mockClient);

        ClientConfig config = new ClientConfig();
        config.setDimensions(List.of(rangedDimension()));
        ArdaMapsClient.CONFIG = config;
    }

    /**
     * Builds a dimension whose first layer exposes the canonical exploration ranges used by these tests.
     *
     * @return A ranged dimension definition suitable for {@link ClientProgress#reset(boolean)}.
     */
    private static Dimension rangedDimension() {

        Dimension dimension = new Dimension("Test", "test:dimension", 1f, 0, 1000, 0, 1000, false, false,
                List.of(new DimensionRemap("test:remapped")));
        dimension.getMapLayers().add(new MapLayerDefinition("Ranged", MapLayerSource.PMTILES, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, "fallback.pmtiles", "fallback.png", List.of(LOW, HIGH)));
        return dimension;
    }

    /**
     * Builds the ranged dimension with an explicit exploration cell size.
     *
     * @return A ranged dimension whose grid geometry differs from {@link #rangedDimension()}.
     */
    private static Dimension explicitRangedDimension() {

        Dimension dimension = new Dimension("Test", "test:dimension", 1f, 0, 1000, 0, 1000, false, false,
                List.of(new DimensionRemap("test:remapped")), 8);
        dimension.getMapLayers().add(new MapLayerDefinition("Ranged", MapLayerSource.PMTILES, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, "fallback.pmtiles", "fallback.png", List.of(LOW, HIGH)));
        return dimension;
    }

    /**
     * Releases mocked native-resource wrappers and restores the shared test config state.
     */
    @AfterEach
    void tearDown() {

        mockedNativeImage.close();
        mockedNativeImageBackedTexture.close();
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
     * Verifies remapped world IDs use the owner dimension's exploration key.
     * This prevents fog progress from being orphaned under the raw remapped world ID.
     */
    @Test
    void getExplorationState_remappedDimensionUsesOwnerKey() {

        ClientProgress progress = new ClientProgress();

        var remapped = progress.getExplorationState("test:remapped", 0, true);
        var canonical = progress.getExplorationState("test:dimension", 0, true);

        assertNotNull(remapped);
        assertSame(remapped, canonical);
        assertTrue(progress.getExplorationState().containsKey(ClientProgress.explorationKey("test:dimension", 0)));
        assertFalse(progress.getExplorationState().containsKey(ClientProgress.explorationKey("test:remapped", 0)));
    }

    /**
     * Grid migration removes only entries whose persisted geometry no longer matches current config.
     */
    @Test
    void migrateGridGeometry_removesMismatchedEntriesAndKeepsMatchingOnes() {

        Dimension oldFlat = new Dimension("Flat", "test:flat", 1f, 0, 1000, 0, 1000, false);
        Dimension newFlat = new Dimension("Flat", "test:flat", 1f, 0, 1000, 0, 1000, false, false, null, 8);
        ClientProgress progress = new ClientProgress();
        progress.getExplorationState().put(ClientProgress.explorationKey("test:flat", null),
                PlayerExploration.create(oldFlat, null));
        progress.getExplorationState().put(ClientProgress.explorationKey("test:dimension", 0),
                PlayerExploration.create(rangedDimension(), 0, null));
        progress.getExplorationState().put(ClientProgress.explorationKey("test:dimension", 1),
                PlayerExploration.create(explicitRangedDimension(), 1, null));

        assertTrue(progress.migrateGridGeometry(List.of(newFlat, explicitRangedDimension())));

        assertFalse(progress.getExplorationState().containsKey(ClientProgress.explorationKey("test:flat", null)));
        assertFalse(progress.getExplorationState().containsKey(ClientProgress.explorationKey("test:dimension", 0)));
        assertTrue(progress.getExplorationState().containsKey(ClientProgress.explorationKey("test:dimension", 1)));
    }

    /**
     * Existing revealed ranged cells are backfilled upward as visible cells without widening the saved area.
     */
    @Test
    void propagateVisibleUpward_existingRevealedCells_marksAboveRangesVisible() {

        Dimension dimension = rangedDimension();
        ClientProgress progress = new ClientProgress();
        PlayerExploration low = PlayerExploration.create(dimension, LOW.index(), null);
        PlayerExploration high = PlayerExploration.create(dimension, HIGH.index(), null);
        progress.getExplorationState().put(ClientProgress.explorationKey(dimension.getId(), LOW.index()), low);
        progress.getExplorationState().put(ClientProgress.explorationKey(dimension.getId(), HIGH.index()), high);
        low.markCell(1, 1, REVEALED);
        high.markCell(2, 2, REVEALED);

        assertTrue(progress.propagateVisibleUpward(List.of(dimension)));

        assertEquals(VISIBLE, high.stateAt(1, 1));
        assertEquals(REVEALED, high.stateAt(2, 2));
        assertEquals(HIDDEN, high.stateAt(0, 1));
        assertFalse(progress.propagateVisibleUpward(List.of(dimension)));
    }
}
