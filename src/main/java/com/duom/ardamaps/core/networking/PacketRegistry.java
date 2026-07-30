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

package com.duom.ardamaps.core.networking;

import com.duom.ardamaps.core.consumers.networking.IClientPacketHandler;
import com.duom.ardamaps.core.consumers.networking.IPacket;
import com.duom.ardamaps.core.consumers.networking.IRespondablePacket;
import com.duom.ardamaps.core.consumers.networking.IServerPacketHandler;
import com.duom.ardamaps.core.consumers.networking.RespondablePacketHandler;
import com.duom.ardamaps.core.networking.handlers.client.PlayerExplorationEventHandler;
import com.duom.ardamaps.core.networking.handlers.server.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for all server-bound packets.
 */
public class PacketRegistry {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketRegistry.class);

    /** Server-bound packet handlers */
    public static final GuidebookRequestHandler         GUIDEBOOK_REQUEST_HANDLER      = register(new GuidebookRequestHandler());
    public static final MapSourcesRequestHandler        MAP_SOURCES_REQUEST            = register(new MapSourcesRequestHandler());
    public static final LocationsRequestHandler         LOCATIONS_UPDATE_REQUEST       = register(new LocationsRequestHandler());
    public static final RegionsLutRequestHandler        REGION_LUT_UPDATE_REQUEST      = register(new RegionsLutRequestHandler());
    public static final PlayerTeleportHandler           PLAYER_TELEPORT_REQUEST        = register(new PlayerTeleportHandler());
    public static final PlayerRangedTeleportHandler     PLAYER_RANGED_TELEPORT_REQUEST = register(new PlayerRangedTeleportHandler());
    public static final PlayerWarpHandler               PLAYER_WARP_REQUEST            = register(new PlayerWarpHandler());
    public static final LocationDetailsRequestHandler   LOCATION_DETAILS_REQUEST       = register(new LocationDetailsRequestHandler());
    public static final PlayerExplorationEventHandler   PLAYER_EXPLORATION_EVENT       = registerClient(new PlayerExplorationEventHandler());

    /** Class cannot be instantiated. */
    private PacketRegistry() {
    }

    /**
     * Register a client-to-server packet handler.
     *
     * @param handler The handler to register
     */
    private static <T extends IServerPacketHandler<?>> T register(T handler) {

        registerServerboundPayload(handler);
        var clientEnv = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

        if (!clientEnv) {
            registerServerReceiver(handler);
        }

        if (handler instanceof RespondablePacketHandler<?, ?> responseHandler) {
            registerClientboundResponsePayload(responseHandler);
            if (clientEnv) {
                registerClientResponseReceiver(responseHandler);
            }
        }
        return handler;
    }

    /**
     * Register a server-to-client packet handler.
     *
     * @param handler The handler to register
     */
    private static <T extends IClientPacketHandler<?>> T registerClient(T handler) {

        registerClientboundPayload(handler);
        var clientEnv = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        if (clientEnv) {

            registerClientReceiver(handler);
        }

        return handler;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerServerboundPayload(IServerPacketHandler<?> handler) {

        PayloadTypeRegistry.serverboundPlay().register((net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type) handler.getType(), handler.getCodec());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerClientboundPayload(IClientPacketHandler<?> handler) {

        PayloadTypeRegistry.clientboundPlay().register((net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type) handler.getType(), handler.getCodec());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerClientboundResponsePayload(RespondablePacketHandler<?, ?> handler) {

        PayloadTypeRegistry.clientboundPlay().register((net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type) handler.getResponseType(), handler.getResponseCodec());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerServerReceiver(IServerPacketHandler<?> handler) {

        ServerPlayNetworking.registerGlobalReceiver((net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type) handler.getType(), (packet, context) -> ((IServerPacketHandler) handler).receive((IPacket) packet, context));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerClientReceiver(IClientPacketHandler<?> handler) {

        ClientPlayNetworking.registerGlobalReceiver((net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type) handler.getType(), (packet, context) -> ((IClientPacketHandler) handler).receive((IPacket) packet, context));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerClientResponseReceiver(RespondablePacketHandler<?, ?> handler) {

        ClientPlayNetworking.registerGlobalReceiver((net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type) handler.getResponseType(), (packet, context) -> ((RespondablePacketHandler) handler).receive((IRespondablePacket) packet, context));
    }

    /**
     * Initialize the packet registry.
     */
    public static void init() {

        /*Ensure class is loaded to register the handlers*/
        LOGGER.info("Initializing Packet Registry");
    }

    /**
     * Clears client-side response callbacks for requests that can no longer receive a response.
     */
    public static void clearPendingResponses() {

        clearPendingResponses(GUIDEBOOK_REQUEST_HANDLER);
        clearPendingResponses(MAP_SOURCES_REQUEST);
        clearPendingResponses(LOCATIONS_UPDATE_REQUEST);
        clearPendingResponses(REGION_LUT_UPDATE_REQUEST);
        clearPendingResponses(PLAYER_TELEPORT_REQUEST);
        clearPendingResponses(PLAYER_RANGED_TELEPORT_REQUEST);
        clearPendingResponses(PLAYER_WARP_REQUEST);
        clearPendingResponses(LOCATION_DETAILS_REQUEST);
    }

    private static void clearPendingResponses(Object handler) {

        if (handler instanceof RespondablePacketHandler<?, ?> responseHandler)
            responseHandler.clearPendingResponses();
    }
}
