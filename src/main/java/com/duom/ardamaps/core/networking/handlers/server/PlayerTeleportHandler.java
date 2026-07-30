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

import com.duom.ardamaps.core.consumers.networking.ServerPacketHandler;
import com.duom.ardamaps.core.networking.packets.server.PlayerTeleportPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;
import java.util.Set;

/**
 * Handler for the PlayerTeleportPacket, responsible for teleporting the player to the specified coordinates,
 * optionally in a different world.
 */
public class PlayerTeleportHandler extends ServerPacketHandler<PlayerTeleportPacket> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTeleportHandler.class);

    /** The channel identifier for the PlayerTeleportPacket. */
    private static final String REQ_CHANNEL = "player_teleport";

    /**
     * Constructs a new PlayerTeleportHandler.
     */
    public PlayerTeleportHandler() {
        super(REQ_CHANNEL, PlayerTeleportPacket.TYPE, PlayerTeleportPacket.CODEC);
    }

    /**
     * Handles the PlayerTeleportPacket by teleporting the player to the specified coordinates.
     *
     * @param server The Minecraft server instance.
     * @param player The player to teleport.
     * @param packet The PlayerTeleportPacket containing teleportation data.
     */
    @Override
    protected void handle(MinecraftServer server, ServerPlayer player, PlayerTeleportPacket packet) {

        server.execute(() -> {

            if (packet.worldId() != null) {

                var worlds = server.getAllLevels();
                ServerLevel serverWorld = null;

                // Search for the world with the matching registry key
                for (var world : worlds) {

                    if (world.dimension().identifier().toString().equals(packet.worldId())) {

                        LOGGER.info("World found: {}", world.dimension().identifier());
                        serverWorld = world;
                        break;
                    }
                }

                if (serverWorld != null) {

                    if (Double.isNaN(packet.y())) {

                        double x = SafeTeleportScanner.blockCenter(packet.x());
                        double z = SafeTeleportScanner.blockCenter(packet.z());
                        OptionalDouble safeY = findSafeY(serverWorld, player, x, z);
                        double teleportY;

                        if (safeY.isEmpty()) {

                            BlockPos pos = serverWorld.getHeightmapPos(
                                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    BlockPos.containing(x, 0, z)
                            );
                            teleportY = pos.getY() + 1;
                        } else {

                            teleportY = safeY.getAsDouble();
                            LOGGER.info("Safe position found at: {}, {}, {}", x, teleportY, z);
                        }

                        player.teleportTo(serverWorld, x, teleportY, z, Set.of(), player.getYRot(), player.getXRot(), true);
                    } else {

                        player.teleportTo(serverWorld, packet.x(), packet.y(), packet.z(), Set.of(), player.getYRot(), player.getXRot(), true);
                    }

                    return;
                }
            }

            player.teleportTo(packet.x(), packet.y(), packet.z());

        });
    }

    /**
     * Finds a safe standing Y coordinate for teleportation at the given X and Z coordinates in the specified world.
     *
     * @param world The world to search in.
     * @param player The player whose standing dimensions are being placed.
     * @param x     The snapped X coordinate to check.
     * @param z     The snapped Z coordinate to check.
     * @return The exact standing Y coordinate, or empty if no safe position is found.
     */
    public static OptionalDouble findSafeY(ServerLevel world, ServerPlayer player, double x, double z) {
        int topY = world.getMaxY();
        int bottomY = world.getMinY();

        for (int y = topY - 2; y >= bottomY; y--) {
            OptionalDouble safeY = SafeTeleportScanner.standingHeightAt(world, player, x, y, z);

            if (safeY.isPresent()) return safeY;
        }

        return OptionalDouble.empty();
    }

}
