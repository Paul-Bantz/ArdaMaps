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

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Function;

/**
 * Abstract base class for handling server-side packets.
 *
 * @param <T> The type of packet being handled.
 *            <br/><b>Credits to AjCool</b> for the original code - <a href="https://github.com/ArdaCraft/ArdaPaths">...</a>
 */
public abstract class ServerPacketHandler<T extends IPacket> extends PacketHandler implements IServerPacketHandler<T> {
    /** A function that reads a packet of type T from a PacketByteBuf. This is used to deserialize incoming packets on the server side. */
    private final Function<PacketByteBuf, T> reader;

    /**
     * Constructs a new ServerPacketHandler with the specified channel name and packet reader function.
     *
     * @param channel The name of the packet channel, which will be combined with the mod ID to create a unique Identifier.
     * @param reader  A function that takes a PacketByteBuf and returns an instance of T, used to read incoming packets on the server side.
     */
    public ServerPacketHandler(final String channel, final Function<PacketByteBuf, T> reader) {
        super(channel);
        this.reader = reader;
    }

    /**
     * Handles an incoming packet on the server side. This method reads the packet data from the PacketByteBuf using the provided reader function and then calls the abstract handle method to process the packet.
     *
     * @param server  The MinecraftServer instance representing the server on which the packet was received.
     * @param player  The ServerPlayerEntity representing the player who sent the packet.
     * @param handler The ServerPlayNetworkHandler responsible for managing the network connection for the player.
     * @param buf     The PacketByteBuf containing the raw data of the incoming packet, which will be read and deserialized into an instance of T using the reader function.
     * @param sender  The PacketSender used to send responses back to the client if necessary.
     */
    public void handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        T packet = reader.apply(buf);
        handle(server, player, handler, packet, sender);
    }

    /**
     * Abstract method to process the deserialized packet of type T. Subclasses must implement this method to define the specific behaviour for handling the packet on the server side.
     *
     * @param server         The MinecraftServer instance representing the server on which the packet was received.
     * @param player         The ServerPlayerEntity representing the player who sent the packet.
     * @param ignoredHandler The ServerPlayNetworkHandler responsible for managing the network connection for the player.
     * @param packet         The deserialized packet of type T that was read from the PacketByteBuf, which contains the data sent by the client and needs to be processed by the server.
     * @param ignoredSender  The PacketSender used to send responses back to the client if necessary, allowing for communication between the server and client based on the received packet.
     */
    protected abstract void handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler ignoredHandler, T packet, PacketSender ignoredSender);
}