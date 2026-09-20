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

package com.duom.ardamaps.core.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for persisted exploration-data compression.
 */
class ExplorationCodecTest {

    /**
     * Verifies sparse exploration arrays round-trip through the compressed codec.
     *
     * @throws IOException if decoding fails.
     */
    @Test
    void encodeDecode_sparseArray_roundTrips() throws IOException {

        byte[] states = new byte[4096];
        states[17] = ExplorationState.VISIBLE.getValue();
        states[2048] = ExplorationState.REVEALED.getValue();

        assertArrayEquals(states, ExplorationCodec.decode(ExplorationCodec.encode(states)));
    }

    /**
     * Verifies legacy raw arrays are returned unchanged.
     *
     * @throws IOException if decoding fails.
     */
    @Test
    void decode_legacyRawArray_returnsUnchanged() throws IOException {

        byte[] states = {0, 1, 2, 0};

        assertArrayEquals(states, ExplorationCodec.decode(states));
    }

    /**
     * Verifies corrupt compressed payloads are rejected.
     */
    @Test
    void decode_truncatedMagicPayload_throws() {

        byte[] payload = {'A', 'M', 'X', '1', 0, 0, 0, 16, 120};

        assertThrows(IOException.class, () -> ExplorationCodec.decode(payload));
    }

    /**
     * Verifies oversized declared cell counts are rejected before allocation.
     */
    @Test
    void decode_oversizedDeclaredCellCount_throws() {

        byte[] payload = ByteBuffer.allocate(8)
                .put(new byte[]{'A', 'M', 'X', '1'})
                .putInt(ExplorationGrid.MAX_CELLS_PER_AXIS * ExplorationGrid.MAX_CELLS_PER_AXIS + 1)
                .array();

        assertThrows(IllegalArgumentException.class, () -> ExplorationCodec.decode(payload));
    }
}
