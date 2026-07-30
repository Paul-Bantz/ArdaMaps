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
import com.duom.ardamaps.gui.ModConstants;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Response packet sent after a ranged teleport request has completed on the server thread.
 *
 * @param success True when the player was teleported, false when no safe destination was found or the request failed.
 * @param x The resolved destination X coordinate, or zero for failed responses.
 * @param y The resolved destination Y coordinate, including fractional standing heights, or zero for failed responses.
 * @param z The resolved destination Z coordinate, or zero for failed responses.
 */
public record PlayerTeleportResponsePacket(UUID requestId, boolean success, double x, double y, double z) implements IRespondablePacket<PlayerTeleportResponsePacket> {
    public static final CustomPacketPayload.Type<PlayerTeleportResponsePacket> TYPE = new CustomPacketPayload.Type<>(ModConstants.modId("player_ranged_teleport_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerTeleportResponsePacket> CODEC = IPacket.codec(PlayerTeleportResponsePacket::read);

    public PlayerTeleportResponsePacket(boolean success, double x, double y, double z) {
        this(new UUID(0L, 0L), success, x, y, z);
    }

    /**
     * Creates a failed teleport response with zeroed coordinates.
     *
     * @return A response packet representing a failed ranged teleport request.
     */
    public static PlayerTeleportResponsePacket failed() {

        return new PlayerTeleportResponsePacket(false, 0.0D, 0.0D, 0.0D);
    }

    /**
     * Reads a PlayerTeleportResponsePacket from a PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from
     * @return The PlayerTeleportResponsePacket read from the buffer
     */
    public static PlayerTeleportResponsePacket read(FriendlyByteBuf buf) {

        UUID requestId = buf.readUUID();
        boolean packetSuccess = buf.readBoolean();
        double packetX = buf.readDouble();
        double packetY = buf.readDouble();
        double packetZ = buf.readDouble();

        return new PlayerTeleportResponsePacket(requestId, packetSuccess, packetX, packetY, packetZ);
    }

    /**
     * Serializes this response packet to a PacketByteBuf.
     *
     * @return A packet buffer containing the success flag and resolved destination coordinates.
     */
    @Override
    public FriendlyByteBuf build() {

        FriendlyByteBuf buf = FriendlyByteBufs.create();

        buf.writeUUID(requestId);
        buf.writeBoolean(success);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);

        return buf;
    }

    @Override
    public PlayerTeleportResponsePacket withRequestId(UUID requestId) {
        return new PlayerTeleportResponsePacket(requestId, success, x, y, z);
    }

    @Override
    public CustomPacketPayload.Type<PlayerTeleportResponsePacket> type() {
        return TYPE;
    }
}
