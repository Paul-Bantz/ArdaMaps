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

package com.duom.ardamaps.core.data.trail;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for persisted movement-trail compression.
 */
class TrailCodecTest {

    /**
     * Verifies multi-segment trails round-trip through the compressed codec.
     *
     * @throws IOException if decoding fails.
     */
    @Test
    void encodeDecode_multiSegmentTrail_roundTrips() throws IOException {

        PlayerTrail trail = new PlayerTrail();
        trail.record(-10, 20, MovementMode.WALK);
        trail.record(40, 20, MovementMode.WALK);
        trail.record(45, -30, MovementMode.SWIM);
        trail.breakSegment();

        Map<String, PlayerTrail> decoded = TrailCodec.decode(TrailCodec.encode(Map.of("minecraft:overworld", trail)));
        PlayerTrail decodedTrail = decoded.get("minecraft:overworld");

        assertNotNull(decodedTrail);
        assertEquals(trail.pointCount(), decodedTrail.pointCount());
        assertEquals(2, decodedTrail.segments().size());
        assertEquals(MovementMode.SWIM, decodedTrail.segments().getLast().mode());
        assertEquals(45, decodedTrail.segments().getLast().x(0));
        assertEquals(-30, decodedTrail.segments().getLast().z(0));
    }

    /**
     * Verifies same-mode gap segments and their flags survive a codec round-trip.
     *
     * @throws IOException if decoding fails.
     */
    @Test
    void encodeDecode_sameModeGapSegments_preservesBoundariesAndFlags() throws IOException {

        PlayerTrail trail = new PlayerTrail();
        trail.record(0, 0, MovementMode.FLY);
        trail.record(10, 0, MovementMode.FLY);
        trail.breakForJump(10, 0, MovementMode.FLY);
        trail.record(2_000, 0, MovementMode.FLY);
        trail.record(2_010, 0, MovementMode.FLY);
        trail.breakSegment();

        PlayerTrail decoded = TrailCodec.decode(TrailCodec.encode(Map.of("minecraft:the_end", trail)))
                .get("minecraft:the_end");

        assertNotNull(decoded);
        assertEquals(2, decoded.segments().size());
        assertEquals(MovementMode.FLY, decoded.segments().getFirst().mode());
        assertEquals(MovementMode.FLY, decoded.segments().getLast().mode());
        assertTrue(decoded.segments().getFirst().endsAtGap());
        assertTrue(decoded.segments().getLast().startsAtGap());
        assertEquals(10, decoded.segments().getFirst().x(decoded.segments().getFirst().size() - 1));
        assertEquals(2_000, decoded.segments().getLast().x(0));
    }

    /**
     * Verifies non-trail payloads are rejected.
     */
    @Test
    void decode_invalidMagic_throws() {

        byte[] payload = {'A', 'M', 'X', '1'};

        assertThrows(IOException.class, () -> TrailCodec.decode(payload));
    }

    /**
     * Verifies oversized declared payload lengths are rejected before allocation.
     */
    @Test
    void decode_oversizedDeclaredLength_throws() {

        byte[] payload = ByteBuffer.allocate(8)
                .put(new byte[]{'A', 'M', 'T', '1'})
                .putInt(64 * 1024 * 1024 + 1)
                .array();

        assertThrows(IllegalArgumentException.class, () -> TrailCodec.decode(payload));
    }
}
