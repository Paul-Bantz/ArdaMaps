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
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.networking.packets.client.RegionsGeometryResponsePacket;
import com.duom.ardamaps.core.networking.packets.server.RegionsGeometryRequestPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handles client requests for vector region geometry.
 */
public class RegionsGeometryRequestHandler extends RespondablePacketHandler<RegionsGeometryRequestPacket, RegionsGeometryResponsePacket> {

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionsGeometryRequestHandler.class);

    /** The channel identifier for the request packet. */
    private static final String REQ_CHANNEL = "regions_geometry_data_request";

    /** The channel identifier for the response packet. */
    private static final String RESP_CHANNEL = "regions_geometry_data_response";

    /**
     * Constructs a region geometry request handler.
     */
    public RegionsGeometryRequestHandler() {
        super(REQ_CHANNEL, RegionsGeometryRequestPacket.TYPE, RegionsGeometryRequestPacket.CODEC,
                RESP_CHANNEL, RegionsGeometryResponsePacket.TYPE, RegionsGeometryResponsePacket.CODEC);
    }

    /**
     * Handles a region geometry request.
     *
     * @param server The server instance.
     * @param player The requesting player.
     * @param packet The request packet.
     * @return A response containing updated geometry, or an empty response.
     */
    @Override
    public RegionsGeometryResponsePacket handle(MinecraftServer server, ServerPlayer player, RegionsGeometryRequestPacket packet) {

        List<RegionGeometry> payload = new ArrayList<>();
        List<String> serverDimensionIds = new ArrayList<>();

        for (var entry : ArdaMaps.CONFIG.getRegionGeometryByDimension().entrySet()) {
            String dimensionId = entry.getKey();
            RegionGeometry serverGeometry = entry.getValue();
            if (serverGeometry == null || serverGeometry.lastUpdate() == null) continue;

            serverDimensionIds.add(dimensionId);
            Date lastClientUpdate = packet.knownGeometry().get(dimensionId);
            Date lastServerUpdate = serverGeometry.lastUpdate();
            if (lastClientUpdate == null || lastClientUpdate.before(lastServerUpdate)) {
                LOGGER.info("Sending updated region geometry data to client for {}", dimensionId);
                payload.add(serverGeometry);
            }
        }

        LOGGER.info("Sending {}/{} region geometries to client", payload.size(), serverDimensionIds.size());
        if (payload.isEmpty() && serverDimensionIds.isEmpty()) return RegionsGeometryResponsePacket.EMPTY;
        return new RegionsGeometryResponsePacket(payload, serverDimensionIds);
    }
}
