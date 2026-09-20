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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encodes and decodes persisted movement trails.
 */
public final class TrailCodec {

    /** Magic header for compressed trail payloads. */
    private static final byte[] MAGIC = {'A', 'M', 'T', '1'};

    /** Number of bytes used by the magic plus declared uncompressed length. */
    private static final int HEADER_SIZE = MAGIC.length + Integer.BYTES;

    /** Deflate compression level used for persisted trail payloads. */
    private static final int COMPRESSION_LEVEL = 6;

    /** Maximum accepted uncompressed payload size. */
    private static final int MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024;

    /** Bit flag stored in the mode byte when a segment starts after a gap. */
    private static final int STARTS_AT_GAP_FLAG = 0x40;

    /** Bit flag stored in the mode byte when a segment ends before a gap. */
    private static final int ENDS_AT_GAP_FLAG = 0x80;

    /** Mask for the low mode-id bits in a flagged mode byte. */
    private static final int MODE_ID_MASK = 0x3F;

    /**
     * Utility class.
     */
    private TrailCodec() {

    }

    /**
     * Encodes all movement trails into a compressed binary payload.
     *
     * @param trails Trails keyed by dimension ID.
     * @return Encoded payload.
     */
    public static byte[] encode(Map<String, PlayerTrail> trails) {

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, trails.size());

        for (var entry : trails.entrySet()) {
            writeUtf(payload, entry.getKey());
            PlayerTrail trail = entry.getValue();
            writeVarInt(payload, trail.segments().size());
            for (TrailSegment segment : trail.segments()) {
                payload.write(segment.mode().getId()
                        | (segment.startsAtGap() ? STARTS_AT_GAP_FLAG : 0)
                        | (segment.endsAtGap() ? ENDS_AT_GAP_FLAG : 0));
                writeVarInt(payload, segment.size());
                if (segment.size() == 0) continue;

                int previousX = segment.x(0);
                int previousZ = segment.z(0);
                writeZigZagVarInt(payload, previousX);
                writeZigZagVarInt(payload, previousZ);

                for (int i = 1; i < segment.size(); i++) {
                    int x = segment.x(i);
                    int z = segment.z(i);
                    writeZigZagVarInt(payload, x - previousX);
                    writeZigZagVarInt(payload, z - previousZ);
                    previousX = x;
                    previousZ = z;
                }
            }
        }

