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
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import com.duom.ardamaps.core.networking.packets.client.RegionsLutResponsePacket;
import com.duom.ardamaps.core.networking.packets.server.RegionsLutRequestPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Handles incoming RegionsLutRequestPacket packets from clients, checks if the client's region LUT data is outdated compared to the server's last update, and responds with a RegionsLutResponsePacket containing the updated region LUT data if necessary.
 */
public class RegionsLutRequestHandler extends RespondablePacketHandler<RegionsLutRequestPacket, RegionsLutResponsePacket> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionsLutRequestHandler.class);

    /** The channel identifier for the RegionsLutRequestPacket and RegionsLutResponsePacket. */
    private static final String REQ_CHANNEL = "regions_lut_data_request";

    /** The channel identifier for the RegionsLutResponsePacket. */
    private static final String RESP_CHANNEL = "regions_lut_data_response";

    /**
     * Constructs a new RegionsLutRequestHandler, specifying the request and response channels and packet readers.
     */
    public RegionsLutRequestHandler() {
        super(REQ_CHANNEL, RegionsLutRequestPacket::read, RESP_CHANNEL, RegionsLutResponsePacket::read);
    }

    /**
     * Handles the incoming RegionsLutRequestPacket by checking if the client's region LUT data is outdated compared to the server's last update, and responding with a RegionsLutResponsePacket containing the updated region LUT data if necessary.
     *
     * @param server  The Minecraft server instance.
     * @param player  The player who sent the request.
     * @param handler The network handler for the player's connection.
     * @param packet  The RegionsLutRequestPacket containing the request data, including the client's last update timestamp.
     * @param sender  The packet sender to send the response back to the client.
     * @return A RegionsLutResponsePacket containing the updated region LUT data if the client's data is outdated, or an empty response if the client's data is up-to-date.
     */
    @Override
    public RegionsLutResponsePacket handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, RegionsLutRequestPacket packet, PacketSender sender) {

        Date lastClientUpdate = packet.date();
        RegionLookupTexture serverRegionLut = ArdaMaps.CONFIG.getRegionLookupTexture();
        Date locationsLastServerUpdate = serverRegionLut.lastUpdate();
        RegionsLutResponsePacket responsePacket;

        if (lastClientUpdate == null || lastClientUpdate.before(locationsLastServerUpdate)) {

            LOGGER.info("Sending updated region LUT data to client");
            responsePacket = new RegionsLutResponsePacket(serverRegionLut);

        } else {

            responsePacket = RegionsLutResponsePacket.EMPTY;
        }

        return responsePacket;
    }
}
