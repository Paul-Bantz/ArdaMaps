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

import java.util.Date;

/**
 * A packet sent from the client to the server requesting location data, optionally filtered by a date.
 *
 * @param date The date to filter locations by. If null, all locations will be requested.
 */
public record LocationsRequestPacket(Date date) implements IPacket {

    /**
     * Reads a LocationsRequestPacket from the given PacketByteBuf.
     *
     * @param buf The PacketByteBuf to read from.
     * @return A new LocationsRequestPacket instance.
     */
    public static LocationsRequestPacket read(PacketByteBuf buf) {

        var hasData = buf.readBoolean();
        Date updateDate = null;
        if (hasData) updateDate = buf.readDate();

        return new LocationsRequestPacket(updateDate);
    }

    /**
     * Builds a PacketByteBuf from this LocationsRequestPacket.
     *
     * @return A PacketByteBuf representing this LocationsRequestPacket.
     */
    @Override
    public PacketByteBuf build() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(date != null);
        if (date != null) buf.writeDate(date);
        return buf;
    }
}