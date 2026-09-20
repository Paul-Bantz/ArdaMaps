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

package com.duom.ardamaps.core.data.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for adaptive movement trail sampling.
 */
class PlayerTrailTest {

    /**
     * Verifies straight movement keeps only start and committed endpoint.
     */
    @Test
    void record_straightLine_simplifiesIntermediatePoints() {

        PlayerTrail trail = new PlayerTrail();

        trail.record(0, 0, MovementMode.WALK);
        trail.record(100, 0, MovementMode.WALK);
        trail.record(200, 0, MovementMode.WALK);
        trail.breakSegment();

        assertEquals(1, trail.segments().size());
        assertEquals(2, trail.pointCount());
        assertEquals(0, trail.segments().getFirst().x(0));
        assertEquals(200, trail.segments().getFirst().x(1));
    }

    /**
     * Verifies a meaningful turn commits the previous point.
     */
    @Test
    void record_turn_commitsCornerPoint() {

        PlayerTrail trail = new PlayerTrail();

        trail.record(0, 0, MovementMode.WALK);
        trail.record(100, 0, MovementMode.WALK);
        trail.record(100, 100, MovementMode.WALK);
        trail.breakSegment();

        assertEquals(3, trail.pointCount());
        assertEquals(100, trail.segments().getFirst().x(1));
        assertEquals(0, trail.segments().getFirst().z(1));
    }

    /**
     * Verifies movement mode changes start a new segment.
     */
    @Test
    void record_modeChange_startsNewSegment() {

        PlayerTrail trail = new PlayerTrail();

        trail.record(0, 0, MovementMode.WALK);
        trail.record(10, 0, MovementMode.SWIM);

        assertEquals(2, trail.segments().size());
        assertEquals(MovementMode.WALK, trail.segments().getFirst().mode());
        assertEquals(MovementMode.SWIM, trail.segments().getLast().mode());
    }

    /**
     * Verifies implausible movement threshold boundaries.
     */
    @Test
    void isPositionJump_thresholds_respectsMinimumAndElapsedTicks() {

        assertFalse(PlayerTrail.isPositionJump(12.0, 1));
        assertTrue(PlayerTrail.isPositionJump(2_000.0, 1));
        assertFalse(PlayerTrail.isPositionJump(100.0, 40));
    }

    /**
     * Verifies an explicit jump splits same-mode geometry and marks both gap endpoints.
     */
    @Test
    void breakForJump_sameMode_startsFlaggedSegmentWithoutConnectingPoint() {

        PlayerTrail trail = new PlayerTrail();

        trail.record(0, 0, MovementMode.FLY);
        trail.record(10, 0, MovementMode.FLY);
        trail.breakForJump(10, 0, MovementMode.FLY);
        trail.record(2_000, 0, MovementMode.FLY);

        assertEquals(2, trail.segments().size());
        assertTrue(trail.segments().getFirst().endsAtGap());
        assertTrue(trail.segments().getLast().startsAtGap());
        assertEquals(10, trail.segments().getFirst().x(trail.segments().getFirst().size() - 1));
        assertEquals(2_000, trail.segments().getLast().x(0));
    }

    /**
     * Verifies far resumes after a break become gaps while nearby resumes stay continuous.
     */
    @Test
    void breakSegment_resumeDistance_splitsOnlyWhenFar() {

        PlayerTrail near = new PlayerTrail();
        near.record(0, 0, MovementMode.WALK);
        near.record(10, 0, MovementMode.WALK);
        near.breakSegment();
        near.record(14, 0, MovementMode.WALK);

        assertEquals(1, near.segments().size());
        assertFalse(near.segments().getFirst().endsAtGap());

        PlayerTrail far = new PlayerTrail();
        far.record(0, 0, MovementMode.WALK);
        far.record(10, 0, MovementMode.WALK);
        far.breakSegment();
        far.record(100, 0, MovementMode.WALK);

        assertEquals(2, far.segments().size());
        assertTrue(far.segments().getFirst().endsAtGap());
        assertTrue(far.segments().getLast().startsAtGap());
    }

    /**
     * Verifies the corridor-distance helper avoids square roots while preserving threshold behaviour.
     */
    @Test
    void perpendicularDistanceSquaredExceeds_pointOutsideCorridor_returnsTrue() {

        assertFalse(PlayerTrail.perpendicularDistanceSquaredExceeds(0, 0, 100, 0, 50, 4, 4d));
        assertTrue(PlayerTrail.perpendicularDistanceSquaredExceeds(0, 0, 100, 0, 50, 5, 4d));
    }
}
