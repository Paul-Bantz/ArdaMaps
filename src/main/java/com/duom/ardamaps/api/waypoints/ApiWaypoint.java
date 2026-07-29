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

package com.duom.ardamaps.api.waypoints;

import net.minecraft.util.Identifier;

/**
 * Public waypoint DTO for external mods.
 *
 * @param x          the X coordinate of the waypoint
 * @param z          the Z coordinate of the waypoint
 * @param text       the waypoint display text
 * @param r          the red component of the waypoint colour
 * @param g          the green component of the waypoint colour
 * @param b          the blue component of the waypoint colour
 * @param identifier the waypoint owner's identifier
 * @param dimension  the waypoint dimension
 * @param showToast  whether to show a toast notification on hit
 * @param icon       the waypoint icon identifier
 */
public record ApiWaypoint(int x, int z,
                          String text,
                          float r, float g, float b,
                          String identifier, String dimension,
                          boolean showToast,
                          Identifier icon) {

    /**
     * Creates a waypoint that uses ArdaMaps' default toast and icon handling.
     */
    public ApiWaypoint(int x, int z, String text, float r, float g, float b, String identifier, String dimension) {
        this(x, z, text, r, g, b, identifier, dimension, true, null);
    }
}
