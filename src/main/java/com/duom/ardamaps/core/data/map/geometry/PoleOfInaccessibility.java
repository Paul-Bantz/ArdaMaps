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

package com.duom.ardamaps.core.data.map.geometry;

import com.duom.ardamaps.core.data.Vec2d;

import java.util.PriorityQueue;

/**
 * Computes a polygon label anchor using the polylabel algorithm.
 */
public final class PoleOfInaccessibility {

    /** Target precision in world blocks. */
    private static final double PRECISION = 1.0;

    /** Square root of two used for cell radius calculation. */
    private static final double SQRT_2 = Math.sqrt(2.0);

    /** Hidden constructor for this utility class. */
    private PoleOfInaccessibility() {

    }

    /**
     * Computes the label anchor for a multi-ring polygon.
     *
     * @param rings The rings as interleaved x,z coordinates.
     * @param minX  The minimum X coordinate to search.
     * @param minZ  The minimum Z coordinate to search.
     * @param maxX  The maximum X coordinate to search.
     * @param maxZ  The maximum Z coordinate to search.
     * @return The best interior point and its radius.
     */
    public static Result compute(int[][] rings, int minX, int minZ, int maxX, int maxZ) {

        if (rings == null || rings.length == 0 || minX > maxX || minZ > maxZ)
            return new Result(new Vec2d(minX, minZ), 0.0);

        double width = maxX - minX;
        double height = maxZ - minZ;
        double cellSize = Math.min(width, height);
        if (cellSize <= 0.0) return fallback(rings, minX, minZ);

        double half = cellSize / 2.0;
        PriorityQueue<Cell> queue = new PriorityQueue<>((left, right) -> Double.compare(right.max(), left.max()));

        for (double x = minX; x < maxX; x += cellSize) {
            for (double z = minZ; z < maxZ; z += cellSize) {
                queue.add(new Cell(x + half, z + half, half, signedDistance(rings, x + half, z + half)));
            }
        }

        Cell best = fallbackCell(rings, minX, minZ);
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            if (cell.distance() > best.distance()) best = cell;
            if (cell.max() - best.distance() <= PRECISION) continue;

            double childHalf = cell.half() / 2.0;
            queue.add(new Cell(cell.x() - childHalf, cell.z() - childHalf, childHalf,
                    signedDistance(rings, cell.x() - childHalf, cell.z() - childHalf)));
            queue.add(new Cell(cell.x() + childHalf, cell.z() - childHalf, childHalf,
                    signedDistance(rings, cell.x() + childHalf, cell.z() - childHalf)));
            queue.add(new Cell(cell.x() - childHalf, cell.z() + childHalf, childHalf,
                    signedDistance(rings, cell.x() - childHalf, cell.z() + childHalf)));
            queue.add(new Cell(cell.x() + childHalf, cell.z() + childHalf, childHalf,
                    signedDistance(rings, cell.x() + childHalf, cell.z() + childHalf)));
        }

        return new Result(new Vec2d(best.x(), best.z()), Math.max(0.0, best.distance()));
    }

    /**
     * Creates a fallback result when the polygon has a degenerate bbox.
     *
     * @param rings The polygon rings.
     * @param minX  The minimum X coordinate.
     * @param minZ  The minimum Z coordinate.
     * @return The fallback result.
     */
    private static Result fallback(int[][] rings, int minX, int minZ) {

        Cell cell = fallbackCell(rings, minX, minZ);
        return new Result(new Vec2d(cell.x(), cell.z()), Math.max(0.0, cell.distance()));
    }

    /**
     * Creates a fallback cell from the first ring's first vertex.
     *
     * @param rings The polygon rings.
     * @param minX  The minimum X coordinate.
     * @param minZ  The minimum Z coordinate.
     * @return The fallback cell.
     */
    private static Cell fallbackCell(int[][] rings, int minX, int minZ) {

        for (int[] ring : rings) {
            if (ring != null && ring.length >= 2) {
                return new Cell(ring[0], ring[1], 0.0, signedDistance(rings, ring[0], ring[1]));
            }
        }

        return new Cell(minX, minZ, 0.0, 0.0);
    }

    /**
     * Computes a signed distance from the point to the closest polygon edge.
     *
     * @param rings The polygon rings.
     * @param x     The point X coordinate.
     * @param z     The point Z coordinate.
     * @return Positive distance inside the polygon, negative outside it.
     */
    private static double signedDistance(int[][] rings, double x, double z) {

        double minDistanceSquared = Double.POSITIVE_INFINITY;

        for (int[] ring : rings) {
            if (ring == null || ring.length < 4) continue;

            int vertexCount = ring.length / 2;
            int previous = vertexCount - 1;
            for (int current = 0; current < vertexCount; current++) {
                double distanceSquared = distanceToSegmentSquared(x, z,
                        ring[previous * 2], ring[previous * 2 + 1],
                        ring[current * 2], ring[current * 2 + 1]);
                minDistanceSquared = Math.min(minDistanceSquared, distanceSquared);
                previous = current;
            }
        }

        double distance = Math.sqrt(minDistanceSquared);
        return PointInPolygon.contains(rings, x, z) ? distance : -distance;
    }

    /**
     * Computes the squared distance from a point to a segment.
     *
     * @param px The point X coordinate.
     * @param pz The point Z coordinate.
     * @param ax The segment start X coordinate.
     * @param az The segment start Z coordinate.
     * @param bx The segment end X coordinate.
     * @param bz The segment end Z coordinate.
     * @return The squared distance.
     */
    private static double distanceToSegmentSquared(double px, double pz, double ax, double az, double bx, double bz) {

        double dx = bx - ax;
        double dz = bz - az;
        if (dx == 0.0 && dz == 0.0) {
            dx = px - ax;
            dz = pz - az;
            return dx * dx + dz * dz;
        }

        double t = ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz);
        t = Math.max(0.0, Math.min(1.0, t));
        dx = px - (ax + t * dx);
        dz = pz - (az + t * dz);
        return dx * dx + dz * dz;
    }

    /**
     * Polylabel result containing an anchor and radius.
     *
     * @param anchor The selected anchor point.
     * @param radius The distance to the closest edge.
     */
    public record Result(Vec2d anchor, double radius) {
    }

    /**
     * Search cell used by the polylabel priority queue.
     *
     * @param x        The cell centre X coordinate.
     * @param z        The cell centre Z coordinate.
     * @param half     The cell half size.
     * @param distance The signed centre distance.
     */
    private record Cell(double x, double z, double half, double distance) {

        /**
         * @return The maximum possible distance inside this cell.
         */
        double max() {

            return distance + half * SQRT_2;
        }
    }
}
