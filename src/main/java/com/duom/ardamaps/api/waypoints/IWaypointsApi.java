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

import com.duom.ardamaps.core.data.map.Waypoint;
import net.minecraft.util.Identifier;

/**
 * API Contract for interacting with ArdaMaps waypoints.
 */
@SuppressWarnings("unused")
public interface IWaypointsApi {

    /**
     * Adds a waypoint at a set position and dimension, with a given identifier.
     *
     * @param x          the X coordinate of the waypoint
     * @param z          the Z coordinate of the waypoint
     * @param identifier the waypoints identifier
     * @param dimension  the dimension the waypoint is in
     */
    void addWaypoint(int x, int z, String text, float r, float g, float b, String identifier, String dimension);

    /**
     * Adds a waypoint at a set position and dimension, with a given identifier.
     *
     * @param x          the X coordinate of the waypoint
     * @param z          the Z coordinate of the waypoint
     * @param identifier the waypoints identifier
     * @param dimension  the dimension the waypoint is in
     * @param showToast  whether to show a Toast notification on hit
     * @param icon       identifier of the icon texture to show as the waypoint
     */
    void addWaypoint(int x, int z, String text, float r, float g, float b, String identifier, String dimension, boolean showToast, Identifier icon);

    /**
     * Adds the given waypoint to the map
     *
     * @param waypoint the waypoint to add
     */
    void addWaypoint(Waypoint waypoint);

    /**
     * Removes a waypoint at a set position for the given client dimension
     *
     * @param x          the X coordinate of the waypoint
     * @param z          the Z coordinate of the waypoint
     * @param identifier the waypoints identifier
     */
    void removeWaypoint(int x, int z, String identifier, String dimension);

    /**
     * Removes a waypoint at a set position for the current client dimension
     *
     * @param x          the X coordinate of the waypoint
     * @param z          the Z coordinate of the waypoint
     * @param identifier the waypoints identifier
     */
    void removeWaypoint(int x, int z, String identifier);

    /**
     * Removes the given waypoint from the map
     *
     * @param waypoint the waypoint to remove
     */
    void removeWaypoint(Waypoint waypoint);

    /**
     * Removes all the waypoints associated with the given identifier
     *
     * @param identifier the identifier to clear
     */
    void removeWaypoints(String identifier);
}
