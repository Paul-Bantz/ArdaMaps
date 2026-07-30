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

import com.duom.ardamaps.core.data.location.BasicLocation;
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.location.LocationClient;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LocationsResponsePacket} JSON/GZIP wire encoding and length validation.
 */
class LocationsResponsePacketTest {

    /**
     * Verifies that location data round-trips through the packet without Java object serialization.
     */
    @Test
    void buildRead_roundTripsLocationConfigAsJson() {

        LocationClient location = new LocationClient();
        location.setId("loc-1");
        location.setName("Amon Sul");
        location.setWorld("minecraft:overworld");
        location.setTypes(List.of("ruin"));
        location.setWarp("weathertop");
        location.setPathfinder("path-1");
        location.setPosition(new Vec3d(12.5, 64, -8.25));

        LocationConfig<LocationClient> config = new LocationConfig<>();
        config.setLastUpdate(new Date(123_456_000L));
        config.setLocations(List.of(location));

        LocationsResponsePacket parsed = roundTrip(new LocationsResponsePacket(config));

        assertNotNull(parsed.data());
        assertEquals(config.getLastUpdate(), parsed.data().getLastUpdate());
        assertEquals(1, parsed.data().getLocations().size());
        LocationClient parsedLocation = parsed.data().getLocations().get(0);
        assertEquals(location.getId(), parsedLocation.getId());
        assertEquals(locationName(location), locationName(parsedLocation));
        assertEquals(location.getWorld(), parsedLocation.getWorld());
        assertEquals(location.getTypes(), parsedLocation.getTypes());
        assertEquals(location.getWarp(), parsedLocation.getWarp());
        assertEquals(location.getPathfinder(), parsedLocation.getPathfinder());
        assertEquals(location.getPosition(), parsedLocation.getPosition());
    }

    /**
     * Negative lengths must be rejected before byte-array allocation.
     */
    @Test
    void read_negativeDataLength_rejectsBeforeAllocation() {

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(-1);
        buf.readerIndex(0);

        assertThrows(IllegalArgumentException.class, () -> LocationsResponsePacket.read(buf));
    }

    /**
     * Oversized lengths must be rejected before byte-array allocation.
     */
    @Test
    void read_oversizedDataLength_rejectsBeforeAllocation() {

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(8 * 1024 * 1024 + 1);
        buf.readerIndex(0);

        assertThrows(IllegalArgumentException.class, () -> LocationsResponsePacket.read(buf));
    }

    /**
     * Serializes and deserializes a packet through its binary buffer representation.
     *
     * @param packet The packet to round-trip.
     * @return The packet parsed back from the built buffer.
     */
    private static LocationsResponsePacket roundTrip(LocationsResponsePacket packet) {

        var buf = packet.build();
        buf.readerIndex(0);
        return LocationsResponsePacket.read(buf);
    }

    /**
     * Reads the raw serialized name field without invoking LocationClient's runtime exploration-aware getter.
     *
     * @param location The location to inspect.
     * @return The raw location name.
     */
    private static String locationName(LocationClient location) {

        try {
            var field = BasicLocation.class.getDeclaredField("name");
            field.setAccessible(true);
            return (String) field.get(location);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
