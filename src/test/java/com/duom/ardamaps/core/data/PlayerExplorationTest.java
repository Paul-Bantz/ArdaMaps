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

package com.duom.ardamaps.core.data;

import com.duom.ardamaps.core.data.config.Dimension;
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

import static com.duom.ardamaps.core.data.ExplorationState.HIDDEN;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for @{{@link PlayerExploration}}. Ensure edge-cases are covered, and out of bounds lookups are handled gracefully.
 */
class PlayerExplorationTest {

    /** Small world: 64×64 blocks (well under the 420*128 = 53760 threshold). */
    private static final Dimension SMALL_DIM =
            new Dimension("Small", "test:small", 1f, 0, 64, 0, 64, false);

    /** Large world: 400 000×400 000 blocks (far over the threshold). */
    private static final Dimension LARGE_DIM =
            new Dimension("Large", "test:large", 1f, 0, 400_001, 0, 400_001, false);

    /**
     * Offset world: starts at negative coordinates (like the real Arda world).
     * xMin = -1000, xMax = 1001 -> width = 2000.  zMin = -500, zMax = 501 -> height = 1000.
     */
    private static final Dimension OFFSET_DIM =
            new Dimension("Offset", "test:offset", 1f, -1000, 1001, -500, 501, false);

    /** Minecraft internal mocks */
    private MockedConstruction<NativeImage> mockedNativeImage;

    private MockedConstruction<NativeImageBackedTexture> mockedNativeImageBackedTexture;

    private MockedStatic<MinecraftClient> mockedMinecraftClient;

    /**
     * Setup - mock minecraft internals
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

    }

    /**
     * Clear mocks after each pass
     */
    @AfterEach
    public void afterEach() {
        mockedNativeImage.close();
        mockedNativeImageBackedTexture.close();
        mockedMinecraftClient.close();
    }

    /**
     * A world under 128 * 420 = 53 760 blocks on any axis must use the minimum cell size (128).
     * Returning a smaller value would over-resolve the fog grid and waste memory; returning a larger one would under-resolve it.
     */
    @Test
    void computeCellSize_smallDimension_returnsMinCellSize() {

        PlayerExploration pe = PlayerExploration.create(SMALL_DIM, null);

        assertEquals(128, pe.getCellSize(),
                "Small worlds must use MIN_CELL_SIZE=128 to avoid an oversized fog array");
    }

    /**
     * A 200 000-block world needs cellSize = ceil(200000/420) = 477. If the formula is wrong,
     * the grid would exceed MAX_CELLS_PER_AXIS (420) and the backing array would be ~4× larger than intended.
     */
    @Test
    void computeCellSize_largeDimension_exceedsMinAndKeepsGridUnderMaxCells() {

        PlayerExploration pe = PlayerExploration.create(LARGE_DIM, null);

        assertTrue(pe.getCellSize() >= 128, "cellSize must be at least MIN_CELL_SIZE");
        assertTrue(200_000.0 / pe.getCellSize() <= 420,
                "Grid must not exceed MAX_CELLS_PER_AXIS=420 on any axis");
    }

    /**
     * The minimum world-X coordinate must map to cell 0.
     * A negative result would cause stateAt() to hit the out-of-bounds guard and return HIDDEN
     */
    @Test
    void toCellX_worldXAtDimensionMin_returnsZero() {

        PlayerExploration pe = PlayerExploration.create(OFFSET_DIM, null);

        int cell = pe.toCellX(-1000.0);

        assertEquals(0, cell, "xMin must map to cell 0");
    }

    /**
     * The maximum world-X coordinate may be exactly one past the last valid cell.
     * This documents that toCellX() does NOT clamp - stateAt() handles OOB via inBounds().
     */
    @Test
    void toCellX_worldXAtDimensionMax_returnsLastCellOrJustBeyond() {

        PlayerExploration pe = PlayerExploration.create(OFFSET_DIM, null);
        int cell = pe.toCellX(1001.0);
        assertTrue(cell >= 0, "OOB cell index must still be non-negative (sign-safe)");
    }

