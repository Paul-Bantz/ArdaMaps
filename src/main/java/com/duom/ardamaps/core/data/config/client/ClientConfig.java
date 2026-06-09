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

package com.duom.ardamaps.core.data.config.client;

import com.duom.ardamaps.core.data.UnitSystem;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.shared.Configuration;
import com.duom.ardamaps.core.data.conversion.DistanceUnitConverter;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Configuration settings specific to the client.
 */
public class ClientConfig extends Configuration<LocationClient> {

    /** Toposcope draw distance in km (metric) or miles (imperial) - defaults to 50 km */
    @Setter
    @Getter
    @SerializedName("toposcope_draw_distance")
    private float toposcopeDrawDistance = 50f;

    /** Compass draw distance in km (metric) or miles (imperial) - defaults to 50 km */
    @Setter
    @Getter
    @SerializedName("compass_draw_distance")
    private float compassDrawDistance = 50f;

    /** Unit system for distance display - defaults to metric */
    @Getter
    @SerializedName("unit_system")
    private UnitSystem unitSystem;

    /** Whether to display map debug information - defaults to false */
    @Getter
    @SerializedName("map_debug_display")
    private boolean mapDebugDisplay;

    /** Whether to reveal the entire map by default - defaults to false */
    @Setter
    @Getter
    @SerializedName("map_reveal_all")
    private boolean mapRevealAll = false;

    /** Compass opacity - defaults to 1.0 (fully opaque) */
    @Setter
    @Getter
    @SerializedName("compass_opacity")
    private float compassOpacity = 1.0f;

    /**
     * The last page opened in the ArdaMaps GUI, stored as a deep-link string.
     * Format: {@code "guide"} (landing), {@code "guide:map"}, {@code "guide:configuration"},
     * or {@code "guide:page:<pageId>/<entryId>"}. Defaults to {@code "guide"}.
     */
    @Setter
    @SerializedName("last_page")
    private String lastPage = "guide";

    /** Client exploration progress - persisted in an external file */
    @Setter
    @Getter
    private transient ClientProgress clientProgress;

    /**
     * Dimensions definition, Client Side this is session scoped, the configuration is in sync with the server.
     * It is set up as the client start when querying the server.
     */
    @Setter
    @Getter
    private transient List<Dimension> dimensions;

    /** Waypoints coordinates - not persisted */
    private final transient HashMap<String, Set<Waypoint>> waypoints = new HashMap<>();

    /**
     * Returns the toposcope draw distance converted to in-game blocks based on the current unit system and the provided dimension definition.
     *
     * @param dimension The dimension definition to use for the conversion.
     * @return The toposcope draw distance converted to in-game blocks.
     */
    public int getToposcopeDrawDistanceBlocks(Dimension dimension) {

        if (unitSystem == UnitSystem.IMPERIAL)
            return (int) DistanceUnitConverter.milesToBlocks(dimension, toposcopeDrawDistance);

        return (int) DistanceUnitConverter.kmToBlocks(dimension, toposcopeDrawDistance);
    }

    /**
     * Returns the dimension definition for the given dimension ID, or a default definition if not found.
     *
     * @param dimensionId The dimension ID to look up (e.g. "minecraft:overworld").
     * @return The Dimension for the given dimension ID, or a default definition if not found.
     */
    public @Nullable Dimension getDimension(String dimensionId) {

        if (dimensions == null || dimensions.isEmpty())
            return null;

        return dimensions.stream().filter(dimension -> dimension.getId().equals(dimensionId)).findFirst().orElse(null);
    }

    /**
     * Sets the unit system. When switching, converts stored draw distances to the new unit.
     */
    public void setUnitSystem(UnitSystem newSystem) {

        if (this.unitSystem != null && this.unitSystem != newSystem) {

            if (newSystem == UnitSystem.IMPERIAL) {

                toposcopeDrawDistance *= DistanceUnitConverter.KM_TO_MILES;
                compassDrawDistance *= DistanceUnitConverter.KM_TO_MILES;

            } else {

                toposcopeDrawDistance /= DistanceUnitConverter.KM_TO_MILES;
                compassDrawDistance /= DistanceUnitConverter.KM_TO_MILES;

            }
        }
        this.unitSystem = newSystem;
    }

    /**
     * Returns the compass draw distance converted to in-game blocks based on the current unit system and the provided dimension definition.
     *
     * @param dimension The dimension definition to use for the conversion.
     * @return The compass draw distance converted to in-game blocks.
     */
    public int getCompassDrawDistanceBlocks(Dimension dimension) {
        if (unitSystem == UnitSystem.IMPERIAL)
            return (int) DistanceUnitConverter.milesToBlocks(dimension, compassDrawDistance);
        return (int) DistanceUnitConverter.kmToBlocks(dimension, compassDrawDistance);
    }

