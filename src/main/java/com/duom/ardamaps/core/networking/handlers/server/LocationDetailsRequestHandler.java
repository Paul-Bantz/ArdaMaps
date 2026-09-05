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
import com.duom.ardamaps.core.data.location.LocationDetails;
import com.duom.ardamaps.core.networking.packets.client.LocationDetailsResponsePacket;
import com.duom.ardamaps.core.networking.packets.server.LocationDetailsRequestPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * A packet handler that processes LocationDetailsRequestPacket sent from the client and responds with a LocationDetailsResponsePacket containing detailed information about a specific location.
 */
public class LocationDetailsRequestHandler extends RespondablePacketHandler<LocationDetailsRequestPacket, LocationDetailsResponsePacket> {

    /** The channel identifier for the location details request and response packets. */
    private static final String REQ_CHANNEL = "location_details_request";

    /** The channel identifier for the location details response packet. */
    private static final String RESP_CHANNEL = "location_details_response";

    /**
     * Constructs a new LocationDetailsRequestHandler, specifying the request and response channels and packet readers.
     */
    public LocationDetailsRequestHandler() {
        super(REQ_CHANNEL, LocationDetailsRequestPacket::read, RESP_CHANNEL, LocationDetailsResponsePacket::read);
    }

    /**
     * Handles the incoming LocationDetailsRequestPacket by retrieving the details of the requested location and responding with a LocationDetailsResponsePacket containing the relevant information.
     *
     * @param server  The Minecraft server instance.
     * @param player  The player who sent the request.
     * @param handler The network handler for the player's connection.
     * @param packet  The LocationDetailsRequestPacket containing the request data, including the location identifier.
     * @param sender  The packet sender to send the response back to the client.
     * @return A LocationDetailsResponsePacket containing detailed information about the requested location, or default values if the location is not found.
     */
    @Override
    public LocationDetailsResponsePacket handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, LocationDetailsRequestPacket packet, PacketSender sender) {

        var locations = ArdaMaps.CONFIG.getLocations();
        var location = locations.stream()
                .filter(loc -> loc.getName().equals(packet.locationIdentifier()))
                .findFirst();

        LocationDetails details;

        if (location.isPresent()) {

            var locationServer = location.get();
            details = new LocationDetails(locationServer.getName(), locationServer.isCanon(), locationServer.getDescription(), locationServer.getExternalUrl());

        } else {

            details = new LocationDetails(packet.locationIdentifier());
        }

        return new LocationDetailsResponsePacket(details);
    }
}