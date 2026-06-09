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
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.location.LocationServer;
import com.duom.ardamaps.core.networking.packets.client.LocationsResponsePacket;
import com.duom.ardamaps.core.networking.packets.server.LocationsRequestPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Handles incoming LocationsRequestPacket packets from clients, checks if the client's location data is outdated compared to the server's last update, and responds with a LocationsResponsePacket containing the updated location data if necessary.
 */
public class LocationsRequestHandler extends RespondablePacketHandler<LocationsRequestPacket, LocationsResponsePacket> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationsRequestHandler.class);

    /** The channel identifier for the LocationsRequestPacket and LocationsResponsePacket. */
    private static final String REQ_CHANNEL = "location_data_request";

    /** The channel identifier for the LocationsResponsePacket. */
    private static final String RESP_CHANNEL = "location_data_response";

    /**
     * Constructs a new LocationsRequestHandler, specifying the request and response channels and packet readers.
     */
    public LocationsRequestHandler() {
        super(REQ_CHANNEL, LocationsRequestPacket::read, RESP_CHANNEL, LocationsResponsePacket::read);
    }

    /**
     * Handles the incoming LocationsRequestPacket by checking if the client's location data is outdated compared to the server's last update, and responding with a LocationsResponsePacket containing the updated location data if necessary.
     *
     * @param server  The Minecraft server instance.
     * @param player  The player who sent the request.
     * @param handler The network handler for the player's connection.
     * @param packet  The LocationsRequestPacket containing the request data, including the client's last update timestamp.
     * @param sender  The packet sender to send the response back to the client.
     * @return A LocationsResponsePacket containing the updated location data if the client's data is outdated, or an empty response if the client's data is up-to-date.
     */
    @Override
    public LocationsResponsePacket handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, LocationsRequestPacket packet, PacketSender sender) {

        Date lastClientUpdate = packet.date();
        Date locationsLastServerUpdate = ArdaMaps.CONFIG.getLocationConfig().getLastUpdate();
        LocationsResponsePacket responsePacket;

        if (lastClientUpdate == null || lastClientUpdate.before(locationsLastServerUpdate)) {

            List<LocationClient> clientLocations = ArdaMaps.CONFIG.getLocationConfig().getLocations().stream()
                    .map(LocationServer::toLocationClient)
                    .toList();

            // Format a stripped location configuration for the client
            LocationConfig<LocationClient> clientConfig = new LocationConfig<>();
            clientConfig.setLastUpdate(locationsLastServerUpdate);
            clientConfig.setLocations(clientLocations);

            LOGGER.info("Sending updated locations data to client");
            responsePacket = new LocationsResponsePacket(clientConfig);

        } else {

            responsePacket = LocationsResponsePacket.EMPTY;
        }

        return responsePacket;
    }
}