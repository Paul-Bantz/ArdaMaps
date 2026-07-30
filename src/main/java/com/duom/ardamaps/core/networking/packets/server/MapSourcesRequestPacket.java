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

import java.util.UUID;

/**
 * A packet sent from the client to the server requesting map source configuration and feature availability.
 *
 * @param requestId The unique request identifier for tracking the response.
 */
public record MapSourcesRequestPacket(UUID requestId) implements IRespondablePacket<MapSourcesRequestPacket> {

    public static final CustomPacketPayload.Type<MapSourcesRequestPacket> TYPE = new CustomPacketPayload.Type<>(ModConstants.modId("map_source_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapSourcesRequestPacket> CODEC = IPacket.codec(MapSourcesRequestPacket::read);

    /**
     * Constructs a MapSourcesRequestPacket with a new request identifier.
     */
    public MapSourcesRequestPacket() {
        this(new UUID(0L, 0L));
    }

    /**
     * Reads a MapSourcesRequestPacket from the given PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A new MapSourcesRequestPacket instance.
     */
    public static MapSourcesRequestPacket read(FriendlyByteBuf buf) {
        return new MapSourcesRequestPacket(buf.readUUID());
    }

    /**
     * Serializes this packet into a PacketByteBuf for transmission over the network.
     *
     * @return A new PacketByteBuf containing the serialized packet data.
     */
    @Override
    public FriendlyByteBuf build() {
        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUUID(requestId);
        return buf;
    }

    /**
     * Creates a new MapSourcesRequestPacket with the specified request identifier.
     *
     * @param requestId The request identifier to associate with this request.
     * @return A new MapSourcesRequestPacket with the updated request identifier.
     */
    @Override
    public MapSourcesRequestPacket withRequestId(UUID requestId) {
        return new MapSourcesRequestPacket(requestId);
    }

    @Override
    public CustomPacketPayload.@NonNull Type<MapSourcesRequestPacket> type() {
        return TYPE;
    }
}