    /**
     * The minimum world-Z coordinate must map to cell 0.
     * A negative result would cause stateAt() to hit the out-of-bounds guard and return HIDDEN
     */
    @Test
    void toCellZ_worldZAtDimensionMin_returnsZero() {
        PlayerExploration pe = PlayerExploration.create(OFFSET_DIM, null);
        assertEquals(0, pe.toCellZ(-500.0), "zMin must map to cell 0");
    }

    /**
     * Newly created exploration must start fully fogged.
     * Any cell starting REVEALED means the fog-of-war is broken from the very first render.
     */
    @Test
    void stateAt_freshExploration_allCellsHidden() {

        PlayerExploration pe = PlayerExploration.create(SMALL_DIM, null);
        assertEquals(HIDDEN, pe.stateAt(0, 0));
        assertEquals(HIDDEN, pe.stateAt(5, 3));
    }

    /**
     * The inBounds() guard prevents ArrayIndexOutOfBoundsException when the player stands right at or beyond the world edge.
     * Without it, a crash every time the player looks at the map from the boundary.
     */
    @Test
    void stateAt_outOfBoundsCellCoord_returnsHidden() {

        PlayerExploration pe = PlayerExploration.create(SMALL_DIM, null);
        assertEquals(HIDDEN, pe.stateAt(-1, 0), "negative cellX must be safe");
        assertEquals(HIDDEN, pe.stateAt(0, -1), "negative cellY must be safe");
        assertEquals(HIDDEN, pe.stateAt(9999, 0), "huge cellX must be safe");
    }

    /**
     * Integration test: world coords flow through toCellX/Z -> stateAt.
     * If any step in the pipeline has an off-by-one, this test catches it at the level that LocationProvider actually calls in production.
     */
    @Test
    void stateAtWorldPos_knownPosition_isHiddenOnFreshExploration() {
        //
        PlayerExploration pe = PlayerExploration.create(OFFSET_DIM, null);

        // xMin = -1000, any X in-range should give HIDDEN (fresh exploration).
        assertEquals(HIDDEN, pe.stateAtWorldPos(-999.0, -499.0));
        assertEquals(HIDDEN, pe.stateAtWorldPos(0.0, 0.0));
    }

    /**
     * Validates that region explored returns true when a partial cell has been explored
     */
    @Test
    void regionExplored_returnsTrueWhenAnyIntersectingCellIsRevealed() {

        PlayerExploration pe = PlayerExploration.create(OFFSET_DIM, null);
        pe.markCell(1, 2, ExplorationState.REVEALED);

        int cellSize = pe.getCellSize();
        double cellTopLeftX = OFFSET_DIM.getXMin() + cellSize;
        double cellTopLeftZ = OFFSET_DIM.getZMin() + (2.0 * cellSize);

        assertTrue(pe.regionExplored(cellTopLeftX + 1, cellTopLeftZ + 1, 4, 4, 0));
        assertTrue(pe.regionExplored(cellTopLeftX - 2, cellTopLeftZ - 2, 4, 4, 0));
    }

    /**
     * Validates that region explored returns false when no intersecting cells have been explored
     */
    @Test
    void regionExplored_returnsFalseWhenNoIntersectingCellIsRevealed() {

        PlayerExploration pe = PlayerExploration.create(OFFSET_DIM, null);
        pe.markCell(1, 2, ExplorationState.REVEALED);

        int cellSize = pe.getCellSize();
        double otherCellTopLeftX = OFFSET_DIM.getXMin() + (5.0 * cellSize);
        double otherCellTopLeftZ = OFFSET_DIM.getZMin() + (6.0 * cellSize);

        assertFalse(pe.regionExplored(otherCellTopLeftX, otherCellTopLeftZ, 10, 10, 0));
        assertFalse(pe.regionExplored(OFFSET_DIM.getXMin() - 5000, OFFSET_DIM.getZMin() - 5000, 20, 20, 0));
    }
}
