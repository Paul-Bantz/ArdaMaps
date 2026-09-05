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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Even-odd point-in-polygon tests for interleaved x,z integer rings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PointInPolygon {

    /**
     * Tests whether a point lies inside a multi-ring polygon.
     *
     * @param rings The rings as interleaved x,z coordinates.
     * @param x     The world X coordinate.
     * @param z     The world Z coordinate.
     * @return true when the point is inside an odd number of rings.
     */
    public static boolean contains(int[][] rings, double x, double z) {

        if (rings == null || rings.length == 0) return false;

        boolean inside = false;
        for (int[] ring : rings) {
            if (containsRing(ring, x, z)) inside = !inside;
        }

        return inside;
    }

    /**
     * Tests whether a point lies inside one closed ring.
     *
     * @param ring The interleaved x,z coordinates.
     * @param x    The world X coordinate.
     * @param z    The world Z coordinate.
     * @return true when the point is inside the ring.
     */
    public static boolean containsRing(int[] ring, double x, double z) {

        if (ring == null || ring.length < 6) return false;

        boolean inside = false;
        int vertexCount = ring.length / 2;
        int previous = vertexCount - 1;

        for (int current = 0; current < vertexCount; current++) {
            double xi = ring[current * 2];
            double zi = ring[current * 2 + 1];
            double xj = ring[previous * 2];
            double zj = ring[previous * 2 + 1];

            if (pointOnSegment(x, z, xi, zi, xj, zj)) return true;

            boolean crosses = (zi > z) != (zj > z);
            if (crosses) {
                double intersectionX = (xj - xi) * (z - zi) / (zj - zi) + xi;
                if (x < intersectionX) inside = !inside;
            }
            previous = current;
        }

        return inside;
    }

    /**
     * Tests whether a point lies directly on a segment.
     *
     * @param x  The point X coordinate.
     * @param z  The point Z coordinate.
     * @param ax The segment start X coordinate.
     * @param az The segment start Z coordinate.
     * @param bx The segment end X coordinate.
     * @param bz The segment end Z coordinate.
     * @return true when the point is on the segment.
     */
    private static boolean pointOnSegment(double x, double z, double ax, double az, double bx, double bz) {

        double cross = (z - az) * (bx - ax) - (x - ax) * (bz - az);
        if (Math.abs(cross) > 1.0e-9) return false;

        double minX = Math.min(ax, bx);
        double maxX = Math.max(ax, bx);
        double minZ = Math.min(az, bz);
        double maxZ = Math.max(az, bz);

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
