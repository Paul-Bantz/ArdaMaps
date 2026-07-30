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

package com.duom.ardamaps.core.consumers.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Function;

/**
 * Interface for network packets.
 * <br/><b>Credits to AjCool</b> for the original code - <a href="https://github.com/ArdaCraft/ArdaPaths">...</a>
 */
public interface IPacket extends CustomPacketPayload {

    /**
     * Convert the packet to an instance of the object.
     *
     * @param buf The packet byte buffer to read
     */
    @SuppressWarnings("unused")
    static <T> T read(FriendlyByteBuf buf) {
        return null;
    }

    /**
     * Creates a simple payload codec backed by the packet's legacy read/build methods.
     *
     * @param reader The packet reader.
     * @param <T>    The packet type.
     * @return A StreamCodec for play-phase payload registration.
     */
    static <T extends IPacket> StreamCodec<RegistryFriendlyByteBuf, T> codec(Function<FriendlyByteBuf, T> reader) {

        return StreamCodec.of((buf, packet) -> buf.writeBytes(packet.build()), reader::apply);
    }

    /**
     * Builds this packet into a serialized byte buffer.
     *
     * @return the packet data encoded in a byte buffer
     */
    FriendlyByteBuf build();
}
