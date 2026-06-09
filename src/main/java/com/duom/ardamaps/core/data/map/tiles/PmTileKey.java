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

package com.duom.ardamaps.core.data.map.tiles;

/**
 * Represents a unique key for a map tile identified by its zoom level (z) and x, y coordinates.
 */
public class PmTileKey extends TileKey {

    public PmTileKey(int z, int x, int y) {
        super(z, x, y);
    }

    /**
     * Convert ZXY tile coordinates to a PMTiles tile ID using Hilbert curve indexing.
     *
     * @see <a href="https://github.com/tileverse-io/tileverse-pmtiles/blob/main/docs/pmtiles_format_specification.md#22-tile-id-calculation">PMTiles Tile ID Calculation</a> for details.
     */
    public long toTileId() {

        long zoomOffset = ((1L << (2 * z)) - 1) / 3;
        long hilbert = hilbertIndex(z, x, y);
        return zoomOffset + hilbert;
    }

    /**
     * Calculate the Hilbert curve index for given ZXY tile coordinates.
     *
     * @param z Zoom level
     * @param x Tile X coordinate
     * @param y Tile Y coordinate
     * @return Hilbert curve index
     */
    private long hilbertIndex(int z, int x, int y) {
        long n = 1L << z;
        long d = 0;

        long[] xy = {x, y};

        for (long s = n / 2; s > 0; s /= 2) {
            long rx = (xy[0] & s) > 0 ? 1 : 0;
            long ry = (xy[1] & s) > 0 ? 1 : 0;

            d += s * s * ((3 * rx) ^ ry);
            rotate(s, xy, rx, ry);
        }
        return d;
    }

    /**
     * Rotate/flip a quadrant appropriately.
     *
     * @param s  Size of the side of the square
     * @param xy Array containing x and y coordinates
     * @param rx Rotation in x
     * @param ry Rotation in y
     */
    private void rotate(long s, long[] xy, long rx, long ry) {
        if (ry == 0) {
            if (rx == 1) {
                xy[0] = s - 1 - xy[0];
                xy[1] = s - 1 - xy[1];
            }
            // swap x and y
            long t = xy[0];
            xy[0] = xy[1];
            xy[1] = t;
        }
    }

    /**
     * Determines whether this PmTileKey is equal to another object.
     * Two PmTileKeys are considered equal if they have the same zoom level and coordinates.
     *
     * @param o The object to compare with this PmTileKey.
     * @return true if the given object is a PmTileKey with the same zoom level and coordinates, false otherwise.
     */
    @Override
    public boolean equals(Object o) {

        if (!(o instanceof PmTileKey that)) return false;

        return this.x == that.x && this.y == that.y && this.z == that.z;
    }
}