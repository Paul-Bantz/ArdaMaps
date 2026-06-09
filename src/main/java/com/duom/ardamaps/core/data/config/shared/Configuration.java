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

package com.duom.ardamaps.core.data.config.shared;

import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.location.BasicLocation;
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Abstract base class for configuration classes that manage location data.
 * This class provides common functionality for handling location configurations,
 * such as retrieving locations and filtering them by world and type.
 *
 * @param <T> The type of BasicLocation that this configuration manages.
 */
@Setter
@Getter
public abstract class Configuration<T extends BasicLocation> {

    /**
     * Location data is persisted in another file
     * Managed by ConfigManager
     */
    protected transient LocationConfig<T> locationConfig;

    /**
     * Region lookup texture is persisted in another file.
     * Managed by ConfigManager
     */
    protected transient RegionLookupTexture regionLookupTexture;

    /**
     * Get the given location from its id
     * @param locationId the identifier of the location to get
     * @return the location
     */
    public T getLocation(String locationId) {

        if (locationId == null) return null;

        return locationConfig.getLocations().stream()
                .filter(location -> location.getId().equals(locationId))
                .findFirst()
                .orElse(null);

    }

    /**
     * Get all locations
     *
     * @return A list of all locations
     */
    public List<T> getLocations() {
        return locationConfig.getLocations();
    }

    /**
     * Get all locations in a specific worldId
     *
     * @param worldId The worldId id to get locations from
     * @return A list of locations in the specified worldId
     */
    public List<T> getLocations(String worldId, String type) {

        if (worldId == null) return List.of();
        if (type != null) {
            return locationConfig.getLocations().stream()
                    .filter(location -> location.getWorld().equals(worldId) && location.getTypes().contains(type))
                    .toList();
        }

        return locationConfig.getLocations().stream()
                .filter(location -> location.getWorld().equals(worldId))
                .toList();
    }
}