        byte[] uncompressed = payload.toByteArray();
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream output = new ByteArrayOutputStream(HEADER_SIZE + Math.max(32, uncompressed.length / 4));
        output.writeBytes(MAGIC);
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(uncompressed.length).array());

        try (Deflater deflater = new Deflater(COMPRESSION_LEVEL)) {
            deflater.setInput(uncompressed);
            deflater.finish();
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
        }

        return output.toByteArray();
    }

    /**
     * Decodes persisted movement trails.
     *
     * @param stored Encoded payload.
     * @return Trails keyed by dimension ID.
     * @throws IOException              If the compressed payload is corrupt or incomplete.
     * @throws IllegalArgumentException If the declared length or structural counts are not sane.
     */
    public static Map<String, PlayerTrail> decode(byte[] stored) throws IOException {

        if (!hasMagic(stored)) throw new IOException("Invalid trail payload header");
        if (stored.length < HEADER_SIZE) throw new IOException("Truncated trail payload header");

        int uncompressedLength = ByteBuffer.wrap(stored, MAGIC.length, Integer.BYTES).getInt();
        if (uncompressedLength < 0 || uncompressedLength > MAX_UNCOMPRESSED_BYTES) {
            throw new IllegalArgumentException("Invalid trail payload length: " + uncompressedLength);
        }

        byte[] payload = inflate(stored, uncompressedLength);
        return readTrails(payload);
    }

    /**
     * Inflates the compressed payload body.
     *
     * @param stored             Stored encoded bytes.
     * @param uncompressedLength Declared uncompressed length.
     * @return Inflated payload bytes.
     * @throws IOException If inflation fails.
     */
    private static byte[] inflate(byte[] stored, int uncompressedLength) throws IOException {

        try (Inflater inflater = new Inflater()) {
            inflater.setInput(stored, HEADER_SIZE, stored.length - HEADER_SIZE);
            byte[] output = new byte[uncompressedLength];
            int offset = 0;
            while (offset < uncompressedLength) {
                int count = inflater.inflate(output, offset, uncompressedLength - offset);
                if (count == 0) {
                    if (inflater.finished()) break;
                    if (inflater.needsInput()) throw new IOException("Truncated trail payload");
                    if (inflater.needsDictionary()) throw new IOException("Trail payload requires a dictionary");
                }
                offset += count;
            }

            if (offset != uncompressedLength || !inflater.finished()) {
                throw new IOException("Trail payload length mismatch");
            }

            return output;
        } catch (DataFormatException e) {
            throw new IOException("Invalid trail payload", e);
        }
    }

    /**
     * Reads trails from the uncompressed binary payload.
     *
     * @param payload Uncompressed payload.
     * @return Trails keyed by dimension ID.
     * @throws IOException If the payload is truncated.
     */
    private static Map<String, PlayerTrail> readTrails(byte[] payload) throws IOException {

        ByteArrayInputStream input = new ByteArrayInputStream(payload);
        int dimensionCount = readVarInt(input);
        if (dimensionCount < 0) throw new IOException("Invalid trail dimension count");

        Map<String, PlayerTrail> trails = new LinkedHashMap<>();
        for (int dimensionIndex = 0; dimensionIndex < dimensionCount; dimensionIndex++) {
            String dimensionId = readUtf(input);
            int segmentCount = readVarInt(input);
            if (segmentCount < 0) throw new IOException("Invalid trail segment count");

            PlayerTrail trail = new PlayerTrail();
            for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
                int modeByte = input.read();
                if (modeByte < 0) throw new IOException("Truncated trail segment mode");

                int pointCount = readVarInt(input);
                if (pointCount < 0 || pointCount > PlayerTrail.MAX_POINTS_PER_DIMENSION) {
                    throw new IllegalArgumentException("Invalid trail point count: " + pointCount);
                }
                if (pointCount == 0) continue;

                TrailSegment segment = new TrailSegment(MovementMode.fromId((byte) (modeByte & MODE_ID_MASK)));
                segment.setStartsAtGap((modeByte & STARTS_AT_GAP_FLAG) != 0);
                segment.setEndsAtGap((modeByte & ENDS_AT_GAP_FLAG) != 0);

                int x = readZigZagVarInt(input);
                int z = readZigZagVarInt(input);
                segment.append(x, z);
                for (int pointIndex = 1; pointIndex < pointCount; pointIndex++) {
                    x += readZigZagVarInt(input);
                    z += readZigZagVarInt(input);
                    segment.append(x, z);
                }
                trail.restoreSegment(segment);
            }
            trails.put(dimensionId, trail);
        }

        if (input.available() != 0) throw new IOException("Trailing bytes in trail payload");
        return trails;
    }

    /**
     * Writes a UTF-8 string prefixed by its byte length.
     *
     * @param output Destination stream.
     * @param value  String value.
     */
    private static void writeUtf(ByteArrayOutputStream output, String value) {

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        output.writeBytes(bytes);
    }

    /**
     * Reads a UTF-8 string prefixed by its byte length.
     *
     * @param input Source stream.
     * @return Decoded string.
     * @throws IOException If the payload is truncated.
     */
    private static String readUtf(ByteArrayInputStream input) throws IOException {

        int length = readVarInt(input);
        if (length < 0 || length > input.available()) throw new IOException("Invalid trail string length");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IOException("Truncated trail string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Writes a signed integer using zigzag varint encoding.
     *
     * @param output Destination stream.
     * @param value  Signed value.
     */
    private static void writeZigZagVarInt(ByteArrayOutputStream output, int value) {

        writeVarInt(output, (value << 1) ^ (value >> 31));
    }

    /**
     * Reads a signed integer using zigzag varint encoding.
     *
     * @param input Source stream.
     * @return Signed value.
     * @throws IOException If the payload is truncated.
     */
    private static int readZigZagVarInt(ByteArrayInputStream input) throws IOException {

        int raw = readVarInt(input);
        return raw >>> 1 ^ -(raw & 1);
    }

    /**
     * Writes an integer using unsigned varint encoding.
     *
     * @param output Destination stream.
     * @param value  Value to write.
     */
    private static void writeVarInt(ByteArrayOutputStream output, int value) {

        while ((value & 0xFFFFFF80) != 0L) {
            output.write(value & 0x7F | 0x80);
            value >>>= 7;
        }
        output.write(value & 0x7F);
    }

    /**
     * Reads an integer using unsigned varint encoding.
     *
     * @param input Source stream.
     * @return Decoded value.
     * @throws IOException If the payload is truncated or malformed.
     */
    private static int readVarInt(ByteArrayInputStream input) throws IOException {

        int value = 0;
        int shift = 0;
        while (shift < Integer.SIZE) {
            int current = input.read();
            if (current < 0) throw new IOException("Truncated trail varint");
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IOException("Trail varint is too long");
    }

    /**
     * Returns whether the stored payload begins with the trail magic.
     *
     * @param stored Stored payload.
     * @return Whether the payload has the trail magic.
     */
    private static boolean hasMagic(byte[] stored) {

        return stored.length >= MAGIC.length
                && Arrays.equals(MAGIC, Arrays.copyOf(stored, MAGIC.length));
    }
}
