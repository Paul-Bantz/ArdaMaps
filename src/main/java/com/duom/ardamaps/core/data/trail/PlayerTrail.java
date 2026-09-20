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

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adaptive, bounded player movement trail for one dimension.
 */
public class PlayerTrail implements Serializable {

    /** Maximum persisted geometry points retained per dimension. */
    public static final int MAX_POINTS_PER_DIMENSION = 200_000;

    /** Simplification corridor half-width in blocks. */
    static final double SIMPLIFY_EPSILON_BLOCKS = 4d;

    /** Movement delta that forces a trail discontinuity. */
    static final double TELEPORT_THRESHOLD_BLOCKS = 512d;

    /** Minimum XZ delta that may be considered a position jump. */
    public static final double MIN_JUMP_BLOCKS = 16d;

    /** Maximum plausible XZ travel rate before a movement sample is considered a jump. */
    public static final double MAX_BLOCKS_PER_TICK = 4d;

    /** Maximum span of a simplification run before forcing a committed point. */
    static final double MAX_SEGMENT_SPAN_BLOCKS = 4096d;

    @Serial
    private static final long serialVersionUID = 1L;

    /** Trail segments in chronological order. */
    private final List<TrailSegment> segments = new ArrayList<>();

    /** Total retained point count. */
    private int pointCount;

    /** Anchor X coordinate for the current simplification run. */
    private int anchorX;

    /** Anchor Z coordinate for the current simplification run. */
    private int anchorZ;

    /** Last sampled X coordinate waiting for possible commitment. */
    private int lastX;

    /** Last sampled Z coordinate waiting for possible commitment. */
    private int lastZ;

    /** Whether a simplification run is active. */
    private boolean hasPending;

    /** Last committed X coordinate. */
    private int lastCommittedX;

    /** Last committed Z coordinate. */
    private int lastCommittedZ;

    /** Whether a committed point exists. */
    private boolean hasCommitted;

    /** Whether the next segment must start with a gap marker. */
    private boolean pendingGap;

    /** Whether the next resume should become a gap only if it is far from the last committed point. */
    private boolean conditionalGap;

    /**
     * Records a sampled point using adaptive live simplification.
     *
     * @param x    Absolute block X coordinate.
     * @param z    Absolute block Z coordinate.
     * @param mode Movement mode at the sample.
     */
    public void record(int x, int z, MovementMode mode) {

        if (!hasPending) {
            beginRun(x, z, mode);
            anchorX = x;
            anchorZ = z;
            lastX = x;
            lastZ = z;
            hasPending = true;
            return;
        }

        TrailSegment current = currentSegment();
        boolean modeChanged = current == null || current.mode() != mode;
        boolean teleport = distanceSquared(lastX, lastZ, x, z) > TELEPORT_THRESHOLD_BLOCKS * TELEPORT_THRESHOLD_BLOCKS;
        boolean spanExceeded = distanceSquared(anchorX, anchorZ, x, z) > MAX_SEGMENT_SPAN_BLOCKS * MAX_SEGMENT_SPAN_BLOCKS;

        if (teleport) {
            append(current == null ? mode : current.mode(), lastX, lastZ);
            markCurrentEndsAtGap();
            hasPending = false;
            pendingGap = true;
            conditionalGap = false;
            beginRun(x, z, mode);
            anchorX = x;
            anchorZ = z;
            lastX = x;
            lastZ = z;
            hasPending = true;
            return;
        }

        if (modeChanged || spanExceeded) {
            append(current == null ? mode : current.mode(), lastX, lastZ);
            anchorX = x;
            anchorZ = z;
            lastX = x;
            lastZ = z;
            append(mode, x, z);
            return;
        }

        if (perpendicularDistanceSquaredExceeds(anchorX, anchorZ, x, z, lastX, lastZ, SIMPLIFY_EPSILON_BLOCKS)) {
            append(mode, lastX, lastZ);
            anchorX = lastX;
            anchorZ = lastZ;
        }

        lastX = x;
        lastZ = z;
    }

    /**
     * Forces the pending point to be committed and ends the current simplification run.
     */
    public void breakSegment() {

        if (!hasPending) return;

        TrailSegment current = currentSegment();
        if (current != null) append(current.mode(), lastX, lastZ);
        hasPending = false;
        conditionalGap = hasCommitted;
    }

    /**
     * Commits the pre-jump endpoint, ends the current segment and arms a gap for the next point.
     *
     * @param x    Pre-jump block X coordinate.
     * @param z    Pre-jump block Z coordinate.
     * @param mode Movement mode to use if no segment exists yet.
     */
    public void breakForJump(int x, int z, MovementMode mode) {

        TrailSegment current = currentSegment();
        append(current == null ? mode : current.mode(), x, z);
        markCurrentEndsAtGap();
        hasPending = false;
        pendingGap = true;
        conditionalGap = false;
    }

    /**
     * Returns an immutable view of retained segments.
     *
     * @return Retained trail segments.
     */
    public List<TrailSegment> segments() {

        return Collections.unmodifiableList(segments);
    }

    /**
     * Clears all retained geometry and simplifier state.
     */
    public void clear() {

        segments.clear();
        pointCount = 0;
        hasPending = false;
        hasCommitted = false;
        pendingGap = false;
        conditionalGap = false;
    }

    /**
     * Returns the total retained point count.
     *
     * @return Retained point count.
     */
    public int pointCount() {

        return pointCount;
    }

    /**
     * Returns the retained segment count.
     *
     * @return Retained segment count.
     */
    public int segmentCount() {

        return segments.size();
    }

    /**
     * Returns whether a sampled point is waiting for commitment.
     *
     * @return True when a pending point exists.
     */
    public boolean hasPendingPoint() {

        return hasPending;
    }

