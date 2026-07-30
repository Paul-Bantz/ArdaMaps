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

import com.duom.ardamaps.gui.ModConstants;
import lombok.Getter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A packet handler that supports request-response communication.
 *
 * @param <T> The type of the request packet
 * @param <U> The type of the response packet
 *            <br/><b>Credits to AjCool</b> for the original code - <a href="https://github.com/ArdaCraft/ArdaPaths">...</a>
 */
public abstract class RespondablePacketHandler<T extends IRespondablePacket<T>, U extends IRespondablePacket<U>>
        extends PacketHandler<T> implements IServerPacketHandler<T>, IClientPacketHandler<U> {

    /** Class logger for response delivery failures. */
    private static final Logger LOGGER = LoggerFactory.getLogger(RespondablePacketHandler.class);

    /** Thread-safe map storing response consumers keyed by their unique request IDs. */
    private final Map<UUID, Consumer<U>> responseConsumers = new ConcurrentHashMap<>();

    /** The unique identifier for the response packet channel, constructed using the mod ID and a specific channel name. */
    @Getter
    private final Identifier responseChannelId;

    /** The Fabric payload type for responses on this handler. */
    @Getter
    private final CustomPacketPayload.Type<U> responseType;

    /** The codec registered for responses on this handler. */
    @Getter
    private final StreamCodec<RegistryFriendlyByteBuf, U> responseCodec;

    /**
     * Constructs a new RespondablePacketHandler with the specified channel names and packet reader functions for both request and response packets.
     *
     * @param channel         The name of the request packet channel, which will be combined with the mod ID to create a unique Identifier for handling incoming requests on the server side.
     * @param requestType     The request type
     * @param requestCodec    The request codec
     * @param responseChannel The name of the response packet channel, which will be combined with the mod ID to create a unique Identifier for sending responses back to clients and handling incoming responses on the client side.
     * @param responseType    The response type
     * @param responseCodec   The response codec
     */
    public RespondablePacketHandler(
            final String channel,
            final CustomPacketPayload.Type<T> requestType,
            final StreamCodec<RegistryFriendlyByteBuf, T> requestCodec,
            final String responseChannel,
            final CustomPacketPayload.Type<U> responseType,
            final StreamCodec<RegistryFriendlyByteBuf, U> responseCodec
    ) {
        super(channel, requestType, requestCodec);
        responseChannelId = ModConstants.modId(responseChannel);
        this.responseType = responseType;
        this.responseCodec = responseCodec;
    }

    /**
     * Sends a request packet of type T to the client and registers a consumer to handle the response of type U when it is received. This method generates a unique request ID, serializes the packet, and sends it to the client. The consumer is stored in a map keyed by the request ID, allowing for asynchronous handling of responses when they arrive.
     *
     * @param packet   The request packet of type T to be sent to the client, which will be serialized and transmitted over the network.
     * @param consumer A Consumer that will be called with the response packet of type U when it is received from the client. This allows for asynchronous processing of the response based on the original request.
     */
    public void send(final T packet, final Consumer<U> consumer) {
        UUID id = UUID.randomUUID();
        if (consumer != null) {
            responseConsumers.put(id, consumer);
        }
        ClientPlayNetworking.send(packet.withRequestId(id));
    }

    /**
     * Clears all pending client-side response consumers.
     * Call on disconnect so abandoned in-flight requests do not retain captured UI/config state.
     */
    public void clearPendingResponses() {

        responseConsumers.clear();
    }

    /**
     * Handles an incoming request packet on the server side. This method reads the request data from the PacketByteBuf using the provided reader function, processes the request by calling the async-capable handle method, and sends an immediate response when one is returned. Implementations that return null must call the provided responder exactly once later.
     *
     * @param packet  The deserialized request packet.
     * @param context The Fabric networking context for this server-side receive.
     */
    @Override
    public void receive(T packet, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        UUID requestId = packet.requestId();
        Consumer<U> responder = response -> respond(context.responseSender(), requestId, response);
        U immediate = handle(context.server(), context.player(), packet, responder);

        if (immediate != null) responder.accept(immediate);
    }

    /**
     * Serializes a response packet and sends it back on this handler's response channel for the supplied request ID.
     *
     * @param sender    The packet sender used to deliver the response to the client.
     * @param requestId The UUID read from the matching request packet.
     * @param response  The response packet to serialize and send.
     */
    protected void respond(PacketSender sender, UUID requestId, U response) {

        try {
            sender.sendPacket(response.withRequestId(requestId));
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to send response packet on channel {}", responseChannelId, e);
        }
    }

    /**
     * Processes a deserialized request packet and supports deferred responses.
     * <p>
     * Return a response packet to answer immediately, or return {@code null} and call {@code responder} exactly once
     * later. Missing the responder call leaves a dangling client-side response consumer, and calling it more than once
     * sends duplicate responses for the same request ID.
     *
     * @param server    The MinecraftServer instance representing the server on which the packet was received.
     * @param player    The ServerPlayerEntity representing the player who sent the packet.
     * @param packet    The deserialized request packet of type T that needs to be processed by the server.
     * @param responder Callback that sends the response for this request and must be called exactly once for deferred responses.
     * @return A response packet to send immediately, or {@code null} when {@code responder} will be called later.
     */
    protected U handle(MinecraftServer server, ServerPlayer player, T packet, Consumer<U> responder) {

        return handle(server, player, packet);
    }

    /**
     * Processes a deserialized request packet and produces a response packet synchronously.
     * <p>
     * Subclasses must override either this method or the async-capable overload with a responder callback.
     *
     * @param ignoredServer The MinecraftServer instance representing the server on which the packet was received.
     * @param ignoredPlayer The ServerPlayerEntity representing the player who sent the packet.
     * @param packet        The deserialized request packet of type T that needs to be processed by the server.
     * @return A response packet to send immediately.
     */
    protected U handle(MinecraftServer ignoredServer, ServerPlayer ignoredPlayer, T packet) {

        throw new UnsupportedOperationException("Subclasses must override one of the two handle overloads");
    }

    /**
     * Handles an incoming response packet on the client side.
     *
     * @param packet  The deserialized response packet.
     * @param context The Fabric networking context for this client-side receive.
     */
    @Override
    public void receive(U packet, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        UUID requestId = packet.requestId();
        Consumer<U> consumer = responseConsumers.remove(requestId);
        if (consumer != null) {
            consumer.accept(packet);
        }
    }
}
