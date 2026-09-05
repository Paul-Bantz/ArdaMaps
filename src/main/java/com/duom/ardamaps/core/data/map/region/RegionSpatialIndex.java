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

package com.duom.ardamaps.core.data.map.region;

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.map.geometry.PointInPolygon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Uniform-grid spatial index for exact region hover lookup.
 */
public final class RegionSpatialIndex {

    /** Maximum number of grid cells per axis. */
    private static final int MAX_GRID_AXIS = 128;

    /** Indexed dimension. */
    private final Dimension dimension;

    /** Indexed region geometry. */
    private final RegionGeometry geometry;

    /** Indexed shapes by region identifier. */
    private final Map<String, RegionShape> shapesById;

    /** Grid width in cells. */
    private final int gridWidth;

    /** Grid height in cells. */
    private final int gridHeight;

    /** Per-cell candidate shape arrays. */
    private final RegionShape[][] cells;

    /**
     * Builds a spatial index for the given geometry and dimension.
     *
     * @param geometry  The region geometry to index.
     * @param dimension The dimension definition.
     */
    public RegionSpatialIndex(RegionGeometry geometry, Dimension dimension) {

        this.dimension = dimension;
        this.geometry = geometry;
        this.shapesById = buildShapesById();
        this.gridWidth = Math.max(1, Math.min(MAX_GRID_AXIS, (int) Math.ceil(Math.sqrt(dimension.getWidth()))));
        this.gridHeight = Math.max(1, Math.min(MAX_GRID_AXIS, (int) Math.ceil(Math.sqrt(dimension.getHeight()))));
        this.cells = buildCells();
    }

    /**
     * Finds the deepest region containing a point.
     *
     * @param worldX The world X coordinate.
     * @param worldZ The world Z coordinate.
     * @return The matching region, or null.
     */
    public RegionShape getRegionAt(double worldX, double worldZ) {

        if (!containsDimension(worldX, worldZ)) return null;

        int cellX = cellX(worldX);
        int cellZ = cellZ(worldZ);
        RegionShape[] candidates = cells[cellZ * gridWidth + cellX];
        RegionShape best = null;

        for (RegionShape candidate : candidates) {
            if (!containsBbox(candidate, worldX, worldZ)) continue;
            if (!PointInPolygon.contains(candidate.rings(), worldX, worldZ)) continue;
            if (best == null || compare(candidate, best) < 0) best = candidate;
        }

        return best;
    }

    /**
     * @return The dimension identifier this index serves.
     */
    public String dimensionId() {

        return geometry.dimensionId();
    }

    /**
     * Resolves the top-most ancestor for a region shape.
     *
     * @param shape The shape to resolve.
     * @return The root shape reached through parent links.
     */
    public RegionShape rootOf(RegionShape shape) {

        RegionShape root = shape;
        String parentId = shape.parentId();
        Set<String> visited = new HashSet<>();

        while (parentId != null && visited.add(parentId)) {
            RegionShape parent = shapesById.get(parentId);
            if (parent == null) break;

            root = parent;
            parentId = parent.parentId();
        }

        return root;
    }

    /**
     * Indexes region shapes by identifier.
     *
     * @return The indexed region shapes.
     */
    private Map<String, RegionShape> buildShapesById() {

        Map<String, RegionShape> result = new HashMap<>();
        for (RegionShape shape : geometry.regions()) result.put(shape.id(), shape);
        return result;
    }

    /**
     * Builds candidate arrays for all grid cells.
     *
     * @return The populated cell candidate arrays.
     */
    @SuppressWarnings("unchecked")
    private RegionShape[][] buildCells() {

        List<RegionShape>[] candidateLists = new List[gridWidth * gridHeight];
        for (int i = 0; i < candidateLists.length; i++) {
            candidateLists[i] = new ArrayList<>();
        }

        for (RegionShape shape : geometry.regions()) {
            int minCellX = cellX(shape.minX());
            int maxCellX = cellX(shape.maxX());
            int minCellZ = cellZ(shape.minZ());
            int maxCellZ = cellZ(shape.maxZ());

            for (int z = minCellZ; z <= maxCellZ; z++) {
                for (int x = minCellX; x <= maxCellX; x++) {
                    candidateLists[z * gridWidth + x].add(shape);
                }
            }
        }

        RegionShape[][] result = new RegionShape[candidateLists.length][];
        for (int i = 0; i < candidateLists.length; i++) {
            result[i] = candidateLists[i].toArray(new RegionShape[0]);
        }
        return result;
    }

    /**
     * Checks whether a point lies inside the dimension bounds.
     *
     * @param worldX The world X coordinate.
     * @param worldZ The world Z coordinate.
     * @return true when the point lies inside the indexed dimension.
     */
    private boolean containsDimension(double worldX, double worldZ) {

        return Objects.equals(geometry.dimensionId(), dimension.getId())
                && worldX >= dimension.getXMin() && worldX <= dimension.getXMax()
                && worldZ >= dimension.getZMin() && worldZ <= dimension.getZMax();
    }

    /**
     * Checks whether a point lies inside a shape bbox.
     *
     * @param shape  The shape to test.
     * @param worldX The world X coordinate.
     * @param worldZ The world Z coordinate.
     * @return true when the point lies inside the bbox.
     */
    private static boolean containsBbox(RegionShape shape, double worldX, double worldZ) {

        return worldX >= shape.minX() && worldX <= shape.maxX()
                && worldZ >= shape.minZ() && worldZ <= shape.maxZ();
    }

    /**
     * Compares two matching candidates by hierarchy depth and bbox area.
     *
     * @param left  The first candidate.
     * @param right The second candidate.
     * @return A negative value when left should win.
     */
    private static int compare(RegionShape left, RegionShape right) {

        int depthCompare = Integer.compare(right.depth(), left.depth());
        if (depthCompare != 0) return depthCompare;

        return Long.compare(area(left), area(right));
    }

    /**
     * Computes a shape bbox area.
     *
     * @param shape The shape.
     * @return The bbox area.
     */
    private static long area(RegionShape shape) {

        return (long) (shape.maxX() - shape.minX()) * (long) (shape.maxZ() - shape.minZ());
    }

    /**
     * Converts a world X coordinate to a grid cell.
     *
     * @param worldX The world X coordinate.
     * @return The clamped grid X cell.
     */
    private int cellX(double worldX) {

        double normalized = (worldX - dimension.getXMin()) / Math.max(1.0, dimension.getWidth());
        return Math.max(0, Math.min(gridWidth - 1, (int) Math.floor(normalized * gridWidth)));
    }

    /**
     * Converts a world Z coordinate to a grid cell.
     *
     * @param worldZ The world Z coordinate.
     * @return The clamped grid Z cell.
     */
    private int cellZ(double worldZ) {

        double normalized = (worldZ - dimension.getZMin()) / Math.max(1.0, dimension.getHeight());
        return Math.max(0, Math.min(gridHeight - 1, (int) Math.floor(normalized * gridHeight)));
    }
}
