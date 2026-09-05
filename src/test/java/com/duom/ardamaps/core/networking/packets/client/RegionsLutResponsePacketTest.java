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

import com.duom.ardamaps.core.data.map.Region;
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RegionsLutResponsePacket} JSON/GZIP wire encoding and length validation.
 */
class RegionsLutResponsePacketTest {

    /**
     * Verifies that region lookup data round-trips through the packet without Java object serialization.
     */
    @Test
    void buildRead_roundTripsRegionLookupTextureAsJson() {

        RegionLookupTexture texture = new RegionLookupTexture(
                new byte[]{0, 1, 2, 1},
                new Region[]{new Region("r1", "Rohan"), new Region("r2", "Gondor")},
                2,
                2,
                "minecraft:overworld",
                new Date(987_654_000L));

        RegionsLutResponsePacket parsed = roundTrip(new RegionsLutResponsePacket(texture));

        assertNotNull(parsed.data());
        assertArrayEquals(texture.pixels(), parsed.data().pixels());
        assertArrayEquals(texture.regions(), parsed.data().regions());
        assertEquals(texture.texWidth(), parsed.data().texWidth());
        assertEquals(texture.texHeight(), parsed.data().texHeight());
        assertEquals(texture.dimensionId(), parsed.data().dimensionId());
        assertEquals(texture.lastUpdate(), parsed.data().lastUpdate());
    }

    /**
     * Serializes and deserializes a packet through its binary buffer representation.
     *
     * @param packet The packet to round-trip.
     * @return The packet parsed back from the built buffer.
     */
    private static RegionsLutResponsePacket roundTrip(RegionsLutResponsePacket packet) {

        var buf = packet.build();
        buf.readerIndex(0);
        return RegionsLutResponsePacket.read(buf);
    }

    /**
     * Verifies that negative lengths are rejected before byte-array allocation.
     */
    @Test
    void read_negativeDataLength_rejectsBeforeAllocation() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUUID(new UUID(0L, 0L));
        buf.writeInt(-1);
        buf.readerIndex(0);

        assertThrows(IllegalArgumentException.class, () -> RegionsLutResponsePacket.read(buf));
    }

    /**
     * Verifies that oversized lengths are rejected before byte-array allocation.
     */
    @Test
    void read_oversizedDataLength_rejectsBeforeAllocation() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUUID(new UUID(0L, 0L));
        buf.writeInt(8 * 1024 * 1024 + 1);
        buf.readerIndex(0);

        assertThrows(IllegalArgumentException.class, () -> RegionsLutResponsePacket.read(buf));
    }
}
