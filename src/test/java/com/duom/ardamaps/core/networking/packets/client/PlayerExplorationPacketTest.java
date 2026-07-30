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

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PlayerExplorationPacket} malformed polygon count handling.
 */
class PlayerExplorationPacketTest {

    /**
     * Negative polygon counts are rejected by the parser and surfaced as an empty packet.
     */
    @Test
    void read_negativePolygonCount_returnsEmptyPacket() {

        var buf = PacketByteBufs.create();
        buf.writeUtf("minecraft:overworld");
        buf.writeUtf("region-1");
        buf.writeVarInt(-1);
        buf.readerIndex(0);

        assertTrue(PlayerExplorationPacket.read(buf).isEmpty());
    }

    /**
     * Oversized polygon counts are rejected by the parser and surfaced as an empty packet.
     */
    @Test
    void read_oversizedPolygonCount_returnsEmptyPacket() {

        var buf = PacketByteBufs.create();
        buf.writeUtf("minecraft:overworld");
        buf.writeUtf("region-1");
        buf.writeVarInt(1025);
        buf.readerIndex(0);

        assertTrue(PlayerExplorationPacket.read(buf).isEmpty());
    }
}
