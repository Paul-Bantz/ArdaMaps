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
import org.junit.jupiter.api.Test;

import static com.duom.ardamaps.core.data.ExplorationState.HIDDEN;
import static com.duom.ardamaps.core.data.ExplorationState.REVEALED;
import static com.duom.ardamaps.core.data.ExplorationState.VISIBLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for exploration grid coordinate and mutation logic.
 */
class ExplorationGridTest {

    /** Small world: 64x64 blocks. */
    private static final Dimension SMALL_DIM =
            new Dimension("Small", "test:small", 1f, 0, 64, 0, 64, false);

    /** Large world: 400 002x400 002 inclusive blocks. */
    private static final Dimension LARGE_DIM =
            new Dimension("Large", "test:large", 1f, 0, 400_001, 0, 400_001, false);

    /** Offset world starting at negative coordinates. */
    private static final Dimension OFFSET_DIM =
            new Dimension("Offset", "test:offset", 1f, -1000, 1001, -500, 501, false);

    /** Moria-sized world with explicit 8-block exploration cells. */
    private static final Dimension EXPLICIT_DIM =
            new Dimension("Moria", "test:moria", 1f, 0, 2939, 0, 1919, false, false, null, 8);

    /**
     * Small worlds should use the minimum grid cell size.
     */
    @Test
    void computeCellSize_smallDimension_returnsMinCellSize() {

        ExplorationGrid grid = ExplorationGrid.create(SMALL_DIM, null);

        assertEquals(128, grid.getCellSize());
    }

    /**
     * Large worlds should expand cell size enough to keep each axis under the maximum cell count.
     */
    @Test
    void computeCellSize_largeDimension_keepsGridUnderMaxCells() {

        ExplorationGrid grid = ExplorationGrid.create(LARGE_DIM, null);

        assertTrue(grid.getCellSize() >= 128);
        assertTrue(grid.getNbCellsX() <= 420);
        assertTrue(grid.getNbCellsY() <= 420);
    }

    /**
     * Explicit cell sizes should be honored for modestly sized dimensions.
     */
    @Test
    void computeCellSize_explicitSize_returnsRequestedGrid() {

        ExplorationGrid grid = ExplorationGrid.create(EXPLICIT_DIM, null);

        assertEquals(8, grid.getCellSize());
        assertEquals(368, grid.getNbCellsX());
        assertEquals(240, grid.getNbCellsY());
    }

    /**
     * A zero explicit size is the documented auto mode.
     */
    @Test
    void computeCellSize_zeroExplicitSize_usesAutoMode() {

        assertEquals(128, ExplorationGrid.computeCellSize(2940, 1920, 0));
    }

    /**
     * Tiny explicit cells are raised enough to cap the largest axis.
     */
    @Test
    void computeCellSize_tinyExplicitSize_capsGridAxis() {

        assertEquals(196, ExplorationGrid.computeCellSize(400_002, 400_002, 1));
    }

    /**
     * Persisted geometry matches only when all scalar grid fields still match the dimension.
     */
    @Test
    void matchesGeometry_comparesComputedGeometry() {

        ExplorationGrid grid = ExplorationGrid.create(EXPLICIT_DIM, null);

        assertTrue(ExplorationGrid.matchesGeometry(EXPLICIT_DIM, grid.getCellSize(), grid.getNbCellsX(),
                grid.getNbCellsY(), grid.getXMin(), grid.getZMin()));
        assertFalse(ExplorationGrid.matchesGeometry(EXPLICIT_DIM, 128, grid.getNbCellsX(),
                grid.getNbCellsY(), grid.getXMin(), grid.getZMin()));
        assertFalse(ExplorationGrid.matchesGeometry(EXPLICIT_DIM, grid.getCellSize(), grid.getNbCellsX() + 1,
                grid.getNbCellsY(), grid.getXMin(), grid.getZMin()));
    }

