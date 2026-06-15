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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A packet handler that supports request-response communication.
 *
 * @param <T> The type of the request packet
 * @param <U> The type of the response packet
 *            <br/><b>Credits to AjCool</b> for the original code - <a href="https://github.com/ArdaCraft/ArdaPaths">...</a>
 */
public abstract class RespondablePacketHandler<T extends IPacket, U extends IPacket> extends PacketHandler implements IServerPacketHandler<T>, IClientPacketHandler {

    /** Map to store response consumers keyed by their unique request IDs */
    private final Map<UUID, Consumer<U>> responseConsumers = new HashMap<>();

    /** A function that reads a packet of type T from a PacketByteBuf. This is used to deserialize incoming packets on the server side. */
    private final Function<PacketByteBuf, T> reader;

    /** The unique identifier for the response packet channel, constructed using the mod ID and a specific channel name. */
    @Getter
    private final Identifier responseChannelId;

    /** A function that reads a packet of type U from a PacketByteBuf. This is used to deserialize incoming packets on the client side. */
    private final Function<PacketByteBuf, U> responseReader;

    /**
     * Constructs a new RespondablePacketHandler with the specified channel names and packet reader functions for both request and response packets.
     *
     * @param channel         The name of the request packet channel, which will be combined with the mod ID to create a unique Identifier for handling incoming requests on the server side.
     * @param reader          A function that takes a PacketByteBuf and returns an instance of T, used to read incoming request packets on the server side.
     * @param responseChannel The name of the response packet channel, which will be combined with the mod ID to create a unique Identifier for sending responses back to clients and handling incoming responses on the client side.
     * @param responseReader  A function that takes a PacketByteBuf and returns an instance of U, used to read incoming response packets on the client side.
     */
    public RespondablePacketHandler(
            final String channel,
            final Function<PacketByteBuf, T> reader,
            final String responseChannel,
            final Function<PacketByteBuf, U> responseReader
    ) {
        super(channel);
        this.reader = reader;
        responseChannelId = new Identifier(ArdaMaps.MOD_ID, responseChannel);
        this.responseReader = responseReader;
    }

    /**
     * Sends a request packet of type T to the client and registers a consumer to handle the response of type U when it is received. This method generates a unique request ID, serializes the packet, and sends it to the client. The consumer is stored in a map keyed by the request ID, allowing for asynchronous handling of responses when they arrive.
     *
     * @param packet   The request packet of type T to be sent to the client, which will be serialized and transmitted over the network.
     * @param consumer A Consumer that will be called with the response packet of type U when it is received from the client. This allows for asynchronous processing of the response based on the original request.
     */
    public void send(final T packet, final Consumer<U> consumer) {
        UUID id = UUID.randomUUID();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(id);
        PacketByteBuf packetBuf = packet.build();
        buf.writeBytes(packetBuf);
        if (consumer != null) {
            responseConsumers.put(id, consumer);
        }
        ClientPlayNetworking.send(getChannelId(), buf);
    }

    /**
     * Handles an incoming request packet on the server side. This method reads the request data from the PacketByteBuf using the provided reader function, processes the request by calling the abstract handle method, and then sends a response back to the client using the response channel. The response is serialized into a PacketByteBuf and sent with the same request ID to allow for matching responses to their corresponding requests on the client side.
     *
     * @param server  The MinecraftServer instance representing the server on which the packet was received.
     * @param player  The ServerPlayerEntity representing the player who sent the packet.
     * @param handler The ServerPlayNetworkHandler responsible for managing the network connection for the player.
     * @param buf     The PacketByteBuf containing the raw data of the incoming request packet, which will be read and deserialized into an instance of T using the reader function.
     * @param sender  The PacketSender used to send responses back to the client if necessary, allowing for communication between the server and client based on the received request packet.
     */
    @Override
    public void handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        UUID requestId = buf.readUuid();
        T packet = reader.apply(buf);
        U responsePacket = handle(server, player, handler, packet, sender);
        PacketByteBuf responseBuf = PacketByteBufs.create().writeUuid(requestId);
        PacketByteBuf responsePacketBuf = responsePacket.build();
        responseBuf.writeBytes(responsePacketBuf);
        sender.sendPacket(responseChannelId, responseBuf);
    }

    /**
     * Abstract method to process the deserialized request packet of type T and generate a response packet of type U. Subclasses must implement this method to define the specific behaviour for handling the request on the server side and creating an appropriate response based on the request data.
     *
     * @param ignoredServer  The MinecraftServer instance representing the server on which the packet was received.
     * @param ignoredPlayer  The ServerPlayerEntity representing the player who sent the packet.
     * @param ignoredHandler The ServerPlayNetworkHandler responsible for managing the network connection for the player.
     * @param packet         The deserialized request packet of type T that was read from the PacketByteBuf, which contains the data sent by the client and needs to be processed by the server to generate a response.
     * @param ignoredSender  The PacketSender used to send responses back to the client if necessary, allowing for communication between the server and client based on the received request packet. This can be used within this method to send additional packets if needed while processing the request.
     * @return A response packet of type U that will be sent back to the client in response to the original request. This packet should contain any necessary data or information that is relevant to the client's request and will be serialized and transmitted back over the network.
     */
    public abstract U handle(MinecraftServer ignoredServer, ServerPlayerEntity ignoredPlayer, ServerPlayNetworkHandler ignoredHandler, T packet, PacketSender ignoredSender);

    /**
     * Handles an incoming response packet on the client side. This method reads the response data from the PacketByteBuf using the provided responseReader function, retrieves the corresponding consumer for the original request using the request ID, and then calls the consumer with the deserialized response packet of type U. This allows for asynchronous processing of responses on the client side based on the original requests that were sent.
     *
     * @param client  The MinecraftClient instance representing the client receiving the packet, which can be used to access client-side resources and perform actions in response to the packet.
     * @param handler The ClientPlayNetworkHandler that manages network communication on the client side, which can be used to send additional packets or manage network state if needed while processing the response.
     * @param buf     The PacketByteBuf containing the raw data of the incoming response packet, which will be read and deserialized into an instance of U using the responseReader function. This buffer should contain a UUID at the beginning that matches the request ID of the original request, followed by the serialized response packet data.
     * @param sender  The PacketSender that can be used to send responses back to the server if needed, allowing for communication between the client and server based on the received response packet. This can be used within this method to send additional packets if needed while processing the response.
     */
    @Override
    public void handle(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        UUID requestId = buf.readUuid();
        U packet = responseReader.apply(buf);
        Consumer<U> consumer = responseConsumers.remove(requestId);
        if (consumer != null) {
            consumer.accept(packet);
        }
    }
}