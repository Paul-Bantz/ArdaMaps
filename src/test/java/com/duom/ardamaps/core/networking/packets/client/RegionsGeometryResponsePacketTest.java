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

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.data.map.region.RegionShape;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for region geometry binary GZIP wire encoding.
 */
class RegionsGeometryResponsePacketTest {

    /**
     * Verifies geometries and server dimension ids round-trip through the binary packet codec.
     */
    @Test
    void buildRead_roundTripsRegionGeometriesAndServerDimensionIds() {

        RegionShape shape = new RegionShape("r1", "Rohan", null, 0,
                new int[][]{{-10, -10, 20, -10, 20, 20, -10, 20}},
                -10, -10, 20, 20, new Vec2d(5, 5), 15.0);
        RegionGeometry geometry = new RegionGeometry("minecraft:overworld", new RegionShape[]{shape}, new Date(987_654_000L));
        RegionGeometry netherGeometry = new RegionGeometry("minecraft:the_nether", new RegionShape[0], new Date(987_655_000L));

        RegionsGeometryResponsePacket parsed = roundTrip(new RegionsGeometryResponsePacket(
                List.of(geometry, netherGeometry),
                List.of("minecraft:overworld", "minecraft:the_nether", "test:gone")));

        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "test:gone"), parsed.serverDimensionIds());
        assertEquals(2, parsed.geometries().size());
        assertEquals(geometry.dimensionId(), parsed.geometries().getFirst().dimensionId());
        assertEquals(geometry.lastUpdate(), parsed.geometries().getFirst().lastUpdate());
        assertEquals(1, parsed.geometries().getFirst().regions().length);
        assertEquals(shape.id(), parsed.geometries().getFirst().regions()[0].id());
        assertArrayEquals(shape.rings()[0], parsed.geometries().getFirst().regions()[0].rings()[0]);
        assertEquals(netherGeometry.dimensionId(), parsed.geometries().get(1).dimensionId());
    }

    /**
     * Verifies the empty sentinel survives the binary packet codec.
     */
    @Test
    void buildRead_emptySentinelRoundTripsAsEmptyLists() {

        RegionsGeometryResponsePacket parsed = roundTrip(RegionsGeometryResponsePacket.EMPTY);

        assertNotNull(parsed.geometries());
        assertNotNull(parsed.serverDimensionIds());
        assertEquals(List.of(), parsed.geometries());
        assertEquals(List.of(), parsed.serverDimensionIds());
    }

    /**
     * Verifies negative lengths are rejected before allocation.
     */
    @Test
    void read_negativeDataLength_rejectsBeforeAllocation() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUUID(new UUID(0L, 0L));
        buf.writeInt(-1);
        buf.readerIndex(0);

        assertThrows(IllegalArgumentException.class, () -> RegionsGeometryResponsePacket.read(buf));
    }

    /**
     * Verifies oversized lengths are rejected before allocation.
     */
    @Test
    void read_oversizedDataLength_rejectsBeforeAllocation() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUUID(new UUID(0L, 0L));
        buf.writeInt(8 * 1024 * 1024 + 1);
        buf.readerIndex(0);

        assertThrows(IllegalArgumentException.class, () -> RegionsGeometryResponsePacket.read(buf));
    }

    /**
     * Serializes and deserializes a packet through its binary buffer representation.
     *
     * @param packet The packet to round-trip.
     * @return The packet parsed back from the built buffer.
     */
    private static RegionsGeometryResponsePacket roundTrip(RegionsGeometryResponsePacket packet) {

        var buf = packet.build();
        buf.readerIndex(0);
        return RegionsGeometryResponsePacket.read(buf);
    }
}
