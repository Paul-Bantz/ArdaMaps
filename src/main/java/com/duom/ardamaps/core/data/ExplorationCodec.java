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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encodes and decodes persisted exploration-state byte arrays.
 */
public final class ExplorationCodec {

    /** Magic header for compressed exploration arrays. */
    private static final byte[] MAGIC = {'A', 'M', 'X', '1'};

    /** Number of bytes used by the magic plus the declared cell count. */
    private static final int HEADER_SIZE = MAGIC.length + Integer.BYTES;

    /** Deflate compression level used for persisted exploration arrays. */
    private static final int COMPRESSION_LEVEL = 6;

    /**
     * Utility class.
     */
    private ExplorationCodec() {

    }

    /**
     * Compresses an exploration-state byte array for persistence.
     *
     * @param states Exploration state bytes.
     * @return Encoded bytes with an ArdaMaps header.
     */
    public static byte[] encode(byte[] states) {

        byte[] buffer = new byte[8192];
        ByteArrayOutputStream output = new ByteArrayOutputStream(HEADER_SIZE + Math.max(32, states.length / 8));
        output.writeBytes(MAGIC);
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(states.length).array());

        try (Deflater deflater = new Deflater(COMPRESSION_LEVEL)) {

            deflater.setInput(states);
            deflater.finish();

            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
        }

        return output.toByteArray();
    }

    /**
     * Decodes persisted exploration-state bytes.
     *
     * @param stored Stored bytes, either compressed with {@link #encode(byte[])} or legacy raw data.
     * @return Decoded exploration state bytes.
     * @throws IOException              If the compressed payload is corrupt or incomplete.
     * @throws IllegalArgumentException If the declared cell count is not sane.
     */
    public static byte[] decode(byte[] stored) throws IOException {

        if (!hasMagic(stored)) return stored;
        if (stored.length < HEADER_SIZE) throw new IOException("Truncated exploration payload header");

        int cellCount = ByteBuffer.wrap(stored, MAGIC.length, Integer.BYTES).getInt();
        if (cellCount < 0 || cellCount > ExplorationGrid.MAX_CELLS_PER_AXIS * ExplorationGrid.MAX_CELLS_PER_AXIS) {
            throw new IllegalArgumentException("Invalid exploration cell count: " + cellCount);
        }

        try (Inflater inflater = new Inflater()) {

            inflater.setInput(stored, HEADER_SIZE, stored.length - HEADER_SIZE);
            byte[] output = new byte[cellCount];
            int offset = 0;
            while (offset < cellCount) {
                int count = inflater.inflate(output, offset, cellCount - offset);
                if (count == 0) {
                    if (inflater.finished()) break;
                    if (inflater.needsInput()) throw new IOException("Truncated exploration payload");
                    if (inflater.needsDictionary()) throw new IOException("Exploration payload requires a dictionary");
                }
                offset += count;
            }

            if (offset != cellCount || !inflater.finished()) {
                throw new IOException("Exploration payload length mismatch");
            }

            return output;

        } catch (DataFormatException e) {
            throw new IOException("Invalid exploration payload", e);
        }
    }

    /**
     * Returns whether the stored payload begins with the compressed-data magic.
     *
     * @param stored Stored payload.
     * @return Whether the payload has the compressed-data magic.
     */
    private static boolean hasMagic(byte[] stored) {

        return stored.length >= MAGIC.length
                && Arrays.equals(MAGIC, Arrays.copyOf(stored, MAGIC.length));
    }
}
