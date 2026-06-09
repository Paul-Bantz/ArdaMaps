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
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        super(REQ_CHANNEL, PlayerTeleportPacket::read);
    }

    /**
     * Handles the PlayerTeleportPacket by teleporting the player to the specified coordinates.
     *
     * @param server  The Minecraft server instance.
     * @param player  The player to teleport.
     * @param handler The network handler.
     * @param packet  The PlayerTeleportPacket containing teleportation data.
     * @param sender  The packet sender.
     */
    @Override
    protected void handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PlayerTeleportPacket packet, PacketSender sender) {

        server.execute(() -> {

            if (packet.worldId() != null) {

                var worlds = server.getWorlds();
                ServerWorld serverWorld = null;

                // Search for the world with the matching registry key
                for (var world : worlds) {

                    if (world.getRegistryKey().getValue().toString().equals(packet.worldId())) {

                        LOGGER.info("World found: {}", world.getRegistryKey().getValue());
                        serverWorld = world;
                        break;
                    }
                }

                if (serverWorld != null) {

                    if (Double.isNaN(packet.y())) {

                        BlockPos pos = findSafeY(serverWorld, (int) packet.x(), (int) packet.z());

                        if (pos == null) {

                            pos = serverWorld.getTopPosition(
                                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                    new BlockPos((int) Math.ceil(packet.x()), 0, (int) Math.ceil(packet.z()))
                            );
                        } else {

                            LOGGER.info("Safe position found at: {}", pos);
                        }

                        player.teleport(serverWorld, packet.x(), pos.getY() + 1, packet.z(), player.getYaw(), player.getPitch());
                    } else {

                        player.teleport(serverWorld, packet.x(), packet.y(), packet.z(), player.getYaw(), player.getPitch());
                    }

                    return;
                }
            }

            player.teleport(packet.x(), packet.y(), packet.z());

        });
    }

    /**
     * Finds a safe Y coordinate for teleportation at the given X and Z coordinates in the specified world.
     *
     * @param world The world to search in.
     * @param x     The X coordinate to check.
     * @param z     The Z coordinate to check.
     * @return A BlockPos representing the safe Y coordinate, or null if no safe position is found.
     */
    public static BlockPos findSafeY(ServerWorld world, int x, int z) {
        int topY = world.getTopY();
        int bottomY = world.getBottomY();

        for (int y = topY; y > bottomY; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.up();
            BlockPos ground = feet.down();

            if (isAir(world, feet)
                    && isAir(world, head)
                    && isSolid(world, ground)) {
                return feet;
            }
        }

        return null;
    }

    /**
     * Checks if the block at the given position in the world is air.
     *
     * @param world The world to check in.
     * @param pos   The position to check.
     * @return True if the block is air, false otherwise.
     */
    private static boolean isAir(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isAir();
    }

    /**
     * Checks if the block at the given position in the world is solid.
     *
     * @param world The world to check in.
     * @param pos   The position to check.
     * @return True if the block is solid, false otherwise.
     */
    private static boolean isSolid(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isSolidBlock(world, pos);
    }
}