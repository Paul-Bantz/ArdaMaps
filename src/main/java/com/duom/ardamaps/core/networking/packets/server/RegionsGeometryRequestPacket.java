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

import com.duom.ardamaps.core.consumers.networking.IPacket;
import com.duom.ardamaps.core.consumers.networking.IRespondablePacket;
import com.duom.ardamaps.gui.ModConstants;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Packet sent from the client to the server requesting vector region geometry.
 *
 * @param requestId     The request identifier.
 * @param knownGeometry The client-side known geometry update dates by dimension.
 */
public record RegionsGeometryRequestPacket(UUID requestId,
                                           Map<String, Date> knownGeometry) implements IRespondablePacket<RegionsGeometryRequestPacket> {

    /** Fabric custom payload type for this packet. */
    public static final CustomPacketPayload.Type<RegionsGeometryRequestPacket> TYPE = new CustomPacketPayload.Type<>(ModConstants.modId("regions_geometry_data_request"));

    /** Stream codec used to serialize and deserialize region geometry requests. */
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionsGeometryRequestPacket> CODEC = IPacket.codec(RegionsGeometryRequestPacket::read);

    /** Maximum number of dimensions accepted from a client request. */
    public static final int MAX_DIMENSIONS = 256;

    /**
     * Constructs a request packet with a default request identifier.
     *
     * @param knownGeometry The client-side known geometry update dates by dimension.
     */
    public RegionsGeometryRequestPacket(Map<String, Date> knownGeometry) {
        this(new UUID(0L, 0L), knownGeometry);
    }

    /**
     * Reads a request packet from a byte buffer.
     *
     * @param buf The packet buffer.
     * @return The parsed request packet.
     */
    public static RegionsGeometryRequestPacket read(FriendlyByteBuf buf) {

        var requestId = buf.readUUID();
        int count = readCount(buf);
        Map<String, Date> knownGeometry = new HashMap<>();

        for (int i = 0; i < count; i++) {
            knownGeometry.put(buf.readUtf(), new Date(buf.readLong()));
        }

        return new RegionsGeometryRequestPacket(requestId, knownGeometry);
    }

    /**
     * Reads and validates the dimension count.
     *
     * @param buf The packet buffer.
     * @return The validated dimension count.
     */
    private static int readCount(FriendlyByteBuf buf) {

        int count = buf.readVarInt();
        if (count < 0) throw new IllegalArgumentException("Region geometry request dimension count cannot be negative: " + count);
        if (count > MAX_DIMENSIONS) throw new IllegalArgumentException("Region geometry request dimension count exceeds maximum of " + MAX_DIMENSIONS + ": " + count);
        return count;
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
        Map<String, Date> geometry = knownGeometry == null ? Map.of() : knownGeometry;
        buf.writeVarInt(geometry.size());

        for (var entry : geometry.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeLong(entry.getValue().getTime());
        }

        return buf;
    }

    /**
     * Creates a new packet with the supplied request identifier.
     *
     * @param requestId The request identifier.
     * @return The packet with the request identifier applied.
     */
    @Override
    public RegionsGeometryRequestPacket withRequestId(UUID requestId) {

        return new RegionsGeometryRequestPacket(requestId, knownGeometry);
    }

    @Override
    public CustomPacketPayload.@NonNull Type<RegionsGeometryRequestPacket> type() {
        return TYPE;
    }
}
