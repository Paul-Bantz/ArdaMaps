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
import net.minecraft.network.PacketByteBuf;

/**
 * Packet sent by the server to teleport the player to a specific location, optionally in a specific world.
 *
 * @param x       The X coordinate to teleport to.
 * @param y       The Y coordinate to teleport to.
 * @param z       The Z coordinate to teleport to.
 * @param worldId The Identifier of the world to teleport to. If null, the current world is used.
 */
public record PlayerTeleportPacket(double x, double y, double z, String worldId) implements IPacket {

    /**
     * Constructs a PlayerTeleportPacket with only X and Z coordinates, setting Y to NaN.
     *
     * @param x       The X coordinate to teleport to.
     * @param z       The Z coordinate to teleport to.
     * @param worldId The Identifier of the world to teleport to.
     */
    public PlayerTeleportPacket(double x, double z, String worldId) {
        this(x, Double.NaN, z, worldId);
    }

    /**
     * Builds the PacketByteBuf for this packet.
     *
     * @return The PacketByteBuf containing the packet data.
     */
    @Override
    public PacketByteBuf build() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeString(worldId);
        return buf;
    }

    /**
     * Reads a PlayerTeleportPacket from the given PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A new PlayerTeleportPacket instance.
     */
    public static PlayerTeleportPacket read(PacketByteBuf buf) {
        final double x = buf.readDouble();
        final double y = buf.readDouble();
        final double z = buf.readDouble();
        final String worldId = buf.readString();
        return new PlayerTeleportPacket(x, y, z, worldId);
    }
}