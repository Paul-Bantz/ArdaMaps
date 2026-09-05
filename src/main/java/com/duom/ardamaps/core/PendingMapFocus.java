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

package com.duom.ardamaps.core;

import com.duom.ardamaps.core.data.location.LocationClient;
import org.jetbrains.annotations.Nullable;

/**
 * One-shot holder for a recently visited location that the next map-key press should focus.
 */
public final class PendingMapFocus {

    /** Duration after a visit during which the map key focuses that location. */
    public static final long WINDOW_MS = 10_000L;

    /** Location to focus when the map key is pressed, or {@code null} when none is pending. */
    @Nullable
    private static LocationClient location;

    /** Wall-clock time in milliseconds after which the pending focus expires. */
    private static long expiresAtMs;

    /**
     * Hidden constructor for the static holder.
     */
    private PendingMapFocus() {

    }

    /**
     * Stores a location for the next map-key open, replacing any earlier pending focus.
     *
     * @param location The location to focus.
     */
    public static void set(LocationClient location) {

        set(location, System.currentTimeMillis());
    }

    /**
     * Stores a location using a supplied clock value.
     *
     * @param location The location to focus.
     * @param nowMs    The current time in milliseconds.
     */
    static synchronized void set(LocationClient location, long nowMs) {

        PendingMapFocus.location = location;
        expiresAtMs = nowMs + WINDOW_MS;
    }

    /**
     * Consumes the pending location if it is still inside the focus window.
     *
     * @return The pending location, or {@code null} when absent or expired.
     */
    @Nullable
    public static LocationClient consume() {

        return consume(System.currentTimeMillis());
    }

    /**
     * Consumes the pending location using a supplied clock value.
     *
     * @param nowMs The current time in milliseconds.
     * @return The pending location, or {@code null} when absent or expired.
     */
    @Nullable
    static synchronized LocationClient consume(long nowMs) {

        LocationClient pending = location;
        long expiresAt = expiresAtMs;
        clear();

        if (pending == null || nowMs > expiresAt) return null;

        return pending;
    }

    /**
     * Clears any pending map focus.
     */
    public static synchronized void clear() {

        location = null;
        expiresAtMs = 0L;
    }
}
