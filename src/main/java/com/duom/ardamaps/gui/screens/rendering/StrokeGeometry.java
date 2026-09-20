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

package com.duom.ardamaps.gui.screens.rendering;

import com.duom.ardamaps.core.data.Vec2d;

/**
 * Shared CPU stroke geometry helpers for map polylines.
 */
public final class StrokeGeometry {

    /**
     * Private constructor for utility class.
     */
    private StrokeGeometry() {

    }

    /**
     * Computes a miter join offset with a bevel fallback for sharp turns.
     *
     * @param px             The previous vertex X.
     * @param pz             The previous vertex Z.
     * @param cx             The current vertex X.
     * @param cz             The current vertex Z.
     * @param nx             The next vertex X.
     * @param nz             The next vertex Z.
     * @param halfWidthWorld The stroke half-width in world units.
     * @return The join offset.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    public static Vec2d joinOffset(double px, double pz, double cx, double cz, double nx, double nz,
                                   double halfWidthWorld) {

        double prevDx = cx - px;
        double prevDz = cz - pz;
        double nextDx = nx - cx;
        double nextDz = nz - cz;
        double prevLength = Math.hypot(prevDx, prevDz);
        double nextLength = Math.hypot(nextDx, nextDz);

        if (prevLength == 0.0 || nextLength == 0.0)
            return new Vec2d(0.0, halfWidthWorld);

        prevDx /= prevLength;
        prevDz /= prevLength;
        nextDx /= nextLength;
        nextDz /= nextLength;

        double tangentX = prevDx + nextDx;
        double tangentZ = prevDz + nextDz;
        double tangentLength = Math.hypot(tangentX, tangentZ);
        double nextNormalX = -nextDz;
        double nextNormalZ = nextDx;

        if (tangentLength == 0.0)
            return new Vec2d(nextNormalX * halfWidthWorld, nextNormalZ * halfWidthWorld);

        tangentX /= tangentLength;
        tangentZ /= tangentLength;
        double miterX = -tangentZ;
        double miterZ = tangentX;
        double denominator = miterX * nextNormalX + miterZ * nextNormalZ;

        if (Math.abs(denominator) < 0.1)
            return new Vec2d(nextNormalX * halfWidthWorld, nextNormalZ * halfWidthWorld);

        double miterLength = halfWidthWorld / denominator;
        double miterLimit = halfWidthWorld * 4.0;
        if (Math.abs(miterLength) > miterLimit)
            return new Vec2d(nextNormalX * halfWidthWorld, nextNormalZ * halfWidthWorld);

        return new Vec2d(miterX * miterLength, miterZ * miterLength);
    }

    /**
     * Computes an open-polyline cap offset perpendicular to the segment direction.
     *
     * @param dx             The segment X delta.
     * @param dz             The segment Z delta.
     * @param halfWidthWorld The stroke half-width in world units.
     * @return The cap offset.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    public static Vec2d endCapOffset(double dx, double dz, double halfWidthWorld) {

        double length = Math.hypot(dx, dz);
        if (length == 0.0) return new Vec2d(0.0, halfWidthWorld);

        return new Vec2d(-dz / length * halfWidthWorld, dx / length * halfWidthWorld);
    }
}