    /**
     * World minimum coordinates map to the first cell.
     */
    @Test
    void toCell_worldMinimum_returnsZero() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);

        assertEquals(0, grid.toCellX(-1000.0));
        assertEquals(0, grid.toCellZ(-500.0));
    }

    /**
     * Out-of-bounds reads are safe and return hidden.
     */
    @Test
    void stateAt_outOfBounds_returnsHidden() {

        ExplorationGrid grid = ExplorationGrid.create(SMALL_DIM, null);

        assertEquals(HIDDEN, grid.stateAt(-1, 0));
        assertEquals(HIDDEN, grid.stateAt(0, -1));
        assertEquals(HIDDEN, grid.stateAt(9999, 0));
    }

    /**
     * Marking a ranged cell on the western edge must not wrap negative X neighbors into the previous row.
     */
    @Test
    void markCell_westernEdgeRange_doesNotWrapToPreviousRow() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);

        grid.markCell(0, 1, REVEALED, 2);

        assertEquals(HIDDEN, grid.stateAt(grid.getNbCellsX() - 1, 0));
    }

    /**
     * Upgrade-only marking raises hidden cells to the requested state.
     */
    @Test
    void markCellAtLeast_hiddenCell_upgradesToVisible() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);

        assertEquals(grid.index(1, 1), grid.markCellAtLeast(1, 1, VISIBLE));

        assertEquals(VISIBLE, grid.stateAt(1, 1));
    }

    /**
     * Upgrade-only marking preserves stronger states.
     */
    @Test
    void markCellAtLeast_revealedCell_doesNotDowngradeToVisible() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);
        grid.markCell(1, 1, REVEALED);

        assertEquals(-1, grid.markCellAtLeast(1, 1, VISIBLE));

        assertEquals(REVEALED, grid.stateAt(1, 1));
    }

    /**
     * Upgrade-only ranged marking returns only cells whose state changed.
     */
    @Test
    void markCellAtLeast_range_returnsOnlyChangedCells() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);
        grid.markCell(1, 1, REVEALED);

        var changed = grid.markCellAtLeast(1, 1, VISIBLE, 1);

        assertEquals(4, changed.size());
        assertFalse(changed.contains(grid.index(1, 1)));
        assertTrue(changed.contains(grid.index(0, 1)));
        assertTrue(changed.contains(grid.index(1, 0)));
        assertTrue(changed.contains(grid.index(1, 2)));
        assertTrue(changed.contains(grid.index(2, 1)));
    }

    /**
     * Upgrade-only ranged marking respects circular radius and grid bounds.
     */
    @Test
    void markCellAtLeast_edgeRange_respectsRadiusAndBounds() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);

        var changed = grid.markCellAtLeast(0, 0, VISIBLE, 2);

        assertEquals(6, changed.size());
        assertTrue(changed.contains(grid.index(0, 0)));
        assertTrue(changed.contains(grid.index(1, 0)));
        assertTrue(changed.contains(grid.index(2, 0)));
        assertTrue(changed.contains(grid.index(0, 1)));
        assertTrue(changed.contains(grid.index(1, 1)));
        assertTrue(changed.contains(grid.index(0, 2)));
        assertEquals(HIDDEN, grid.stateAt(2, 1));
    }

    /**
     * Region queries return true when an intersecting cell is visible or revealed.
     */
    @Test
    void regionExplored_returnsTrueWhenAnyIntersectingCellIsRevealed() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);
        grid.markCell(1, 2, REVEALED);

        double cellTopLeftX = OFFSET_DIM.getXMin() + grid.getCellSize();
        double cellTopLeftZ = OFFSET_DIM.getZMin() + (2.0 * grid.getCellSize());

        assertTrue(grid.regionExplored(cellTopLeftX + 1, cellTopLeftZ + 1, 4, 4, 0));
        assertTrue(grid.regionExplored(cellTopLeftX - 2, cellTopLeftZ - 2, 4, 4, 0));
    }

    /**
     * Region queries return false when the rectangle is outside or disjoint from explored cells.
     */
    @Test
    void regionExplored_returnsFalseWhenNoIntersectingCellIsRevealed() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);
        grid.markCell(1, 2, REVEALED);

        double otherCellTopLeftX = OFFSET_DIM.getXMin() + (5.0 * grid.getCellSize());
        double otherCellTopLeftZ = OFFSET_DIM.getZMin() + (6.0 * grid.getCellSize());

        assertFalse(grid.regionExplored(otherCellTopLeftX, otherCellTopLeftZ, 10, 10, 0));
        assertFalse(grid.regionExplored(OFFSET_DIM.getXMin() - 5000, OFFSET_DIM.getZMin() - 5000, 20, 20, 0));
    }

    /**
     * Empty grids do not have a revealed hotspot.
     */
    @Test
    void findRevealedHotspot_emptyGrid_returnsNull() {

        ExplorationGrid grid = ExplorationGrid.create(0, 0, 1, 5, 5, null);

        assertNull(grid.findRevealedHotspot(1));
    }

    /**
     * A single revealed cell is selected as the hotspot.
     */
    @Test
    void findRevealedHotspot_singleCell_returnsCell() {

        ExplorationGrid grid = ExplorationGrid.create(0, 0, 1, 5, 5, null);
        grid.markCell(2, 3, REVEALED);

        var hotspot = grid.findRevealedHotspot(1);

        assertNotNull(hotspot);
        assertEquals(2, hotspot.cellX());
        assertEquals(3, hotspot.cellY());
        assertEquals(1, hotspot.revealedNeighbours());
    }

    /**
     * Dense revealed areas beat isolated revealed cells.
     */
    @Test
    void findRevealedHotspot_isolatedCellAndBlock_returnsBlockCenter() {

        ExplorationGrid grid = ExplorationGrid.create(0, 0, 1, 8, 8, null);
        grid.markCell(0, 0, REVEALED);
        for (int cellY = 3; cellY <= 5; cellY++) {
            for (int cellX = 3; cellX <= 5; cellX++) {
                grid.markCell(cellX, cellY, REVEALED);
            }
        }

        var hotspot = grid.findRevealedHotspot(1);

        assertNotNull(hotspot);
        assertEquals(4, hotspot.cellX());
        assertEquals(4, hotspot.cellY());
        assertEquals(9, hotspot.revealedNeighbours());
    }

    /**
     * Equal scores preserve scan-order tie breaking.
     */
    @Test
    void findRevealedHotspot_equalScores_returnsFirstInScanOrder() {

        ExplorationGrid grid = ExplorationGrid.create(0, 0, 1, 5, 5, null);
        grid.markCell(3, 3, REVEALED);
        grid.markCell(1, 1, REVEALED);

        var hotspot = grid.findRevealedHotspot(0);

        assertNotNull(hotspot);
        assertEquals(1, hotspot.cellX());
        assertEquals(1, hotspot.cellY());
    }

    /**
     * Visible cells alone are not considered revealed hotspots.
     */
    @Test
    void findRevealedHotspot_visibleOnly_returnsNull() {

        ExplorationGrid grid = ExplorationGrid.create(0, 0, 1, 5, 5, null);
        grid.markCell(2, 2, VISIBLE);

        assertNull(grid.findRevealedHotspot(1));
    }

    /**
     * Saved data with the expected length is used as the backing array so mutations remain visible to persistence.
     */
    @Test
    void create_withValidBackingData_usesSameArray() {

        ExplorationGrid template = ExplorationGrid.create(SMALL_DIM, null);
        byte[] backing = new byte[template.getNbCellsX() * template.getNbCellsY()];

        ExplorationGrid grid = ExplorationGrid.create(SMALL_DIM, backing);
        grid.markCell(0, 0, REVEALED);

        assertSame(backing, grid.getExplorationData());
        assertEquals(REVEALED.getValue(), backing[0]);
    }
}
