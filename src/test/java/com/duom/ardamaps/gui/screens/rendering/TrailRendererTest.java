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
import com.duom.ardamaps.core.data.trail.MovementMode;
import com.duom.ardamaps.core.data.trail.PlayerTrail;
import com.duom.ardamaps.gui.map.rendering.RegionBorderRenderState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for pure travel-trail rendering geometry helpers.
 */
class TrailRendererTest {

    /**
     * Verifies Chaikin smoothing keeps endpoints, softens a corner, and does not overshoot.
     */
    @Test
    void chaikin_rightAngle_preservesEndpointsAndBounds() {

        List<TrailRenderer.Point> points = List.of(
                new TrailRenderer.Point(0.0, 0.0),
                new TrailRenderer.Point(10.0, 0.0),
                new TrailRenderer.Point(10.0, 10.0));

        List<TrailRenderer.Point> smoothed = TrailRenderer.chaikin(points, 1);

        assertEquals(points.getFirst(), smoothed.getFirst());
        assertEquals(points.getLast(), smoothed.getLast());
        assertTrue(smoothed.size() >= 5);

        double sharpestInteriorAngle = 180.0;
        for (int index = 1; index < smoothed.size() - 1; index++) {
            TrailRenderer.Point previous = smoothed.get(index - 1);
            TrailRenderer.Point current = smoothed.get(index);
            TrailRenderer.Point next = smoothed.get(index + 1);

            assertTrue(current.x() >= 0.0 && current.x() <= 10.0);
            assertTrue(current.z() >= 0.0 && current.z() <= 10.0);
            sharpestInteriorAngle = Math.min(sharpestInteriorAngle, interiorAngle(previous, current, next));
        }

        assertTrue(sharpestInteriorAngle > 90.0);
    }

    /**
     * Verifies Chaikin leaves too-short lines untouched.
     */
    @Test
    void chaikin_shortInputs_returnsInput() {

        List<TrailRenderer.Point> empty = List.of();
        List<TrailRenderer.Point> single = List.of(new TrailRenderer.Point(1.0, 2.0));
        List<TrailRenderer.Point> pair = List.of(new TrailRenderer.Point(1.0, 2.0),
                new TrailRenderer.Point(3.0, 4.0));

        assertSame(empty, TrailRenderer.chaikin(empty, 3));
        assertSame(single, TrailRenderer.chaikin(single, 3));
        assertSame(pair, TrailRenderer.chaikin(pair, 3));
    }

    /**
     * Verifies decimation drops dense collinear points while preserving endpoints.
     */
    @Test
    void decimate_denseLine_preservesEndpoints() {

        ArrayList<TrailRenderer.Point> points = new ArrayList<>();
        for (int x = 0; x <= 10; x++) {
            points.add(new TrailRenderer.Point(x, 0.0));
        }

        ArrayList<TrailRenderer.Point> decimated = TrailRenderer.decimate(points, 3.0);

        assertTrue(decimated.size() < points.size());
        assertEquals(points.getFirst(), decimated.getFirst());
        assertEquals(points.getLast(), decimated.getLast());
    }

    /**
     * Verifies a larger decimation epsilon keeps fewer points.
     */
    @Test
    void decimate_largerEpsilon_keepsFewerPoints() {

        ArrayList<TrailRenderer.Point> points = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            points.add(new TrailRenderer.Point(x, 0.0));
        }

        ArrayList<TrailRenderer.Point> small = TrailRenderer.decimate(points, 2.0);
        ArrayList<TrailRenderer.Point> large = TrailRenderer.decimate(points, 5.0);