    /**
     * Returns the pending point X coordinate.
     *
     * @return Pending block X coordinate.
     */
    public int pendingX() {

        return lastX;
    }

    /**
     * Returns the pending point Z coordinate.
     *
     * @return Pending block Z coordinate.
     */
    public int pendingZ() {

        return lastZ;
    }

    /**
     * Restores a persisted segment verbatim.
     *
     * @param segment The segment to restore.
     */
    void restoreSegment(TrailSegment segment) {

        if (segment.size() == 0) return;

        segments.add(segment);
        pointCount += segment.size();
        lastCommittedX = segment.x(segment.size() - 1);
        lastCommittedZ = segment.z(segment.size() - 1);
        hasCommitted = true;
        hasPending = false;
        pendingGap = false;
        conditionalGap = false;
        evictOverflow();
    }

    /**
     * Returns whether movement over an elapsed tick span is implausible travel.
     *
     * @param blocks       XZ distance in blocks.
     * @param elapsedTicks Number of elapsed ticks.
     * @return True when the delta should be treated as a jump.
     */
    public static boolean isPositionJump(double blocks, int elapsedTicks) {

        return blocks > Math.max(MIN_JUMP_BLOCKS, MAX_BLOCKS_PER_TICK * Math.max(1, elapsedTicks));
    }

    /**
     * Starts a simplification run, respecting any pending gap marker.
     *
     * @param x    Absolute block X coordinate.
     * @param z    Absolute block Z coordinate.
     * @param mode Movement mode.
     */
    private void beginRun(int x, int z, MovementMode mode) {

        boolean startsAtGap = pendingGap;
        if (conditionalGap && hasCommitted) {
            startsAtGap = distanceSquared(lastCommittedX, lastCommittedZ, x, z)
                    > MIN_JUMP_BLOCKS * MIN_JUMP_BLOCKS;
            if (startsAtGap) markCurrentEndsAtGap();
        }

        append(mode, x, z, startsAtGap, startsAtGap);
        pendingGap = false;
        conditionalGap = false;
    }

    /**
     * Appends a point, creating a new segment when needed.
     *
     * @param mode Movement mode.
     * @param x    Absolute block X coordinate.
     * @param z    Absolute block Z coordinate.
     */
    private void append(MovementMode mode, int x, int z) {

        append(mode, x, z, false, false);
    }

    /**
     * Appends a point, optionally forcing a new gap-start segment.
     *
     * @param mode        Movement mode.
     * @param x           Absolute block X coordinate.
     * @param z           Absolute block Z coordinate.
     * @param forceNew    Whether to force a new segment.
     * @param startsAtGap Whether a forced segment starts at a gap.
     */
    private void append(MovementMode mode, int x, int z, boolean forceNew, boolean startsAtGap) {

        TrailSegment segment = currentSegment();
        if (forceNew || segment == null || segment.mode() != mode) {
            segment = new TrailSegment(mode);
            segment.setStartsAtGap(startsAtGap);
            segments.add(segment);
        }

        int previousSize = segment.size();
        segment.append(x, z);
        if (segment.size() == previousSize) return;

        pointCount++;
        lastCommittedX = x;
        lastCommittedZ = z;
        hasCommitted = true;
        evictOverflow();
    }

    /**
     * Marks the most recent segment as ending at a discontinuity.
     */
    private void markCurrentEndsAtGap() {

        TrailSegment current = currentSegment();
        if (current != null && current.size() > 0) current.setEndsAtGap(true);
    }

    /**
     * Removes oldest whole segments until the point bound is satisfied.
     */
    private void evictOverflow() {

        while (pointCount > MAX_POINTS_PER_DIMENSION && !segments.isEmpty()) {
            TrailSegment removed = segments.removeFirst();
            pointCount -= removed.size();
        }

        TrailSegment current = currentSegment();
        if (current == null || current.size() == 0) {
            hasCommitted = false;
            return;
        }

        lastCommittedX = current.x(current.size() - 1);
        lastCommittedZ = current.z(current.size() - 1);
        hasCommitted = true;
    }

    /**
     * Returns the most recent segment.
     *
     * @return Current segment, or null when empty.
     */
    private TrailSegment currentSegment() {

        return segments.isEmpty() ? null : segments.getLast();
    }

    /**
     * Returns the squared distance between two XZ points.
     *
     * @param ax First X.
     * @param az First Z.
     * @param bx Second X.
     * @param bz Second Z.
     * @return Squared distance.
     */
    private static double distanceSquared(int ax, int az, int bx, int bz) {

        double dx = bx - ax;
        double dz = bz - az;
        return dx * dx + dz * dz;
    }

    /**
     * Tests whether a point lies outside a corridor around a line.
     *
     * @param ax      Line start X.
     * @param az      Line start Z.
     * @param bx      Line end X.
     * @param bz      Line end Z.
     * @param px      Point X.
     * @param pz      Point Z.
     * @param epsilon Corridor half-width in blocks.
     * @return True when the perpendicular distance exceeds the epsilon.
     */
    static boolean perpendicularDistanceSquaredExceeds(int ax, int az, int bx, int bz, int px, int pz, double epsilon) {

        double lineX = bx - ax;
        double lineZ = bz - az;
        double lengthSquared = lineX * lineX + lineZ * lineZ;
        if (lengthSquared == 0d) return distanceSquared(ax, az, px, pz) > epsilon * epsilon;

        double pointX = px - ax;
        double pointZ = pz - az;
        double cross = lineX * pointZ - lineZ * pointX;
        return cross * cross > epsilon * epsilon * lengthSquared;
    }
}