    /**
     * Returns the waypoints for the given dimension ID and coordinates, or an empty set if none are found.
     *
     * @param dimensionId the dimension to look the waypoint for
     * @param x           the x coordinate
     * @param z           the z coordinate
     * @param radius      the matching radius in blocks to consider a hit
     * @return the waypoint or an empty set if none are found
     */
    public Optional<Waypoint> getWaypointAtCoordinates(String dimensionId, double x, double z, double radius) {

        if (!waypoints.containsKey(dimensionId))
            return Optional.empty();

        for (Waypoint waypoint : waypoints.get(dimensionId)) {

            double dx = waypoint.x() - x;
            double dz = waypoint.z() - z;

           if  (Math.sqrt(dx * dx + dz * dz) <= radius)
               return Optional.of(waypoint);
        }

        return Optional.empty();
    }

    /**
     * Sets the current waypoint coordinates for the client.
     *
     * @param x The X coordinate of the waypoint.
     * @param z The Z coordinate of the waypoint.
     *          This method allows you to set a waypoint on the client map by specifying its X and Z coordinates. The waypoint can be used for navigation purposes, allowing players to mark specific locations they want to reach or remember on the map. The coordinates should be in the same coordinate system used by the game world.
     */
    public void setWaypoint(double x, double z, String dimensionId) {

        var waypoint = new Waypoint((int) x, (int) z, dimensionId);

        var waypointsForDimension = waypoints.computeIfAbsent(dimensionId, waypointDimensionId -> new HashSet<>());
        waypointsForDimension.removeIf(wp -> Objects.equals(wp.dimension(), dimensionId) && Objects.equals(wp.identifier(), waypoint.identifier()));
        waypointsForDimension.add(waypoint);
    }

    /**
     * Removes all the waypoints associated with the given identifier
     * @param identifier the identifier for which to remove the waypoints.
     */
    public void clearWaypointsByIdentifier(String identifier) {

        for (Set<Waypoint> waypointsForDimension : waypoints.values()) {

            waypointsForDimension.removeIf(waypoint -> Objects.equals(waypoint.identifier(), identifier));
        }
    }

    /**
     * Sets the given waypoint in the given dimension
     *
     * @param waypoint the waypoint to set
     */
    public void setWaypoint(Waypoint waypoint) {

        var waypointsForDimension = waypoints.computeIfAbsent(waypoint.dimension(), waypointDimensionId -> new HashSet<>());

        boolean alreadyHaveWaypointAtPosition = false;

        // Ensure the client doesn't add again a set waypoint
        for (Waypoint waypointForDimension : waypointsForDimension) {

            if (waypoint.x() == waypointForDimension.x() && waypoint.z() == waypointForDimension.z()) {
                alreadyHaveWaypointAtPosition = true;
                break;
            }
        }

        if (!alreadyHaveWaypointAtPosition) {

            // Remove any existing waypoint with the same identifier (identifier is the discriminant)
            waypointsForDimension.remove(waypoint);
            waypointsForDimension.add(waypoint);
        }
    }

    /**
     * Removes the waypoint with the specified identifier from the client map.
     *
     * @param waypoint the waypoint to remove, identified by its unique identifier (e.g. "arda_maps").
     */
    public void removeWaypoint(Waypoint waypoint) {

        // Sanity check
        if (waypoint == null) return;

        var waypointsForDim = waypoints.get(waypoint.dimension());

        if (waypointsForDim == null) return;

        waypointsForDim.remove(waypoint);
    }

    /**
     * Clears all the waypoints for a given dimension
     *
     * @param dimensionId the dimension id to clear the waypoints from
     */
    public void clearWaypoints(String dimensionId) {

        waypoints.remove(dimensionId);
    }

    /**
     * @return Whether a valid waypoint is currently set on the client map. A waypoint is considered valid if both X and Z coordinates are not NaN (Not a Number).
     * This method checks if there is an active waypoint set by verifying that both the X and Z coordinates are valid numbers. If either coordinate is NaN, it indicates that there is no active waypoint, and the method will return false. If both coordinates are valid, it means a waypoint is currently set, and the method will return true.
     */
    public boolean hasWaypoint(String dimensionId) {

        return waypoints.get(dimensionId) != null && !waypoints.get(dimensionId).isEmpty();
    }

    /**
     * Returns all the waypoints for the given dimension.
     *
     * @param dimensionId the dimension ID to get waypoints for
     * @return all the waypoints for the dimension
     */
    public @NotNull Set<Waypoint> getWaypoints(String dimensionId) {

        var waypointsForDim = waypoints.get(dimensionId);

        return waypointsForDim == null ? Set.of() : waypointsForDim;
    }

    /**
     * @return The deep-link string representing the last page opened in the ArdaMaps GUI.
     * Format: {@code "guide"} (landing), {@code "guide:map"}, {@code "guide:configuration"},
     * or {@code "guide:page:<pageId>/<entryId>"}. Defaults to {@code "guide"}.
     */
    public String getLastPage() {
        return lastPage == null || lastPage.isBlank() ? "guide" : lastPage;
    }

}