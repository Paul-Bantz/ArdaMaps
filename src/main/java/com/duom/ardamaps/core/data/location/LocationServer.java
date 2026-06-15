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

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * Data model representing a location within Arda's World
 */
@Setter
@Getter
@SuppressWarnings("unused")
public class LocationServer extends BasicLocation implements Serializable {

    /** Serialization version UID for compatibility */
    @SerializedName("status")
    private String status;

    /** List of region names this location belongs to */
    @SerializedName("regions")
    private List<String> regions;

    /** Whether this location is canon or not */
    @SerializedName("canon")
    private boolean canon;

    /** A detailed description of the location, which may include lore, history, or other information  */
    @SerializedName("description")
    private String description;

    /** An external URL with more information about the location, such as a wiki page or official website */
    @SerializedName("external_url")
    private String externalUrl;

    /**
     * Converts a LocationServer object to a LocationClient object
     *
     * @param server The LocationServer instance
     * @return The corresponding LocationClient instance
     */
    public static LocationClient toLocationClient(LocationServer server) {

        LocationClient client = new LocationClient();

        // Copy BasicLocation fields
        client.setId(server.getId());
        client.setName(server.name);
        client.setWorld(server.world);
        client.setTypes(server.types);
        client.setWarp(server.warp);
        client.setPosition(server.position);
        client.setPathfinder(server.pathfinder);

        return client;
    }
}