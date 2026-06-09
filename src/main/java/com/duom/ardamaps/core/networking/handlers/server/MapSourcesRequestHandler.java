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
import com.duom.ardamaps.core.networking.packets.EmptyPacket;
import com.duom.ardamaps.core.networking.packets.client.MapSourceResponsePacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * A packet sent from the client to the server to request map source data.
 * This returns a list of map layers that the client can display.
 */
public class MapSourcesRequestHandler extends RespondablePacketHandler<EmptyPacket, MapSourceResponsePacket> {

    /** The channel identifier for the map source request and response packets. */
    private static final String REQ_CHANNEL = "map_source_request";

    /** The channel identifier for the map source response packet. */
    private static final String RESP_CHANNEL = "map_source_response";

    /**
     * Constructs a new MapSourcesRequestHandler, specifying the request and response channels and packet readers.
     */
    public MapSourcesRequestHandler() {
        super(REQ_CHANNEL, EmptyPacket::read, RESP_CHANNEL, MapSourceResponsePacket::read);
    }

    /**
     * Handles the incoming EmptyPacket by retrieving the map source configuration and responding with a MapSourceResponsePacket containing the configuration in JSON format.
     *
     * @param server  The Minecraft server instance.
     * @param player  The player who sent the request.
     * @param handler The network handler for the player's connection.
     * @param packet  The EmptyPacket containing the request data (empty in this case).
     * @param sender  The packet sender to send the response back to the client.
     * @return A MapSourceResponsePacket containing the map source configuration in JSON format.
     */
    @Override
    public MapSourceResponsePacket handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, EmptyPacket packet, PacketSender sender) {

        return new MapSourceResponsePacket(ArdaMaps.CONFIG.getDimensions());
    }
}