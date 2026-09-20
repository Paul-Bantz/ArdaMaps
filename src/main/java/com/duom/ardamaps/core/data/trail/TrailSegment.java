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
import java.util.Arrays;

/**
 * Append-only run of movement-trail points sharing one movement mode.
 */
public class TrailSegment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Initial primitive-array capacity. */
    private static final int INITIAL_CAPACITY = 8;

    /** Movement mode shared by every point in this segment. */
    private final MovementMode mode;

    /** Absolute block X coordinates. */
    private int[] xs = new int[INITIAL_CAPACITY];

    /** Absolute block Z coordinates. */
    private int[] zs = new int[INITIAL_CAPACITY];

    /** Number of stored points. */
    private int size;

    /** Whether this segment starts immediately after a trail gap. */
    private boolean startsAtGap;

    /** Whether this segment ends immediately before a trail gap. */
    private boolean endsAtGap;

    /**
     * Constructs an empty segment for a mode.
     *
     * @param mode Movement mode for this segment.
     */
    public TrailSegment(MovementMode mode) {

        this.mode = mode;
    }

    /**
     * Appends an absolute block coordinate.
     *
     * @param x Block X coordinate.
     * @param z Block Z coordinate.
     */
    public void append(int x, int z) {

        if (size > 0 && xs[size - 1] == x && zs[size - 1] == z) return;

        ensureCapacity(size + 1);
        xs[size] = x;
        zs[size] = z;
        size++;
    }

    /**
     * Ensures both coordinate arrays can hold the requested number of points.
     *
     * @param targetSize Required point capacity.
     */
    private void ensureCapacity(int targetSize) {

        if (targetSize <= xs.length) return;

        int capacity = xs.length + Math.max(1, xs.length / 2);
        while (capacity < targetSize) {
            capacity += Math.max(1, capacity / 2);
        }

        xs = Arrays.copyOf(xs, capacity);
        zs = Arrays.copyOf(zs, capacity);
    }

    /**
     * Returns the stored point count.
     *
     * @return Stored point count.
     */
    public int size() {

        return size;
    }

    /**
     * Returns the X coordinate at an index.
     *
     * @param index Point index.
     * @return Absolute block X coordinate.
     */
    public int x(int index) {

        return xs[index];
    }

    /**
     * Returns the Z coordinate at an index.
     *
     * @param index Point index.
     * @return Absolute block Z coordinate.
     */
    public int z(int index) {

        return zs[index];
    }

    /**
     * Returns this segment's movement mode.
     *
     * @return Movement mode.
     */
    public MovementMode mode() {

        return mode;
    }

    /**
     * Returns whether this segment starts after a discontinuity.
     *
     * @return True when the segment starts at a gap.
     */
    public boolean startsAtGap() {

        return startsAtGap;
    }

    /**
     * Returns whether this segment ends before a discontinuity.
     *
     * @return True when the segment ends at a gap.
     */
    public boolean endsAtGap() {

        return endsAtGap;
    }

    /**
     * Sets whether this segment starts after a discontinuity.
     *
     * @param startsAtGap Whether the segment starts at a gap.
     */
    void setStartsAtGap(boolean startsAtGap) {

        this.startsAtGap = startsAtGap;
    }

    /**
     * Sets whether this segment ends before a discontinuity.
     *
     * @param endsAtGap Whether the segment ends at a gap.
     */
    void setEndsAtGap(boolean endsAtGap) {

        this.endsAtGap = endsAtGap;
    }
}
