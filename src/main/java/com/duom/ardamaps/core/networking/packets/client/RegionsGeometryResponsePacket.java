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

import com.duom.ardamaps.core.consumers.networking.IPacket;
import com.duom.ardamaps.core.consumers.networking.IRespondablePacket;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.data.map.region.RegionShape;
import com.duom.ardamaps.gui.ModConstants;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Packet containing gzipped binary vector region geometry.
 *
 * @param requestId          The request identifier.
 * @param geometries         The geometry payloads that are stale or absent on the client.
 * @param serverDimensionIds The full set of dimension identifiers with server geometry.
 */
public record RegionsGeometryResponsePacket(UUID requestId,
                                            List<RegionGeometry> geometries,
                                            List<String> serverDimensionIds) implements IRespondablePacket<RegionsGeometryResponsePacket> {

    /** Fabric custom payload type for this packet. */
    public static final CustomPacketPayload.Type<RegionsGeometryResponsePacket> TYPE = new CustomPacketPayload.Type<>(ModConstants.modId("regions_geometry_data_response"));

    /** Empty response sentinel. */
    public static final RegionsGeometryResponsePacket EMPTY = new RegionsGeometryResponsePacket(List.of(), List.of());

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionsGeometryResponsePacket.class);

    /** Maximum compressed geometry payload accepted from the wire. */
    private static final int MAX_COMPRESSED_DATA_LENGTH = 8 * 1024 * 1024;

    /** Maximum number of regions accepted in one geometry packet. */
    private static final int MAX_REGIONS = 4096;

    /** Maximum number of dimensions accepted in one geometry packet. */
    private static final int MAX_DIMENSIONS = 256;

    /** Maximum number of rings accepted in one region. */
    private static final int MAX_RINGS_PER_REGION = 1024;

    /** Maximum number of points accepted in one ring. */
    private static final int MAX_POINTS_PER_RING = 16_384;

    /** Stream codec used to serialize and deserialize region geometry responses. */
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionsGeometryResponsePacket> CODEC = IPacket.codec(RegionsGeometryResponsePacket::read);

    /**
     * Constructs a response packet with a default request identifier.
     *
     * @param geometries         The geometry payloads.
     * @param serverDimensionIds The full set of server geometry dimension identifiers.
     */
    public RegionsGeometryResponsePacket(List<RegionGeometry> geometries, List<String> serverDimensionIds) {
        this(new UUID(0L, 0L), geometries, serverDimensionIds);
    }

    /**
     * Reads a response packet from a byte buffer.
     *
     * @param buf The packet buffer.
     * @return The parsed response packet.
     */
    public static RegionsGeometryResponsePacket read(FriendlyByteBuf buf) {

        var requestId = buf.readUUID();
        var dataLength = buf.readInt();

        if (dataLength == 0) return new RegionsGeometryResponsePacket(requestId, List.of(), List.of());

        validateDataLength(dataLength, buf.readableBytes());
        byte[] compressedData = new byte[dataLength];
        buf.readBytes(compressedData);

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
            FriendlyByteBuf dataBuffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(gzip.readAllBytes()));
            int geometryCount = readCount(dataBuffer, "dimension", MAX_DIMENSIONS);
            RegionGeometry[] geometries = new RegionGeometry[geometryCount];
            for (int i = 0; i < geometryCount; i++) {
                geometries[i] = readGeometry(dataBuffer);
            }

            int serverDimensionCount = readCount(dataBuffer, "server dimension", MAX_DIMENSIONS);
            String[] serverDimensionIds = new String[serverDimensionCount];
            for (int i = 0; i < serverDimensionCount; i++) {
                serverDimensionIds[i] = dataBuffer.readUtf();
            }

            return new RegionsGeometryResponsePacket(requestId, List.of(geometries), List.of(serverDimensionIds));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read region geometry data from network packet", e);
        }
    }

    /**
     * Reads a geometry payload from an uncompressed buffer.
     *
     * @param buf The uncompressed payload buffer.
     * @return The decoded geometry.
     */
    private static RegionGeometry readGeometry(FriendlyByteBuf buf) {

        String dimensionId = buf.readUtf();
        Date lastUpdate = new Date(buf.readLong());
        int regionCount = readCount(buf, "region", MAX_REGIONS);
        RegionShape[] regions = new RegionShape[regionCount];

        for (int i = 0; i < regionCount; i++) {
            regions[i] = readShape(buf);
        }

        return new RegionGeometry(dimensionId, regions, lastUpdate);
    }

    /**
     * Reads one region shape from an uncompressed buffer.
     *
     * @param buf The uncompressed payload buffer.
     * @return The decoded shape.
     */
    private static RegionShape readShape(FriendlyByteBuf buf) {

        String id = buf.readUtf();
        String name = buf.readUtf();
        String parentId = buf.readBoolean() ? buf.readUtf() : null;
        int depth = buf.readVarInt();
        int minX = buf.readInt();
        int minZ = buf.readInt();
        int maxX = buf.readInt();
        int maxZ = buf.readInt();
        Vec2d labelAnchor = new Vec2d(buf.readDouble(), buf.readDouble());
        double labelRadius = buf.readDouble();

        int ringCount = readCount(buf, "region ring", MAX_RINGS_PER_REGION);
        int[][] rings = new int[ringCount][];
        for (int i = 0; i < ringCount; i++) {
            rings[i] = readRing(buf);
        }

        return new RegionShape(id, name, parentId, depth, rings, minX, minZ, maxX, maxZ, labelAnchor, labelRadius);
    }

    /**
     * Reads one zigzag-varint delta encoded ring.
     *
     * @param buf The uncompressed payload buffer.
     * @return The decoded interleaved x,z coordinates.
     */
    private static int[] readRing(FriendlyByteBuf buf) {

        int pointCount = readCount(buf, "region ring point", MAX_POINTS_PER_RING);
        int[] ring = new int[pointCount * 2];
        int previousX = 0;
        int previousZ = 0;

        for (int i = 0; i < pointCount; i++) {
            int x = previousX + decodeZigZag(buf.readVarInt());
            int z = previousZ + decodeZigZag(buf.readVarInt());
            ring[i * 2] = x;
            ring[i * 2 + 1] = z;
            previousX = x;
            previousZ = z;
        }

        return ring;
    }

    /**
     * Reads and validates a VarInt count.
     *
     * @param buf   The packet buffer.
     * @param label The field label.
     * @param max   The maximum accepted count.
     * @return The validated count.
     */
    private static int readCount(FriendlyByteBuf buf, String label, int max) {

        int count = buf.readVarInt();
        if (count < 0) throw new IllegalArgumentException("Region geometry " + label + " count cannot be negative: " + count);
        if (count > max) throw new IllegalArgumentException("Region geometry " + label + " count exceeds maximum of " + max + ": " + count);
        return count;
    }

    /**
     * Validates the compressed payload size before allocation.
     *
     * @param dataLength    The declared compressed payload length.
     * @param readableBytes The remaining readable bytes.
     */
    private static void validateDataLength(int dataLength, int readableBytes) {

        if (dataLength < 0) {
            throw new IllegalArgumentException("Region geometry response data length cannot be negative: " + dataLength);
        }

        if (dataLength > MAX_COMPRESSED_DATA_LENGTH) {
            throw new IllegalArgumentException("Region geometry response data length exceeds maximum of "
                    + MAX_COMPRESSED_DATA_LENGTH + " bytes: " + dataLength);
        }

        if (dataLength > readableBytes) {
            throw new IllegalArgumentException("Region geometry response data length " + dataLength
                    + " exceeds readable packet bytes " + readableBytes);
        }
    }

    /**
     * Serializes this packet into a packet buffer.
     *
     * @return The serialized packet buffer.
     */
    @Override
    public FriendlyByteBuf build() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUUID(requestId);
        List<RegionGeometry> geometryPayloads = geometries == null ? List.of() : geometries.stream()
                .filter(geometry -> geometry != null && geometry.lastUpdate() != null)
                .toList();
        List<String> dimensionIds = serverDimensionIds == null ? List.of() : serverDimensionIds;
        boolean hasData = !geometryPayloads.isEmpty() || !dimensionIds.isEmpty();

        if (!hasData) {
            buf.writeInt(0);
            return buf;
        }

        try {
            byte[] compressedData = compressGeometry(geometryPayloads, dimensionIds);
            buf.writeInt(compressedData.length);
            buf.writeBytes(compressedData);
        } catch (IOException e) {
            LOGGER.error("Error compressing region geometry data for network packet", e);
            buf.writeInt(0);
        }

        return buf;
    }

    /**
     * Compresses geometry payloads.
     *
     * @param geometries         The geometries to write.
     * @param serverDimensionIds The server dimension identifiers to write.
     * @return The gzipped binary payload.
     * @throws IOException if compression fails.
     */
    private static byte[] compressGeometry(List<RegionGeometry> geometries, List<String> serverDimensionIds) throws IOException {

        FriendlyByteBuf dataBuffer = FriendlyByteBufs.create();
        dataBuffer.writeVarInt(geometries.size());
        for (RegionGeometry geometry : geometries) {
            writeGeometry(geometry, dataBuffer);
        }

        dataBuffer.writeVarInt(serverDimensionIds.size());
        for (String dimensionId : serverDimensionIds) {
            dataBuffer.writeUtf(dimensionId);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
            byte[] bytes = new byte[dataBuffer.readableBytes()];
            dataBuffer.getBytes(dataBuffer.readerIndex(), bytes);
            gzip.write(bytes);
        }

        return outputStream.toByteArray();
    }

    /**
     * Writes a geometry payload to an uncompressed buffer.
     *
     * @param geometry The geometry to write.
     * @param buf      The uncompressed payload buffer.
     */
    private static void writeGeometry(RegionGeometry geometry, FriendlyByteBuf buf) {

        buf.writeUtf(geometry.dimensionId());
        buf.writeLong(geometry.lastUpdate().getTime());
        buf.writeVarInt(geometry.regions().length);

        for (RegionShape shape : geometry.regions()) {
            writeShape(shape, buf);
        }
    }

    /**
     * Writes one region shape to an uncompressed buffer.
     *
     * @param shape The shape to write.
     * @param buf   The uncompressed payload buffer.
     */
    private static void writeShape(RegionShape shape, FriendlyByteBuf buf) {

        buf.writeUtf(shape.id());
        buf.writeUtf(shape.name());
        buf.writeBoolean(shape.parentId() != null);
        if (shape.parentId() != null) buf.writeUtf(shape.parentId());
        buf.writeVarInt(shape.depth());
        buf.writeInt(shape.minX());
        buf.writeInt(shape.minZ());
        buf.writeInt(shape.maxX());
        buf.writeInt(shape.maxZ());
        buf.writeDouble(shape.labelAnchor().x());
        buf.writeDouble(shape.labelAnchor().y());
        buf.writeDouble(shape.labelRadius());
        buf.writeVarInt(shape.rings().length);

        for (int[] ring : shape.rings()) {
            writeRing(ring, buf);
        }
    }

    /**
     * Writes one zigzag-varint delta encoded ring.
     *
     * @param ring The interleaved x,z ring.
     * @param buf  The uncompressed payload buffer.
     */
    private static void writeRing(int[] ring, FriendlyByteBuf buf) {

        int pointCount = ring.length / 2;
        buf.writeVarInt(pointCount);

        int previousX = 0;
        int previousZ = 0;
        for (int i = 0; i < pointCount; i++) {
            int x = ring[i * 2];
            int z = ring[i * 2 + 1];
            buf.writeVarInt(encodeZigZag(x - previousX));
            buf.writeVarInt(encodeZigZag(z - previousZ));
            previousX = x;
            previousZ = z;
        }
    }

    /**
     * Encodes a signed integer as zigzag.
     *
     * @param value The signed value.
     * @return The zigzag-encoded value.
     */
    private static int encodeZigZag(int value) {

        return (value << 1) ^ (value >> 31);
    }

    /**
     * Decodes a zigzag-encoded integer.
     *
     * @param value The zigzag-encoded value.
     * @return The signed value.
     */
    private static int decodeZigZag(int value) {

        return (value >>> 1) ^ -(value & 1);
    }

    /**
     * Creates a new response packet with the supplied request identifier.
     *
     * @param requestId The request identifier.
     * @return The packet with the request identifier applied.
     */
    @Override
    public RegionsGeometryResponsePacket withRequestId(UUID requestId) {

        return new RegionsGeometryResponsePacket(requestId, geometries, serverDimensionIds);
    }

    @Override
    public CustomPacketPayload.@NonNull Type<RegionsGeometryResponsePacket> type() {
        return TYPE;
    }
}
