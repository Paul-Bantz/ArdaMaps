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
import com.duom.ardamaps.core.data.config.server.ServerConfig;
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.data.map.region.RegionShape;
import com.duom.ardamaps.core.networking.packets.client.RegionsGeometryResponsePacket;
import com.duom.ardamaps.core.networking.packets.server.RegionsGeometryRequestPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for region geometry request filtering.
 */
class RegionsGeometryRequestHandlerTest {

    /** Previous server config restored after each test. */
    private ServerConfig previousConfig;

    /**
     * Stores the previous static config.
     */
    @BeforeEach
    void setUp() {

        previousConfig = ArdaMaps.CONFIG;
        ArdaMaps.CONFIG = new ServerConfig();
    }

    /**
     * Restores the previous static config.
     */
    @AfterEach
    void tearDown() {

        ArdaMaps.CONFIG = previousConfig;
    }

    /**
     * Verifies absent and stale client geometry is sent, fresh client geometry is skipped, and unbuilt server geometry is omitted.
     */
    @Test
    void handle_filtersGeometryByClientTimestampsAndOmitsUnbuiltGeometry() {

        Date serverDate = new Date(2000L);
        ArdaMaps.CONFIG.setRegionGeometry(geometry("test:absent", serverDate));
        ArdaMaps.CONFIG.setRegionGeometry(geometry("test:older", serverDate));
        ArdaMaps.CONFIG.setRegionGeometry(geometry("test:equal", serverDate));
        ArdaMaps.CONFIG.setRegionGeometry(geometry("test:newer", serverDate));
        ArdaMaps.CONFIG.setRegionGeometry(geometry("test:unbuilt", null));

        RegionsGeometryRequestPacket packet = new RegionsGeometryRequestPacket(Map.of(
                "test:older", new Date(1000L),
                "test:equal", new Date(2000L),
                "test:newer", new Date(3000L)
        ));

        RegionsGeometryResponsePacket response = new RegionsGeometryRequestHandler().handle(null, null, packet);

        assertEquals(Set.of("test:absent", "test:older"), dimensionIds(response.geometries()));
        assertEquals(Set.of("test:absent", "test:older", "test:equal", "test:newer"),
                Set.copyOf(response.serverDimensionIds()));
    }

    /**
     * Creates geometry for a test dimension.
     *
     * @param dimensionId The dimension identifier.
     * @param lastUpdate  The last update timestamp.
     * @return A geometry object with no shapes.
     */
    private static RegionGeometry geometry(String dimensionId, Date lastUpdate) {

        return new RegionGeometry(dimensionId, new RegionShape[0], lastUpdate);
    }

    /**
     * Collects dimension identifiers from geometries.
     *
     * @param geometries The geometries to inspect.
     * @return The geometry dimension identifiers.
     */
    private static Set<String> dimensionIds(Iterable<RegionGeometry> geometries) {

        return java.util.stream.StreamSupport.stream(geometries.spliterator(), false)
                .map(RegionGeometry::dimensionId)
                .collect(Collectors.toSet());
    }
}
