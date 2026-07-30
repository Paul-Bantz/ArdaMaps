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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Abstract base class for handling server-side packets.
 *
 * @param <T> The type of packet being handled.
 *            <br/><b>Credits to AjCool</b> for the original code - <a href="https://github.com/ArdaCraft/ArdaPaths">...</a>
 */
public abstract class ServerPacketHandler<T extends IPacket> extends PacketHandler<T> implements IServerPacketHandler<T> {

    /**
     * Constructs a new ServerPacketHandler with the specified channel name and packet reader function.
     *
     * @param channel The name of the packet channel, which will be combined with the mod ID to create a unique Identifier.
     * @param type    The packet type
     * @param codec   THe codec
     */
    public ServerPacketHandler(final String channel, final CustomPacketPayload.Type<T> type,
                               final StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        super(channel, type, codec);
    }

    /**
     * Handles an incoming packet on the server side.
     *
     * @param packet  The deserialized packet payload.
     * @param context The Fabric networking context for this server-side receive.
     */
    @Override
    public void receive(T packet, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.server(), context.player(), packet);
    }

    /**
     * Abstract method to process the deserialized packet of type T. Subclasses must implement this method to define the specific behaviour for handling the packet on the server side.
     *
     * @param server The MinecraftServer instance representing the server on which the packet was received.
     * @param player The ServerPlayerEntity representing the player who sent the packet.
     * @param packet The deserialized packet of type T that contains the data sent by the client.
     */
    protected abstract void handle(MinecraftServer server, ServerPlayer player, T packet);
}
