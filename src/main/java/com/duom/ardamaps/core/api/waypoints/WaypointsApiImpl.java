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

package com.duom.ardamaps.core.api.waypoints;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.api.waypoints.ApiWaypoint;
import com.duom.ardamaps.api.waypoints.IWaypointsApi;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal implementation of the waypoints API.
 */
@SuppressWarnings("unused")
public class WaypointsApiImpl implements IWaypointsApi {

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(WaypointsApiImpl.class);

    @Override
    public void addWaypoint(int x, int z, String text, float r, float g, float b, String identifier, String dimension) {
        addWaypoint(x, z, text, r, g, b, identifier, dimension, true, ModConstants.ICON_WAYPOINT);
    }

    @Override
    public void addWaypoint(int x, int z, String text, float r, float g, float b, String identifier, String dimension, boolean showToast, Identifier icon) {
        addWaypoint(new ApiWaypoint(x, z, text, r, g, b, identifier, dimension, showToast, icon));
    }

    @Override
    public void addWaypoint(ApiWaypoint waypoint) {
        if (waypoint == null) return;
        if (!validateWaypointArguments(waypoint.identifier(), waypoint.dimension())) return;

        ArdaMapsClient.CONFIG.setWaypoint(toWaypoint(waypoint));
    }

    @Override
    public void removeWaypoint(int x, int z, String identifier, String dimension) {
        removeWaypoint(new ApiWaypoint(x, z, "", -1, -1, -1, identifier, dimension));
    }

    @Override
    public void removeWaypoint(int x, int z, String identifier) {
        removeWaypoint(x, z, identifier, Client.currentDimensionId());
    }

    @Override
    public void removeWaypoint(ApiWaypoint waypoint) {
        if (waypoint == null) return;
        if (!validateWaypointArguments(waypoint.identifier(), waypoint.dimension())) return;

        ArdaMapsClient.CONFIG.removeWaypoint(toWaypoint(waypoint));
    }

    @Override
    public void removeWaypoints(String identifier) {
        if (identifier == null) {
            LOGGER.warn("[ArdaMapsApi] Identifier must not be null");
            return;
        }

        ArdaMapsClient.CONFIG.clearWaypointsByIdentifier(identifier);
    }

    /**
     * Converts a public waypoint DTO into the internal model.
     *
     * @param waypoint the public waypoint
     * @return the internal waypoint
     */
    public static Waypoint toWaypoint(ApiWaypoint waypoint) {
        return new Waypoint(waypoint.x(), waypoint.z(), waypoint.text(), waypoint.r(), waypoint.g(), waypoint.b(),
                waypoint.identifier(), waypoint.dimension(), waypoint.showToast(),
                waypoint.icon() != null ? waypoint.icon() : ModConstants.ICON_WAYPOINT);
    }

    /**
     * Converts an internal waypoint into the public DTO.
     *
     * @param waypoint the internal waypoint
     * @return the public waypoint
     */
    public static ApiWaypoint toApiWaypoint(Waypoint waypoint) {
        return new ApiWaypoint(waypoint.x(), waypoint.z(), waypoint.text(), waypoint.r(), waypoint.g(), waypoint.b(),
                waypoint.identifier(), waypoint.dimension(), waypoint.showToast(), waypoint.icon());
    }

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
}
