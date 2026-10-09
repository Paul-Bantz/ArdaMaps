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
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure exploration grid logic for one dimension or vertical range.
 */
@Getter
public class ExplorationGrid implements Serializable {

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorationGrid.class);

    /** Minimum cell size in world units (blocks). */
    private static final int MIN_CELL_SIZE = 128;

    /** Maximum number of cells allowed along any single axis. */
    private static final int MAX_CELLS_PER_AXIS = 420;

    /** Maximum number of cells allowed along any single axis for explicit cell sizes. */
    private static final int MAX_EXPLICIT_CELLS_PER_AXIS = 2048;

    @Serial
    private static final long serialVersionUID = 1L;

    /** Number of cells along the X axis. */
    private final int nbCellsX;

    /** Number of cells along the Z axis. */
    private final int nbCellsY;

    /** World X coordinate corresponding to cell X index 0. */
    private final int xMin;

    /** World Z coordinate corresponding to cell Y index 0. */
    private final int zMin;

    /** Size of each exploration cell in world units. */
    private final int cellSize;

    /** Backing exploration state bytes in row-major order. */
    private final byte[] explorationData;

    /**
     * Creates a grid from a dimension and optional backing data.
     *
     * @param dimension       Dimension definition providing world extents.
     * @param explorationData Existing backing data, or null for a fully hidden grid.
     * @return The initialized grid.
     */
    public static ExplorationGrid create(Dimension dimension, byte[] explorationData) {

        int cellSize = computeCellSize(dimension.getWidth(), dimension.getHeight(), dimension.getExplorationCellSize());
        int nbCellsX = (int) Math.ceil((double) dimension.getWidth() / cellSize);
        int nbCellsY = (int) Math.ceil((double) dimension.getHeight() / cellSize);

        return create(dimension.getXMin(), dimension.getZMin(), cellSize, nbCellsX, nbCellsY, explorationData);
    }

    /**
     * Creates a grid from persisted scalar metadata and optional backing data.
     *
     * @param xMin            World X coordinate corresponding to cell X index 0.
     * @param zMin            World Z coordinate corresponding to cell Y index 0.
     * @param cellSize        Cell size in world units.
     * @param nbCellsX        Number of cells along X.
     * @param nbCellsY        Number of cells along Z.
     * @param explorationData Existing backing data, or null for a fully hidden grid.
     * @return The initialized grid.
     */
    public static ExplorationGrid create(int xMin, int zMin, int cellSize, int nbCellsX, int nbCellsY, byte[] explorationData) {

        int nbCells = nbCellsX * nbCellsY;
        byte[] data = explorationData == null || explorationData.length != nbCells
                ? emptyExploration(nbCells)
                : explorationData;

        return new ExplorationGrid(xMin, zMin, cellSize, nbCellsX, nbCellsY, data);
    }

    private ExplorationGrid(int xMin, int zMin, int cellSize, int nbCellsX, int nbCellsY, byte[] explorationData) {

        this.xMin = xMin;
        this.zMin = zMin;
        this.cellSize = cellSize;
        this.nbCellsX = nbCellsX;
        this.nbCellsY = nbCellsY;
        this.explorationData = explorationData;
    }

    /**
     * Computes the cell size for the given world dimensions and optional requested size.
     *
     * @param width     World width in blocks.
     * @param height    World height in blocks.
     * @param requested Requested cell size, or 0 for automatic sizing.
     * @return The resolved cell size in blocks.
     */
    static int computeCellSize(int width, int height, int requested) {

        if (requested > 0) {
            int minimum = (int) Math.ceil((double) Math.max(width, height) / MAX_EXPLICIT_CELLS_PER_AXIS);
            int resolved = Math.max(requested, minimum);

            if (resolved != requested) {
                LOGGER.warn("Raised requested exploration cell size from {} to {} to keep the grid under {} cells per axis.",
                        requested, resolved, MAX_EXPLICIT_CELLS_PER_AXIS);
            }

            return resolved;
        }

        int needed = (int) Math.ceil((double) Math.max(width, height) / MAX_CELLS_PER_AXIS);
        return Math.max(MIN_CELL_SIZE, needed);
    }

    /**
     * Checks whether persisted grid geometry still matches the current dimension configuration.
     *
     * @param dimension Current dimension definition.
     * @param cellSize  Persisted cell size.
     * @param nbCellsX  Persisted number of X cells.
     * @param nbCellsY  Persisted number of Z cells.
     * @param xMin      Persisted minimum X coordinate.
     * @param zMin      Persisted minimum Z coordinate.
     * @return Whether the persisted geometry matches the current computed geometry.
     */
    static boolean matchesGeometry(Dimension dimension, int cellSize, int nbCellsX, int nbCellsY, int xMin, int zMin) {

        int expectedCellSize = computeCellSize(dimension.getWidth(), dimension.getHeight(), dimension.getExplorationCellSize());
        int expectedCellsX = (int) Math.ceil((double) dimension.getWidth() / expectedCellSize);
        int expectedCellsY = (int) Math.ceil((double) dimension.getHeight() / expectedCellSize);

        return cellSize == expectedCellSize
                && nbCellsX == expectedCellsX
                && nbCellsY == expectedCellsY
                && xMin == dimension.getXMin()
                && zMin == dimension.getZMin();
    }

    /**
     * Creates an empty exploration byte array where every cell is {@link ExplorationState#HIDDEN}.
     *
     * @param nbCells Total number of cells.
     * @return A freshly allocated byte array.
     */
    private static byte[] emptyExploration(int nbCells) {

        byte[] array = new byte[nbCells];
        Arrays.fill(array, ExplorationState.HIDDEN.getValue());

        return array;
    }

    /**
     * Returns the exploration state of the cell at {@code (cellX, cellY)}.
     *
     * @param cellX Cell X index.
     * @param cellY Cell Y index.
     * @return The exploration state, or {@link ExplorationState#HIDDEN} if out of bounds.
     */
    public @NotNull ExplorationState stateAt(int cellX, int cellY) {

        if (!inBounds(cellX, cellY)) return ExplorationState.HIDDEN;
        return ExplorationState.fromValue(explorationData[index(cellX, cellY)]);
    }

    /**
     * Returns whether the given cell coordinates are valid.
     *
     * @param cellX Cell X index.
     * @param cellY Cell Y index.
     * @return Whether the coordinates are inside the grid.
     */
    public boolean inBounds(int cellX, int cellY) {

        return cellX >= 0 && cellX < nbCellsX
                && cellY >= 0 && cellY < nbCellsY;
    }

    /**
     * Converts 2D cell coordinates to a row-major array index.
     *
     * @param cellX Cell X index.
     * @param cellY Cell Y index.
     * @return Row-major array index.
     */
    public int index(int cellX, int cellY) {

        return cellY * nbCellsX + cellX;
    }

    /**
     * Converts a world X coordinate to a cell-X index.
     *
     * @param worldX World X coordinate.
     * @return Cell X index, possibly out of bounds.
     */
    public int toCellX(double worldX) {

        return Math.floorDiv((int) Math.floor(worldX) - xMin, cellSize);
    }

    /**
     * Converts a world Z coordinate to a cell-Y index.
     *
     * @param worldZ World Z coordinate.
     * @return Cell Y index, possibly out of bounds.
     */
    public int toCellZ(double worldZ) {

        return Math.floorDiv((int) Math.floor(worldZ) - zMin, cellSize);
    }

    /**
     * Returns the exploration state at a world position.
     *
     * @param worldX World X coordinate.
     * @param worldZ World Z coordinate.
     * @return The exploration state at that world position.
     */
    public ExplorationState stateAtWorldPos(double worldX, double worldZ) {

        return stateAt(toCellX(worldX), toCellZ(worldZ));
    }

    /**
     * Returns whether any nearby cell is revealed.
     *
     * @param worldX      World X coordinate.
     * @param worldZ      World Z coordinate.
     * @param radiusCells Radius in cells.
     * @return Whether any matching cell is revealed.
     */
    public boolean isWorldPosExplored(double worldX, double worldZ, int radiusCells) {

        int cx = toCellX(worldX);
        int cz = toCellZ(worldZ);

        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dy = -radiusCells; dy <= radiusCells; dy++) {
                if (dx * dx + dy * dy <= radiusCells * radiusCells) {
                    int cellX = cx + dx;
                    int cellY = cz + dy;
                    if (inBounds(cellX, cellY) && stateAt(cellX, cellY) == ExplorationState.REVEALED) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns whether at least one cell intersecting a world-space rectangle is visible or revealed.
     *
     * @param topLeftX    World-space top-left X.
     * @param topLeftZ    World-space top-left Z.
     * @param width       Region width in world units.
     * @param height      Region height in world units.
     * @param radiusCells Optional radius in cells to expand the region by.
     * @return Whether any intersecting cell is visible or revealed.
     */
    public boolean regionExplored(double topLeftX, double topLeftZ, double width, double height, int radiusCells) {

        double minX = Math.min(topLeftX, topLeftX + width);
        double maxX = Math.max(topLeftX, topLeftX + width);
        double minZ = Math.min(topLeftZ, topLeftZ + height);
        double maxZ = Math.max(topLeftZ, topLeftZ + height);

        if (maxX > minX) maxX = Math.nextDown(maxX);
        if (maxZ > minZ) maxZ = Math.nextDown(maxZ);

        int minCellX = toCellX(minX) - radiusCells;
        int maxCellX = toCellX(maxX) + radiusCells;
        int minCellZ = toCellZ(minZ) - radiusCells;
        int maxCellZ = toCellZ(maxZ) + radiusCells;

        if (maxCellX < 0 || maxCellZ < 0 || minCellX >= nbCellsX || minCellZ >= nbCellsY) {
            return false;
        }

        minCellX = Math.max(0, minCellX);
        maxCellX = Math.min(nbCellsX - 1, maxCellX);
        minCellZ = Math.max(0, minCellZ);
        maxCellZ = Math.min(nbCellsY - 1, maxCellZ);

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                if (stateAt(cellX, cellZ).ordinal() >= ExplorationState.VISIBLE.ordinal()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Finds the revealed cell with the densest square neighbourhood of revealed cells.
     *
     * @param radiusCells Radius in cells around each candidate to score.
     * @return The best revealed hotspot, or null when no cells are revealed.
     */
    public @Nullable RevealedHotspot findRevealedHotspot(int radiusCells) {

        int[] sums = revealedSummedAreaTable();
        RevealedHotspot best = null;

        for (int cellY = 0; cellY < nbCellsY; cellY++) {
            for (int cellX = 0; cellX < nbCellsX; cellX++) {
                if (stateAt(cellX, cellY) != ExplorationState.REVEALED) continue;

                int neighbours = revealedCount(sums,
                        Math.max(0, cellX - radiusCells),
                        Math.max(0, cellY - radiusCells),
                        Math.min(nbCellsX - 1, cellX + radiusCells),
                        Math.min(nbCellsY - 1, cellY + radiusCells));

                if (best == null || neighbours > best.revealedNeighbours()) {
                    best = new RevealedHotspot(cellX, cellY, neighbours);
                }
            }
        }

        return best;
    }

    /**
     * Builds a summed-area table for revealed cells.
     *
     * @return A row-major table with one extra row and column.
     */
    private int[] revealedSummedAreaTable() {

        int summedWidth = nbCellsX + 1;
        int[] sums = new int[summedWidth * (nbCellsY + 1)];

        for (int cellY = 0; cellY < nbCellsY; cellY++) {
            int rowCount = 0;
            for (int cellX = 0; cellX < nbCellsX; cellX++) {
                if (stateAt(cellX, cellY) == ExplorationState.REVEALED) rowCount++;

                int sumIndex = (cellY + 1) * summedWidth + cellX + 1;
                sums[sumIndex] = sums[sumIndex - summedWidth] + rowCount;
            }
        }

        return sums;
    }

    /**
     * Counts revealed cells within an inclusive rectangle using a summed-area table.
     *
     * @param sums     Summed-area table from {@link #revealedSummedAreaTable()}.
     * @param minCellX Inclusive minimum cell X.
     * @param minCellY Inclusive minimum cell Y.
     * @param maxCellX Inclusive maximum cell X.
     * @param maxCellY Inclusive maximum cell Y.
     * @return Number of revealed cells in the rectangle.
     */
    private int revealedCount(int[] sums, int minCellX, int minCellY, int maxCellX, int maxCellY) {

        int summedWidth = nbCellsX + 1;
        int x1 = maxCellX + 1;
        int y1 = maxCellY + 1;

        return sums[y1 * summedWidth + x1]
                - sums[minCellY * summedWidth + x1]
                - sums[y1 * summedWidth + minCellX]
                + sums[minCellY * summedWidth + minCellX];
    }

    /**
     * Sets one cell's exploration state.
     *
     * @param cellX Cell X index.
     * @param cellY Cell Y index.
     * @param state New exploration state.
     * @return The changed row-major index, or -1 when out of bounds.
     */
    public int markCell(int cellX, int cellY, ExplorationState state) {

        if (!inBounds(cellX, cellY)) return -1;

        int index = index(cellX, cellY);
        explorationData[index] = state.getValue();
        return index;
    }

    /**
     * Raises one cell to at least the given exploration state without downgrading it.
     *
     * @param cellX Cell X index.
     * @param cellY Cell Y index.
     * @param state Minimum exploration state.
     * @return The changed row-major index, or -1 when out of bounds or already at least that state.
     */
    public int markCellAtLeast(int cellX, int cellY, ExplorationState state) {

        if (!inBounds(cellX, cellY)) return -1;
        if (stateAt(cellX, cellY).ordinal() >= state.ordinal()) return -1;

        return markCell(cellX, cellY, state);
    }

    /**
     * Sets the exploration state for a cell and all cells within {@code range} cells of it.
     *
     * @param cellX Cell X index.
     * @param cellY Cell Y index.
     * @param state New exploration state.
     * @param range Radius in cells; 0 means only the target cell.
     * @return The changed row-major indices.
     */
    public Set<Integer> markCell(int cellX, int cellY, ExplorationState state, int range) {

        return markCells(cellX, cellY, state, range, false);
    }

    /**
     * Raises the exploration state for a cell and all cells within {@code range} cells of it.
     *
     * @param cellX Cell X index.
     * @param cellY Cell Y index.
     * @param state Minimum exploration state.
     * @param range Radius in cells; 0 means only the target cell.
     * @return The changed row-major indices.
     */
    public Set<Integer> markCellAtLeast(int cellX, int cellY, ExplorationState state, int range) {

        return markCells(cellX, cellY, state, range, true);
    }

    /**
     * Marks cells in a circular range, optionally applying upgrade-only semantics.
     *
     * @param cellX       Cell X index.
     * @param cellY       Cell Y index.
     * @param state       New or minimum exploration state.
     * @param range       Radius in cells; 0 means only the target cell.
     * @param upgradeOnly Whether already higher states should be preserved.
     * @return The changed row-major indices.
     */
    private Set<Integer> markCells(int cellX, int cellY, ExplorationState state, int range, boolean upgradeOnly) {

        Set<Integer> changedIndices = new HashSet<>();

        if (!inBounds(cellX, cellY)) return changedIndices;

        if (range == 0) {
            int changedIndex = upgradeOnly ? markCellAtLeast(cellX, cellY, state) : markCell(cellX, cellY, state);
            if (changedIndex >= 0) changedIndices.add(changedIndex);
            return changedIndices;
        }

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                int neighborX = cellX + dx;
                int neighborY = cellY + dy;
                if (dx * dx + dy * dy <= range * range && inBounds(neighborX, neighborY)) {
                    int changedIndex = upgradeOnly
                            ? markCellAtLeast(neighborX, neighborY, state)
                            : markCell(neighborX, neighborY, state);
                    if (changedIndex >= 0) changedIndices.add(changedIndex);
                }
            }
        }

        return changedIndices;
    }

    /**
     * Returns the world X coordinate of the centre of the given cell column.
     *
     * @param cellX Cell X index.
     * @return World X of the cell centre.
     */
    public double cellCenterX(int cellX) {

        return xMin + cellX * (double) cellSize + cellSize / 2.0;
    }

    /**
     * Returns the world Z coordinate of the centre of the given cell row.
     *
     * @param cellZ Cell Z index.
     * @return World Z of the cell centre.
     */
    public double cellCenterZ(int cellZ) {

        return zMin + cellZ * (double) cellSize + cellSize / 2.0;
    }

    /**
     * A revealed cell and its local revealed-cell density score.
     *
     * @param cellX              Cell X index.
     * @param cellY              Cell Y index.
     * @param revealedNeighbours Revealed cells inside the scoring window.
     */
    public record RevealedHotspot(int cellX, int cellY, int revealedNeighbours) {
    }
}
