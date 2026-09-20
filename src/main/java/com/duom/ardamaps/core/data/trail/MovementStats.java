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

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Lifetime movement distance counters in real-world metres.
 */
@Getter
public class MovementStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Walked distance in real-world metres. */
    private double walkedMetres;

    /** Swum distance in real-world metres. */
    private double swamMetres;

    /** Flown distance in real-world metres. */
    private double flownMetres;

    /**
     * Adds a distance to the bucket for the given movement mode.
     *
     * @param mode   Movement mode.
     * @param metres Distance in real-world metres.
     */
    public void add(MovementMode mode, double metres) {

        if (metres <= 0d || !Double.isFinite(metres)) return;

        switch (mode) {
            case WALK -> walkedMetres += metres;
            case SWIM -> swamMetres += metres;
            case FLY -> flownMetres += metres;
        }
    }

    /**
     * Resets all distance counters.
     */
    public void reset() {

        walkedMetres = 0d;
        swamMetres = 0d;
        flownMetres = 0d;
    }

    /**
     * Creates a detached copy.
     *
     * @return Copied movement stats.
     */
    public MovementStats copy() {

        MovementStats copy = new MovementStats();
        copy.walkedMetres = walkedMetres;
        copy.swamMetres = swamMetres;
        copy.flownMetres = flownMetres;
        return copy;
    }
}
