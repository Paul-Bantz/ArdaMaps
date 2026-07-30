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

package com.duom.ardamaps.core.networking.handlers.server;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.consumers.networking.RespondablePacketHandler;
import com.duom.ardamaps.core.integration.Regions;
import com.duom.ardamaps.core.integration.Warps;
import com.duom.ardamaps.core.networking.packets.client.MapSourceResponsePacket;
import com.duom.ardamaps.core.networking.packets.server.MapSourcesRequestPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handler for map source requests, responsible for responding with map layer configuration and feature availability.
 */
public class MapSourcesRequestHandler extends RespondablePacketHandler<MapSourcesRequestPacket, MapSourceResponsePacket> {

    /** The channel identifier for the map source request and response packets. */
    private static final String REQ_CHANNEL = "map_source_request";

    /** The channel identifier for the map source response packet. */
    private static final String RESP_CHANNEL = "map_source_response";

    /**
     * Constructs a new MapSourcesRequestHandler, specifying the request and response channels and packet readers.
     */
    public MapSourcesRequestHandler() {
        super(REQ_CHANNEL, MapSourcesRequestPacket.TYPE, MapSourcesRequestPacket.CODEC,
                RESP_CHANNEL, MapSourceResponsePacket.TYPE, MapSourceResponsePacket.CODEC);
    }

    /**
     * Handles the incoming map source request by retrieving dimension and map layer configuration and responding with feature availability.
     *
     * @param server The Minecraft server instance.
     * @param player The player who sent the request.
     * @param packet The MapSourcesRequestPacket containing the request data.
     * @return A MapSourceResponsePacket containing the map source configuration and feature availability.
     */
    @Override
    public MapSourceResponsePacket handle(MinecraftServer server, ServerPlayer player, MapSourcesRequestPacket packet) {

        return new MapSourceResponsePacket(Warps.isAvailable(), Regions.isAvailable(), ArdaMaps.CONFIG.getDimensions());
    }
}
