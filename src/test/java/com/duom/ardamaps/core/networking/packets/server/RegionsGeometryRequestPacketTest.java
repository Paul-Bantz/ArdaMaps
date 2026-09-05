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

package com.duom.ardamaps.core.networking.packets.server;

import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for region geometry request wire encoding.
 */
class RegionsGeometryRequestPacketTest {

    /**
     * Verifies known geometry timestamps round-trip through the packet codec.
     */
    @Test
    void buildRead_roundTripsKnownGeometryMap() {

        Map<String, Date> knownGeometry = new LinkedHashMap<>();
        knownGeometry.put("minecraft:overworld", new Date(1000L));
        knownGeometry.put("minecraft:the_nether", new Date(2000L));

        RegionsGeometryRequestPacket parsed = roundTrip(new RegionsGeometryRequestPacket(knownGeometry));

        assertEquals(knownGeometry, parsed.knownGeometry());
    }

    /**
     * Verifies an empty known geometry map is preserved as the full-refresh request shape.
     */
    @Test
    void buildRead_roundTripsEmptyKnownGeometryMap() {

        RegionsGeometryRequestPacket parsed = roundTrip(new RegionsGeometryRequestPacket(Map.of()));

        assertEquals(Map.of(), parsed.knownGeometry());
    }

    /**
     * Verifies oversized dimension counts are rejected before reading entries.
     */
    @Test
    void read_oversizedDimensionCount_rejectsBeforeReadingEntries() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUUID(new UUID(0L, 0L));
        buf.writeVarInt(RegionsGeometryRequestPacket.MAX_DIMENSIONS + 1);
        buf.readerIndex(0);

        assertThrows(IllegalArgumentException.class, () -> RegionsGeometryRequestPacket.read(buf));
    }

    /**
     * Serializes and deserializes a packet through its binary buffer representation.
     *
     * @param packet The packet to round-trip.
     * @return The packet parsed back from the built buffer.
     */
    private static RegionsGeometryRequestPacket roundTrip(RegionsGeometryRequestPacket packet) {

        var buf = packet.build();
        buf.readerIndex(0);
        return RegionsGeometryRequestPacket.read(buf);
    }
}
