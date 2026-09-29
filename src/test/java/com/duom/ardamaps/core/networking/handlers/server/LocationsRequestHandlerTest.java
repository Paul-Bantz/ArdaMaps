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

package com.duom.ardamaps.core.networking.handlers.server;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.DimensionRemap;
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.config.server.ServerConfig;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.location.LocationServer;
import com.duom.ardamaps.core.networking.packets.client.LocationsResponsePacket;
import com.duom.ardamaps.core.networking.packets.server.LocationsRequestPacket;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link LocationsRequestHandler}.
 */
class LocationsRequestHandlerTest {

    /**
     * Locations from remapped worlds are sent to clients under the owning dimension ID.
     */
    @Test
    void handle_remappedWorld_sendsOwningDimensionId() {

        ServerConfig previousConfig = ArdaMaps.CONFIG;
        try {
            ArdaMaps.CONFIG = serverConfig();

            LocationsResponsePacket response = new LocationsRequestHandler().handle(
                    null,
                    null,
                    null,
                    new LocationsRequestPacket(new Date(0L)),
                    null);

            assertNotNull(response.data());
            List<LocationClient> locations = response.data().getLocations();
            assertEquals(1, locations.size());
            assertEquals("minecraft:overworld", locations.get(0).getWorld());
        } finally {
            ArdaMaps.CONFIG = previousConfig;
        }
    }

    /**
     * Creates a server config with one remapped server location.
     *
     * @return The server config.
     */
    private static ServerConfig serverConfig() {

        LocationServer location = new LocationServer();
        location.setId("loc-1");
        location.setName("Freebuild Marker");
        location.setWorld("multiworld:freebuild");

        LocationConfig<LocationServer> locationConfig = new LocationConfig<>();
        locationConfig.setLastUpdate(new Date(123_456L));
        locationConfig.setLocations(List.of(location));

        ServerConfig config = new ServerConfig();
        config.setDimensions(List.of(new Dimension("Overworld", "minecraft:overworld", 1f, 0, 10, 0, 10, false, false,
                List.of(new DimensionRemap("multiworld:freebuild")))));
        config.setLocationConfig(locationConfig);
        return config;
    }
}
