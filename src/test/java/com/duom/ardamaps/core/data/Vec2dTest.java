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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for @{{@link Vec2d}}. Ensure edge-cases are covered, and out of bounds lookups are handled gracefully.
 */
class Vec2dTest {

    /**
     * Test that the constructor correctly initializes x and z from a Vec3d, ignoring the y component.
     */
    @Test
    void distanceTo_onlyUsesXandZ_notY() {
        // Vec2d is a 2D type mapping world (X, Z) - the Y axis is vertical height and must
        // be ignored.  Accidentally including Y would give wrong distances for any player
        // not standing at exactly sea level (e.g. flying or underground).
        Vec2d origin = new Vec2d(0, 0);
        Vec3d atSeaLevel = new Vec3d(3, 0, 4);   // Y = 0
        Vec3d highAltitude = new Vec3d(3, 1000, 4); // Y = 1000 - should give the same distance

        assertEquals(origin.distanceTo(atSeaLevel), origin.distanceTo(highAltitude), 1e-9,
                "Y axis must not affect the 2-D distance calculation");
    }

    /**
     * Test that distanceTo returns 0 for the same point, and is symmetric.
     */
    @Test
    void distanceTo_samePosition_returnsZero() {
        // A non-zero result here would make every on-screen location appear to be some
        // non-zero distance away from the player even when they are standing on it.
        Vec2d pos = new Vec2d(100, 200);
        Vec3d same = new Vec3d(100, 64, 200);
        assertEquals(0.0, pos.distanceTo(same), 1e-9);
    }

    /**
     * Validate known distance calculation
     */
    @Test
    void distanceTo_knownDistance_returnsCorrectValue() {
        // 3-4-5 right triangle on the XZ plane: sqrt(3²+4²) = 5.
        Vec2d origin = new Vec2d(0, 0);
        Vec3d point = new Vec3d(3, 99, 4);
        assertEquals(5.0, origin.distanceTo(point), 1e-9);
    }

    /**
     * Validation coordinates sum
     */
    @Test
    void add_returnsNewInstanceWithSummedCoordinates() {
        // Vec2d is a record (immutable by contract).  add() must return a new instance
        // and must not mutate the original - mutation would cause action-at-a-distance
        // bugs throughout the coordinate pipeline.
        Vec2d original = new Vec2d(10, 20);
        Vec2d result = original.add(5, 3);

        assertEquals(15.0, result.x(), 1e-9);
        assertEquals(23.0, result.y(), 1e-9);

        // Original must be unchanged.
        assertEquals(10.0, original.x(), 1e-9);
        assertEquals(20.0, original.y(), 1e-9);

        assertNotSame(original, result, "add() must return a new Vec2d instance");
    }

    /**
     * Validate toString format
     */
    @Test
    void toString_formatsWithThreeDecimalPlaces() {
        // Used in debug output; wrong formatting produces garbled coordinate strings
        // that are hard to parse in logs.
        Vec2d v = new Vec2d(1.23456, 78.9);
        String s = v.toString();
        // The format is "(%8.3f, %8.3f)" - verify it contains both values rounded to 3 dp.
        assertTrue(s.contains("1.235"), "x formatted to 3 dp: " + s);
        assertTrue(s.contains("78.900"), "y formatted to 3 dp: " + s);
    }
}
