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

package com.duom.ardamaps.core.data.config;

/**
 * Region mapping from an owning dimension to another world.
 *
 * @param id The remapped world ID.
 * @param x1 The normalized minimum X coordinate, or the regionless sentinel.
 * @param z1 The normalized minimum Z coordinate, or the regionless sentinel.
 * @param x2 The normalized maximum X coordinate, or the regionless sentinel.
 * @param z2 The normalized maximum Z coordinate, or the regionless sentinel.
 */
public record DimensionRemap(String id, int x1, int z1, int x2, int z2) {

    /** Regionless remap coordinate sentinel. */
    private static final int REGIONLESS = Integer.MIN_VALUE;

    /**
     * Constructs a normalized remap region.
     *
     * @param id The remapped world ID.
     * @param x1 The first corner X coordinate.
     * @param z1 The first corner Z coordinate.
     * @param x2 The second corner X coordinate.
     * @param z2 The second corner Z coordinate.
     */
    public DimensionRemap {

        if (isRegionless(x1, z1, x2, z2)) {
            x1 = REGIONLESS;
            z1 = REGIONLESS;
            x2 = REGIONLESS;
            z2 = REGIONLESS;
        } else {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);

            x1 = minX;
            x2 = maxX;
            z1 = minZ;
            z2 = maxZ;
        }
    }

    /**
     * Constructs a regionless remap.
     *
     * @param id The remapped world ID.
     */
    public DimensionRemap(String id) {

        this(id, REGIONLESS, REGIONLESS, REGIONLESS, REGIONLESS);
    }

    /**
     * @return Whether this remap can be used as a teleport target.
     */
    public boolean hasRegion() {

        return !isRegionless(x1, z1, x2, z2);
    }

    /**
     * Checks whether a map coordinate is inside this remap region.
     *
     * @param x The world X coordinate.
     * @param z The world Z coordinate.
     * @return True when this remap has a region containing the coordinate.
     */
    public boolean contains(double x, double z) {

        return hasRegion() && x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    /**
     * Checks whether two remap regions overlap.
     *
     * @param other The other remap.
     * @return True when both remaps have regions that overlap.
     */
    public boolean overlaps(DimensionRemap other) {

        if (!hasRegion() || !other.hasRegion()) return false;

        return x1 <= other.x2
                && x2 >= other.x1
                && z1 <= other.z2
                && z2 >= other.z1;
    }

    /**
     * Checks whether coordinates represent a regionless remap.
     *
     * @param x1 The first X coordinate.
     * @param z1 The first Z coordinate.
     * @param x2 The second X coordinate.
     * @param z2 The second Z coordinate.
     * @return True when all coordinates are the regionless sentinel.
     */
    private static boolean isRegionless(int x1, int z1, int x2, int z2) {

        return x1 == REGIONLESS && z1 == REGIONLESS && x2 == REGIONLESS && z2 == REGIONLESS;
    }
}
