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

package com.duom.ardamaps.core.networking.packets.client;

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.config.MapLayerSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MapSourceResponsePacket} serialization of flat and ranged layer definitions.
 */
class MapSourceResponsePacketTest {

    /**
     * Verifies that packet round-tripping preserves a flat layer's scalar fields and keeps ranges null.
     * This protects the non-ranged serialization path from accidentally manufacturing empty range metadata.
     */
    @Test
    void buildRead_roundTripsLayerWithoutRanges() {

        Dimension dimension = dimension();
        dimension.getMapLayers().add(layer(null));

        MapSourceResponsePacket parsed = roundTrip(new MapSourceResponsePacket(true, false, List.of(dimension)));

        assertTrue(parsed.warpsAvailable());
        assertFalse(parsed.ardaRegionsAvailable());
        assertEquals(1, parsed.dimensions().size());
        assertEquals(layer(null), parsed.dimensions().get(0).getMapLayers().get(0));
        assertNull(parsed.dimensions().get(0).getMapLayers().get(0).ranges());
    }

    /**
     * Creates a minimal dimension fixture for packet round-trip tests.
     *
     * @return A mutable dimension with no map layers attached yet.
     */
    private static Dimension dimension() {

        return new Dimension("Test", "test:dimension", 1f, 0, 1000, 0, 1000, false);
    }

    /**
     * Creates a layer fixture whose top-level path is present only for flat layers.
     *
     * @param ranges The optional ranged path configuration, or null for a flat layer.
     * @return A PMTiles layer definition suitable for packet serialization tests.
     */
    private static MapLayerDefinition layer(List<MapLayerRange> ranges) {

        return new MapLayerDefinition("Layer", MapLayerSource.PMTILES, true, 8, 7d, 2.0,
                1, 3, 1, 14, 512, 1.25, ranges == null ? "fallback.pmtiles" : null, "fallback.png", ranges);
    }

    /**
     * Serializes and deserializes a packet through its binary buffer representation.
     *
     * @param packet The packet to round-trip.
     * @return The packet parsed back from the built buffer.
     */
    private static MapSourceResponsePacket roundTrip(MapSourceResponsePacket packet) {

        var buf = packet.build();
        buf.readerIndex(0);
        return MapSourceResponsePacket.read(buf);
    }

    /**
     * Verifies that packet round-tripping preserves range lists and the null top-level path used by ranged layers.
     * This matters because ranged layers encode their paths per-band, so flattening them would break client layer loading.
     */
    @Test
    void buildRead_roundTripsLayerWithRanges() {

        List<MapLayerRange> ranges = List.of(
                new MapLayerRange(0, "low.pmtiles", -64, 0),
                new MapLayerRange(1, "high.pmtiles", 1, 128));
        Dimension dimension = dimension();
        dimension.getMapLayers().add(layer(ranges));

        MapSourceResponsePacket parsed = roundTrip(new MapSourceResponsePacket(false, true, List.of(dimension)));

        assertFalse(parsed.warpsAvailable());
        assertTrue(parsed.ardaRegionsAvailable());
        assertEquals(ranges, parsed.dimensions().get(0).getMapLayers().get(0).ranges());
        assertNull(parsed.dimensions().get(0).getMapLayers().get(0).path());
        assertEquals(layer(ranges), parsed.dimensions().get(0).getMapLayers().get(0));
    }
}
