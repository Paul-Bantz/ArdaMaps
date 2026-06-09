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

package com.duom.ardamaps.core.networking.handlers.client;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.consumers.networking.ServerToClientPacketHandler;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.networking.packets.client.PlayerExplorationPacket;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles incoming {@link PlayerExplorationPacket} packets from the server, updates the client's
 * per-dimension exploration state, and requests parent-region polygon data when needed.
 */
public class PlayerExplorationEventHandler extends ServerToClientPacketHandler<PlayerExplorationPacket> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerExplorationEventHandler.class);
    
    /** The channel identifier for the PlayerExplorationPacket. */
    public static final String RESP_CHANNEL = "player_exploration_event";

    public PlayerExplorationEventHandler() {
        super(RESP_CHANNEL, PlayerExplorationPacket::read);
    }

    @Override
    protected void handle(MinecraftClient client, PlayerExplorationPacket packet) {
        client.execute(() -> {

            // Discard packet if empty or null
            if (packet == null || packet.isEmpty()) return;

            // Resolve the per-dimension exploration instance; skip if unknown dimension.
            PlayerExploration exploration = ArdaMapsClient.CONFIG.getClientProgress().getExplorationState(packet.dimensionId(), false);

            // Add the discovered region to the progress state
            ArdaMapsClient.CONFIG.getLocations().stream()
                    .filter(loc -> loc.getId().equals(packet.regionId()))
                    .findFirst()
                    .ifPresent(loc -> loc.setVisited(true));

            if (exploration == null) {
                LOGGER.warn("Received PlayerExplorationPacket for unknown dimension '{}', ignoring.", packet.dimensionId());
                return;
            }

            revealExploredArea(exploration, packet.regionPolygon(), ExplorationState.REVEALED);
            revealExploredArea(exploration, packet.parentRegionPolygon(), ExplorationState.VISIBLE);

            ArdaMapsClient.CONFIG_MANAGER.synchronizeLocationExplorationProgress();

            ArdaMapsClient.CONFIG_MANAGER.saveProgress();
        });
    }

    /**
     * Reveals cells covered by the given regionPolygon on the provided {@link PlayerExploration} instance.
     *
     * @param exploration The per-dimension exploration instance to update.
     * @param polygons    Polygon list in world coordinates.
     * @param state       The exploration state to apply.
     */
    private void revealExploredArea(PlayerExploration exploration,
                                    List<List<Vec2d>> polygons,
                                    ExplorationState state) {

        for (List<Vec2d> polygon : polygons) {
            if (polygon.isEmpty()) continue;

            // Find bounding box of polygon
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

            for (Vec2d vertex : polygon) {
                minX = Math.min(minX, vertex.x());
                maxX = Math.max(maxX, vertex.x());
                minZ = Math.min(minZ, vertex.y());  // vertex.y() is world Z
                maxZ = Math.max(maxZ, vertex.y());
            }

            // Convert to cell coordinates using the instance-aware methods
            int minCellX = exploration.toCellX(minX);
            int maxCellX = exploration.toCellX(maxX);
            int minCellZ = exploration.toCellZ(minZ);
            int maxCellZ = exploration.toCellZ(maxZ);

            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {

                    double worldX = exploration.cellCenterX(cellX);
                    double worldZ = exploration.cellCenterZ(cellZ);

                    if (isPointInPolygon(worldX, worldZ, polygon)) {
                        var currentState = exploration.stateAt(cellX, cellZ);
                        if (currentState.ordinal() < state.ordinal()) {
                            exploration.markCell(cellX, cellZ, state);
                        }
                    }
                }
            }
        }

        exploration.flushTexture();
    }

    /**
     * Ray-casting point-in-polygon test.
     *
     * @param x       World X coordinate of the point.
     * @param z       World Z coordinate of the point.
     * @param polygon Polygon vertices ({@code vertex.x()} = world X, {@code vertex.y()} = world Z).
     * @return {@code true} if the point is inside the polygon.
     */
    private boolean isPointInPolygon(double x, double z, List<Vec2d> polygon) {
        boolean inside = false;
        int j = polygon.size() - 1;

        for (int i = 0; i < polygon.size(); i++) {
            Vec2d vi = polygon.get(i);
            Vec2d vj = polygon.get(j);

            if ((vi.y() > z) != (vj.y() > z) &&
                    x < (vj.x() - vi.x()) * (z - vi.y()) / (vj.y() - vi.y()) + vi.x()) {
                inside = !inside;
            }
            j = i;
        }

        return inside;
    }
}