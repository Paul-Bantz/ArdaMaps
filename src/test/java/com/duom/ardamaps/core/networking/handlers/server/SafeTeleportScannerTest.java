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

package com.duom.ardamaps.core.networking.handlers.server;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the bootstrap-free pure helpers in {@link SafeTeleportScanner}.
 */
class SafeTeleportScannerTest {

    /**
     * Verifies that an empty candidate above a full block resolves to the candidate Y itself.
     */
    @Test
    void resolveStandYAcceptsFullBlockSupportBelowFeet() {

        assertEquals(OptionalDouble.of(64.0D), SafeTeleportScanner.resolveStandY(64, 0.0D, false));
    }

    /**
     * Verifies that a partial support inside the feet block preserves its fractional standing height.
     */
    @Test
    void resolveStandYAcceptsPartialSupportInsideFeetBlock() {

        assertEquals(OptionalDouble.of(64.5D), SafeTeleportScanner.resolveStandY(64, 0.5D, false));
    }

    /**
     * Verifies that a full block at the candidate feet level is deferred to the next candidate block.
     */
    @Test
    void resolveStandYRejectsFullBlockAtFeet() {

        assertTrue(SafeTeleportScanner.resolveStandY(64, 1.0D, false).isEmpty());
    }

    /**
     * Verifies that unsupported non-water candidates are not standable.
     */
    @Test
    void resolveStandYRejectsUnsupportedNonWaterBlock() {

        assertTrue(SafeTeleportScanner.resolveStandY(64, Double.NEGATIVE_INFINITY, false).isEmpty());
    }

    /**
     * Verifies that unsupported water candidates resolve to the candidate Y for swimming teleports.
     */
    @Test
    void resolveStandYAcceptsUnsupportedWaterBlock() {

        assertEquals(OptionalDouble.of(64.0D), SafeTeleportScanner.resolveStandY(64, Double.NEGATIVE_INFINITY, true));
    }

    /**
     * Verifies that block-centre snapping handles positive, centered, and negative coordinates.
     */
    @Test
    void blockCenterSnapsCoordinatesToContainingBlockCenter() {

        assertEquals(10.5D, SafeTeleportScanner.blockCenter(10.0D));
        assertEquals(10.5D, SafeTeleportScanner.blockCenter(10.5D));
        assertEquals(-10.5D, SafeTeleportScanner.blockCenter(-10.1D));
        assertEquals(-11.5D, SafeTeleportScanner.blockCenter(-11.9D));
    }
}
