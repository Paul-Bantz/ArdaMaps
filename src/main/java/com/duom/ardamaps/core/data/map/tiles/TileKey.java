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

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a unique key for a map tile identified by its zoom level (z) and x, y coordinates.
 */
public class TileKey {

    /** The zoom level of the tile. */
    public final int x, y, z;

    /**
     * Constructs a TileKey with the specified zoom level and coordinates.
     *
     * @param z The zoom level of the tile.
     * @param x The x coordinate of the tile.
     * @param y The y coordinate of the tile.
     */
    public TileKey(int z, int x, int y) {
        this.z = z;
        this.x = x;
        this.y = y;
    }

    /**
     * Returns a string representation of this TileKey in the format "z=zoom, x=x, y=y".
     *
     * @return A string representation of this TileKey.
     */
    @Override
    public @NotNull String toString() {
        return String.format("z=%d, x=%d, y=%d", z, x, y);
    }

    /**
     * Determines whether this TileKey is equal to another object.
     * Two TileKeys are considered equal if they have the same zoom level and coordinates.
     *
     * @param o The object to compare with this TileKey.
     * @return true if the given object is a TileKey with the same zoom level and coordinates, false otherwise.
     */
    @Override
    public boolean equals(Object o) {

        if (!(o instanceof TileKey that)) return false;

        return this.x == that.x && this.y == that.y && this.z == that.z;
    }

    /**
     * Generates a hash code for this TileKey based on its zoom level and coordinates.
     *
     * @return A hash code representing this TileKey.
     */
    @Override
    public int hashCode() {

        return Objects.hash(z, x, y);
    }
}