        assertTrue(large.size() < small.size());
    }

    /**
     * Verifies the budget decimation pass keeps segment endpoints instead of truncating.
     */
    @Test
    void decimatedPolylines_overBudget_keepsEverySegmentEndpoint() {

        PlayerTrail trail = new PlayerTrail();
        for (int index = 0; index < 7_000; index++) {
            trail.record(index, index % 2 == 0 ? 0 : 10, MovementMode.WALK);
        }

        trail.record(20_000, 0, MovementMode.SWIM);
        for (int index = 0; index < 7_000; index++) {
            trail.record(20_000 + index, index % 2 == 0 ? 0 : 10, MovementMode.SWIM);
        }

        trail.breakSegment();

        ArrayList<TrailRenderer.Polyline> polylines = TrailRenderer.decimatedPolylines(trail, 1.0);

        assertEquals(2, polylines.size());
        assertEquals(0.0, polylines.getFirst().points().getFirst().x());
        assertEquals(6_999.0, polylines.getFirst().points().getLast().x());
        assertEquals(20_000.0, polylines.getLast().points().getFirst().x());
        assertEquals(26_999.0, polylines.getLast().points().getLast().x());
    }

    /**
     * Verifies gap flags are copied from trail segments to render polylines.
     */
    @Test
    void decimatedPolylines_gapSegments_propagatesFlags() {

        PlayerTrail trail = new PlayerTrail();
        trail.record(0, 0, MovementMode.FLY);
        trail.record(10, 0, MovementMode.FLY);
        trail.breakForJump(10, 0, MovementMode.FLY);
        trail.record(100, 0, MovementMode.FLY);
        trail.record(110, 0, MovementMode.FLY);

        ArrayList<TrailRenderer.Polyline> polylines = TrailRenderer.decimatedPolylines(trail, 1.0);

        assertEquals(2, polylines.size());
        assertFalse(polylines.getFirst().gapStart());
        assertTrue(polylines.getFirst().gapEnd());
        assertTrue(polylines.getLast().gapStart());
        assertFalse(polylines.getLast().gapEnd());
    }

    /**
     * Verifies a gap tick is perpendicular to the trail direction and centered on its endpoint.
     */
    @Test
    void addTick_horizontalTrail_buildsCenteredPerpendicularQuad() {

        ArrayList<RegionBorderRenderState.Vertex> vertices = new ArrayList<>();

        TrailRenderer.addTick(vertices, new TrailRenderer.Point(10.0, 20.0), 5.0, 0.0,
                3.0, 1.0, 0xFFFFFFFF, new TrailRenderer.FogUv(0.0, 0.0, 100.0, 100.0));

        assertEquals(4, vertices.size());
        assertEquals(10.0, vertices.stream().mapToDouble(RegionBorderRenderState.Vertex::x).average().orElseThrow(),
                1.0e-6);
        assertEquals(20.0, vertices.stream().mapToDouble(RegionBorderRenderState.Vertex::z).average().orElseThrow(),
                1.0e-6);
        assertEquals(2, vertices.stream().filter(vertex -> Math.abs(vertex.z() - 23.0F) < 1.0e-6).count());
        assertEquals(2, vertices.stream().filter(vertex -> Math.abs(vertex.z() - 17.0F) < 1.0e-6).count());
        assertEquals(2, vertices.stream().filter(vertex -> Math.abs(vertex.x() - 11.0F) < 1.0e-6).count());
        assertEquals(2, vertices.stream().filter(vertex -> Math.abs(vertex.x() - 9.0F) < 1.0e-6).count());
    }

    /**
     * Verifies open end-cap offsets are perpendicular and match the requested width.
     */
    @Test
    void endCapOffset_segment_returnsPerpendicularHalfWidth() {

        Vec2d offset = StrokeGeometry.endCapOffset(3.0, 4.0, 2.0);

        assertEquals(0.0, offset.x() * 3.0 + offset.y() * 4.0, 1.0e-9);
        assertEquals(2.0, Math.hypot(offset.x(), offset.y()), 1.0e-9);
    }

    /**
     * Verifies the miter-limit fallback still returns the next segment normal.
     */
    @Test
    void joinOffset_sharpTurn_usesBevelFallback() {

        Vec2d offset = StrokeGeometry.joinOffset(0.0, 0.0, 1.0, 0.0, 0.001, 0.01, 2.0);
        Vec2d expected = StrokeGeometry.endCapOffset(-0.999, 0.01, 2.0);

        assertEquals(expected.x(), offset.x(), 1.0e-9);
        assertEquals(expected.y(), offset.y(), 1.0e-9);
    }

    /**
     * Computes the interior angle at a polyline vertex.
     *
     * @param previous The previous point.
     * @param current  The current point.
     * @param next     The next point.
     * @return The interior angle in degrees.
     */
    private static double interiorAngle(TrailRenderer.Point previous, TrailRenderer.Point current,
                                        TrailRenderer.Point next) {

        double ax = previous.x() - current.x();
        double az = previous.z() - current.z();
        double bx = next.x() - current.x();
        double bz = next.z() - current.z();
        double denominator = Math.hypot(ax, az) * Math.hypot(bx, bz);
        if (denominator == 0.0) return 180.0;

        double cosine = (ax * bx + az * bz) / denominator;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }
}
