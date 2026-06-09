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
import com.duom.ardamaps.core.consumers.networking.IServerPacketHandler;
import com.duom.ardamaps.core.consumers.networking.RespondablePacketHandler;
import com.duom.ardamaps.core.networking.handlers.client.PlayerExplorationEventHandler;
import com.duom.ardamaps.core.networking.handlers.server.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
    public static final GuidebookRequestHandler         GUIDEBOOK_REQUEST_HANDLER  = register(new GuidebookRequestHandler());
    public static final MapSourcesRequestHandler        MAP_SOURCES_REQUEST        = register(new MapSourcesRequestHandler());
    public static final LocationsRequestHandler         LOCATIONS_UPDATE_REQUEST   = register(new LocationsRequestHandler());
    public static final RegionsLutRequestHandler        REGION_LUT_UPDATE_REQUEST  = register(new RegionsLutRequestHandler());
    public static final PlayerTeleportHandler           PLAYER_TELEPORT_REQUEST    = register(new PlayerTeleportHandler());
    public static final PlayerWarpHandler               PLAYER_WARP_REQUEST        = register(new PlayerWarpHandler());
    public static final LocationDetailsRequestHandler   LOCATION_DETAILS_REQUEST   = register(new LocationDetailsRequestHandler());
    public static final PlayerExplorationEventHandler   PLAYER_EXPLORATION_EVENT   = registerClient(new PlayerExplorationEventHandler());

    /** Class cannot be instantiated. */
    private PacketRegistry() {
    }

    /**
     * Register a client-to-server packet handler.
     *
     * @param handler The handler to register
     */
    private static <T extends IServerPacketHandler<?>> T register(T handler) {

        ServerPlayNetworking.registerGlobalReceiver(handler.getChannelId(), handler::handle);
        var clientEnv = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

        if (clientEnv && handler instanceof RespondablePacketHandler<?, ?> responseHandler) {
            ClientPlayNetworking.registerGlobalReceiver(responseHandler.getResponseChannelId(), responseHandler::handle);
        }
        return handler;
    }

    /**
     * Register a server-to-client packet handler.
     *
     * @param handler The handler to register
     */
    private static <T extends IClientPacketHandler> T registerClient(T handler) {

        var clientEnv = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        if (clientEnv) {

            ClientPlayNetworking.registerGlobalReceiver(handler.getChannelId(), handler::handle);
        }

        return handler;
    }

    /**
     * Initialize the packet registry.
     */
    public static void init() {

        /*Ensure class is loaded to register the handlers*/
        LOGGER.info("Initializing Packet Registry");
    }
}