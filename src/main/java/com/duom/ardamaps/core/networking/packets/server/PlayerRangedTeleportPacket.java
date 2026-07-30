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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet sent by the server to teleport the player to a specific location in a given range, optionally in a specific world.
 *
 * @param x             The X coordinate to teleport to.
 * @param z             The Z coordinate to teleport to.
 * @param worldId       The Identifier of the world to teleport to. If null, the current world is used.
 * @param scanMinBoundY Y coordinate of the minimum Y in the range to scan for a valid position.
 * @param scanMaxBoundY Y coordinate of the maximum Y in the range to scan for a valid position.
 */
public record PlayerRangedTeleportPacket(
        double x,
        double z,
        String worldId,
        double scanMinBoundY,
        double scanMaxBoundY
) implements IPacket {

    /**
     * Deserializes a PlayerRangedTeleportPacket from the given PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A new PlayerRangedTeleportPacket instance with the deserialized data.
     */
    public static PlayerRangedTeleportPacket read(FriendlyByteBuf buf) {

        final double x = buf.readDouble();
        final double z = buf.readDouble();
        final String worldId = buf.readUtf();
        final double scanMinBoundY = buf.readDouble();
        final double scanMaxBoundY = buf.readDouble();

        return new PlayerRangedTeleportPacket(x, z, worldId, scanMinBoundY, scanMaxBoundY);
    }

    /**
     * Serializes this packet into a PacketByteBuf for transmission over the network.
     *
     * @return A new PacketByteBuf containing the serialized packet data.
     */
    @Override
    public FriendlyByteBuf build() {

        FriendlyByteBuf buf = PacketByteBufs.create();

        buf.writeDouble(x);
        buf.writeDouble(z);
        buf.writeUtf(worldId);
        buf.writeDouble(scanMinBoundY);
        buf.writeDouble(scanMaxBoundY);

        return buf;
    }
}
