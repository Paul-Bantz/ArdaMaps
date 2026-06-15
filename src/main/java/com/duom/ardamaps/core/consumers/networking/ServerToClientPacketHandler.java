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
import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.function.Function;

/**
 * Abstract base class for handling server-to-client packets in the ArdaMaps mod.
 * This class provides common functionality for sending packets from the server to clients
 * and handling incoming packets on the client side.
 *
 * @param <T> The type of packet being handled, which must implement the IPacket interface.
 */
public abstract class ServerToClientPacketHandler<T extends IPacket> implements IClientPacketHandler {

    /** The unique identifier for the packet channel, constructed using the mod ID and a specific channel name. */
    @Getter
    private final Identifier channelId;

    /** A function that reads a packet of type T from a PacketByteBuf. This is used to deserialize incoming packets on the client side. */
    private final Function<PacketByteBuf, T> reader;

    /**
     * Constructs a new ServerToClientPacketHandler with the specified channel name and packet reader function.
     *
     * @param channel The name of the packet channel, which will be combined with the mod ID to create a unique Identifier.
     * @param reader  A function that takes a PacketByteBuf and returns an instance of T, used to read incoming packets on the client side.
     */
    @SuppressWarnings("SameParameterValue")
    protected ServerToClientPacketHandler(String channel, Function<PacketByteBuf, T> reader) {
        this.channelId = Identifier.of(ArdaMaps.MOD_ID, channel);
        this.reader = reader;
    }

    /**
     * Sends a packet of type T to a specific player on the client side. This method serializes the packet into a PacketByteBuf and sends it using the ServerPlayNetworking API.
     *
     * @param player The ServerPlayerEntity representing the player to send the packet to.
     * @param packet The packet of type T to be sent to the client, which will be serialized and transmitted over the network.
     */
    public void send(ServerPlayerEntity player, T packet) {
        PacketByteBuf buf = PacketByteBufs.create();
        PacketByteBuf packetBuf = packet.build();
        buf.writeBytes(packetBuf);
        ServerPlayNetworking.send(player, channelId, buf);
    }

    /**
     * Handles incoming packets on the client side. This method is called when a packet is received on the specified channel. It reads the packet data using the provided reader function and then executes the handling logic on the main client thread.
     *
     * @param client  The MinecraftClient instance representing the client receiving the packet.
     * @param handler The ClientPlayNetworkHandler that manages network communication on the client side.
     * @param buf     The PacketByteBuf containing the raw data of the incoming packet, which will be read and deserialized into an instance of T using the reader function.
     * @param sender  The PacketSender that can be used to send responses back to the server if needed.
     */
    @Override
    public void handle(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        T packet = reader.apply(buf);
        client.execute(() -> handle(client, packet));
    }

    /**
     * Abstract method that must be implemented by subclasses to define the logic for handling a packet of type T on the client side. This method is called on the main client thread after the packet has been read and deserialized.
     *
     * @param client The MinecraftClient instance representing the client receiving the packet, which can be used to access client-side resources and perform actions in response to the packet.
     * @param packet The packet of type T that was received and deserialized from the incoming PacketByteBuf, which contains the data that needs to be processed according to the specific handling logic defined in the subclass implementation.
     */
    protected abstract void handle(MinecraftClient client, T packet);
}