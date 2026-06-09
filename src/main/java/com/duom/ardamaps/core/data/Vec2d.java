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

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.NonNull;

/**
 * A 2D vector with double-precision coordinates.
 *
 * @param x The X coordinate of this vector.
 * @param y The Y coordinate of this vector.
 */
public record Vec2d(double x, double y) {

    /**
     * Constructs a new Vec2d with the specified coordinates.
     *
     * @param x The X coordinate.
     * @param y The Y coordinate.
     */
    public Vec2d {
    }

    /**
     * Calculates the Euclidean distance between this vector and another Vec2d.
     *
     * @param vec The other Vec2d.
     * @return The distance between the two vectors.
     */
    public double distanceTo(Vec3d vec) {
        double d = vec.x - this.x;
        double e = vec.z - this.y;
        return Math.sqrt(d * d + e * e);
    }

    /**
     * Adds the specified coordinates to this vector and returns a new Vec2d.
     *
     * @param x The X coordinate to add.
     * @param y The Y coordinate to add.
     * @return A new Vec2d that is the result of the addition.
     */
    public Vec2d add(double x, double y) {
        return new Vec2d(this.x + x, this.y + y);
    }

    /**
     * Returns a string representation of this Vec2d in the format "(x, y)" with 3 decimal places.
     *
     * @return A string representation of this Vec2d.
     */
    @Override
    public @NonNull String toString() {
        return String.format("(%8.3f, %8.3f)", x, y);
    }
}