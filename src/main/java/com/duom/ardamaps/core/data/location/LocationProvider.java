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

package com.duom.ardamaps.core.data.location;

import com.duom.ardamaps.ArdaMapsClient;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * LocationProvider is responsible for retrieving landmarks (locations) based on various criteria such as namespace, position, distance, and exploration status.
 * It interacts with the location configuration to fetch and filter landmarks accordingly.
 */
public class LocationProvider {

    /**
     * Retrieves the list of landmarks for a given namespace within a specified distance from a position.
     * Keyed by the squared distance to the given position
     * @param dimension       The dimension to retrieve landmarks for.
     * @param position    The position to measure distance from.
     * @param maxDistance The maximum distance to filter landmarks.
     * @param filter      Whether to filter out landmarks with position (0,0,0).
     * @return A map of landmarks within the specified distance from the position keyed by the distance to the position.
     */
    public static TreeMap<Double, LocationClient> getLocations(String dimension, Vec3d position, float maxDistance, boolean filter) {

        List<LocationClient> data = ArdaMapsClient.CONFIG.getLocationConfig().getLocations();

        if (data == null) return new TreeMap<>();

        TreeMap<Double, LocationClient> result = new TreeMap<>();
        var maxDistanceSq = maxDistance * maxDistance;

        for (LocationClient location : data) {

            // Skip locations that are in a different dimension than the player's current exploration state
            if (dimension != null && !Objects.equals(location.getWorld(), dimension)) continue;

            var pos = location.getPosition();

            if (pos == null) continue;
            if (filter && pos.getX() == 0d && pos.getY() == 0d && pos.getZ() == 0d) continue;

            var squaredDistance = position.squaredDistanceTo(pos);

            // Use squared distance to avoid sqrt for performance
            if (squaredDistance <= maxDistanceSq) {

                result.put(squaredDistance, location);
            }
        }

        return result;
    }
}