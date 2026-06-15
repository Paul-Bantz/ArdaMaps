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

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waypoints API contract definition for interacting with ArdaMaps waypoints.
 */
@SuppressWarnings("unused")
public class WaypointsApiImpl implements IWaypointsApi{

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(WaypointsApiImpl.class);

    /**
     * Adds a waypoint at a set position and dimension, with a given identifier.
     *
     * @param x          the X coordinate of the waypoint
     * @param z          the Z coordinate of the waypoint
     * @param identifier the waypoints identifier
     * @param dimension  the dimension the waypoint is in
     */
    public void addWaypoint(int x, int z, String text, float r, float g, float b, String identifier, String dimension) {

        addWaypoint(x, z, text, r, g, b, identifier, dimension, true, ModConstants.ICON_WAYPOINT);
    }

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
    public void addWaypoint(int x, int z, String text, float r, float g, float b, String identifier, String dimension, boolean showToast, Identifier icon) {

        addWaypoint(new Waypoint(x, z, text, r, g, b, identifier, dimension, showToast, icon));
    }

    /**
     * Adds the given waypoint to the map
     * @param waypoint the waypoint to add
     */
    public void addWaypoint(Waypoint waypoint) {

        if (waypoint == null) return;

        if(!validateWaypointArguments(waypoint.identifier(), waypoint.dimension())) return;

        ArdaMapsClient.CONFIG.setWaypoint(waypoint);
    }

    /**
     * Validates a waypoint creation argument
     * @param identifier the waypoint identifier
     * @param dimension the waypoint dimension
     * @return true if the parameters are valid, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateWaypointArguments(String identifier, String dimension) {

        String errorFormat = "[ArdaMapsApi] {} must not be null";

        if (dimension == null) {
            LOGGER.warn(errorFormat, "dimension");
            return false;
        }

        if (identifier == null) {
            LOGGER.warn(errorFormat, "identifier");
            return false;
        }

        if (ArdaMaps.MOD_ID.equals(identifier)) {

            LOGGER.warn("[ArdaMapsApi] Waypoint cannot be declared as {}", identifier);
            return false;
        }

        return true;
    }

    /**
     * Removes a waypoint at a set position for the given client dimension
     *
     * @param x          the X coordinate of the waypoint
     * @param z          the Z coordinate of the waypoint
     * @param identifier the waypoints identifier
     */
    public void removeWaypoint(int x, int z, String identifier, String dimension) {

        removeWaypoint(new Waypoint(x, z, "", -1, -1, -1, identifier, dimension));
    }

    /**
     * Removes a waypoint at a set position for the current client dimension
     *
     * @param x          the X coordinate of the waypoint
     * @param z          the Z coordinate of the waypoint
     * @param identifier the waypoints identifier
     */
    public void removeWaypoint(int x, int z, String identifier) {

        removeWaypoint(x, z, identifier, Client.currentDimensionId());
    }

    /**
     * Removes the given waypoint from the map
     * @param waypoint the waypoint to remove
     */
    public void removeWaypoint(Waypoint waypoint) {

        if (waypoint == null) return;

        if (!validateWaypointArguments(waypoint.identifier(), waypoint.dimension())) return;

        ArdaMapsClient.CONFIG.removeWaypoint(waypoint);
    }

    /**
     * Removes all the waypoints associated with the given identifier
     *
     * @param identifier the identifier to clear
     */
    public void removeWaypoints(String identifier) {

        if (identifier == null) {
            LOGGER.warn("[ArdaMapsApi] Identifier must not be null");
            return;
        }

        ArdaMapsClient.CONFIG.clearWaypointsByIdentifier(identifier);
    }
}
