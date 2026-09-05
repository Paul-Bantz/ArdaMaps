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

import org.jspecify.annotations.NonNull;

import java.io.Serializable;

/**
 * Pure 3D vector used by config, API and location DTOs.
 *
 * @param x X coordinate.
 * @param y Y coordinate.
 * @param z Z coordinate.
 */
public record Vec3d(double x, double y, double z) implements Serializable {

    /** Zero vector constant. */
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);

    /**
     * Calculates the Euclidean distance to another vector.
     *
     * @param other The other vector.
     * @return The distance between this vector and the other.
     */
    public double distanceTo(Vec3d other) {

        return Math.sqrt(squaredDistanceTo(other));
    }

    /**
     * Calculates the squared Euclidean distance to another vector.
     *
     * @param other The other vector.
     * @return The squared distance between this vector and the other.
     */
    public double squaredDistanceTo(Vec3d other) {

        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Compares this vector to another object for equality based on coordinates.
     *
     * @param obj The object to compare to.
     * @return True if the object is a Vec3d with equal coordinates.
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) return true;
        if (!(obj instanceof Vec3d(double x1, double y1, double z1))) return false;
        return Double.compare(x, x1) == 0
                && Double.compare(y, y1) == 0
                && Double.compare(z, z1) == 0;
    }

    /**
     * Returns a string representation of this vector in the format "(x, y, z)" with 3 decimal places.
     *
     * @return A formatted string representation of this vector.
     */
    @Override
    public @NonNull String toString() {

        return String.format("(%8.3f, %8.3f, %8.3f)", x, y, z);
    }
}
