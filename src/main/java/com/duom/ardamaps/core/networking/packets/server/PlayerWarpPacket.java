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
import com.duom.ardamaps.gui.ModConstants;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

/**
 * Packet sent by the server to warp the player to a specific location
 *
 * @param warpName the name of the warp to teleport to
 */
public record PlayerWarpPacket(String warpName) implements IPacket {
    public static final CustomPacketPayload.Type<PlayerWarpPacket> TYPE = new CustomPacketPayload.Type<>(ModConstants.modId("player_warp"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerWarpPacket> CODEC = IPacket.codec(PlayerWarpPacket::read);

    /**
     * Reads a PlayerWarpRequest from the given PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A new PlayerWarpRequest instance.
     */
    public static PlayerWarpPacket read(FriendlyByteBuf buf) {
        return new PlayerWarpPacket(buf.readUtf());
    }

    /**
     * Builds the PacketByteBuf for this packet.
     *
     * @return The PacketByteBuf containing the packet data.
     */
    @Override
    public FriendlyByteBuf build() {
        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeUtf(warpName);
        return buf;
    }

    @Override
    public CustomPacketPayload.@NonNull Type<PlayerWarpPacket> type() {
        return TYPE;
    }
}
