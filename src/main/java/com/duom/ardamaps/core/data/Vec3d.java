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

import java.io.Serializable;
import java.util.Objects;

/**
 * Pure 3D vector used by config, API and location DTOs.
 */
public final class Vec3d implements Serializable {

    /** Zero vector. */
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);

    /** X coordinate. */
    public final double x;

    /** Y coordinate. */
    public final double y;

    /** Z coordinate. */
    public final double z;

    public Vec3d(double x, double y, double z) {

        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double distanceTo(Vec3d other) {

        return Math.sqrt(squaredDistanceTo(other));
    }

    public double squaredDistanceTo(Vec3d other) {

        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) return true;
        if (!(obj instanceof Vec3d vec3d)) return false;
        return Double.compare(x, vec3d.x) == 0
                && Double.compare(y, vec3d.y) == 0
                && Double.compare(z, vec3d.z) == 0;
    }

    @Override
    public int hashCode() {

        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {

        return String.format("(%8.3f, %8.3f, %8.3f)", x, y, z);
    }
}
