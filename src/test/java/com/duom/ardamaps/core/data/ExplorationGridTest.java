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
     * Marking a ranged cell on the western edge must not wrap negative X neighbours into the previous row.
     */
    @Test
    void markCell_westernEdgeRange_doesNotWrapToPreviousRow() {

        ExplorationGrid grid = ExplorationGrid.create(OFFSET_DIM, null);

        grid.markCell(0, 1, REVEALED, 2);

        assertEquals(HIDDEN, grid.stateAt(grid.getNbCellsX() - 1, 0));
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
