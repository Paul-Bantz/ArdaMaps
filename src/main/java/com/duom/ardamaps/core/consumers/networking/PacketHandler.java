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

import com.duom.ardamaps.ArdaMaps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Abstract base class for packet handlers, providing common functionality.
 * <br/><b>Credits to AjCool</b> for the original code - <a href="https://github.com/ArdaCraft/ArdaPaths">...</a>
 */
public abstract class PacketHandler<T extends IPacket> implements IPacketHandler {
    /** The unique identifier for the packet channel, constructed using the mod ID and a specific channel name. */
    private final Identifier channelId;
    /** The Fabric custom payload type for this channel. */
    private final CustomPacketPayload.Type<T> type;
    /** The payload codec for this channel. */
    private final StreamCodec<RegistryFriendlyByteBuf, T> codec;

    /**
     * Constructs a new PacketHandler with the specified channel name.
     *
     * @param channelId The name of the packet channel, which will be combined with the mod ID to create a unique Identifier.
     */
    public PacketHandler(final String channelId, final CustomPacketPayload.Type<T> type, final StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        this.channelId = Identifier.tryBuild(ArdaMaps.MOD_ID, channelId);
        this.type = type;
        this.codec = codec;
    }

    /**
     * @return The unique Identifier for the packet channel that this handler is responsible for. This Identifier is used to register the handler and send packets on the correct channel.
     */
    @Override
    public Identifier getChannelId() {
        return channelId;
    }

    @Override
    public CustomPacketPayload.Type<T> getType() {
        return type;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, T> getCodec() {
        return codec;
    }
}
