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

import com.duom.ardamaps.core.consumers.HuskHomesApiHook;
import com.duom.ardamaps.core.consumers.networking.ServerPacketHandler;
import com.duom.ardamaps.core.networking.packets.server.PlayerWarpPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Handler for the PlayerWarpPacket, responsible for warping the player to the specified warp location.
 */
public class PlayerWarpHandler extends ServerPacketHandler<PlayerWarpPacket> {

    /** The channel identifier for the PlayerWarpPacket. */
    private static final String REQ_CHANNEL = "player_warp";

    /**
     * Constructs a new PlayerWarpHandler.
     */
    public PlayerWarpHandler() {
        super(REQ_CHANNEL, PlayerWarpPacket::read);
    }

    /**
     * Handles the PlayerWarpPacket by warping the player to the specified warp location.
     *
     * @param server  The Minecraft server instance.
     * @param player  The player to teleport.
     * @param handler The network handler.
     * @param packet  The PlayerWarpPacket containing warp data.
     * @param sender  The packet sender.
     */
    @Override
    protected void handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PlayerWarpPacket packet, PacketSender sender) {

        server.execute(() -> HuskHomesApiHook.getInstance().getWarp(packet.warpName()).thenAccept((warpOpt) -> warpOpt.ifPresent(warp -> {

            Identifier dimensionId = new Identifier(warp.getWorld().getName());
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, dimensionId);

            ServerWorld serverWorld = server.getWorld(key);
            player.teleport(serverWorld, warp.getX(), warp.getY(), warp.getZ(), player.getYaw(), player.getPitch());
        })));
    }
}