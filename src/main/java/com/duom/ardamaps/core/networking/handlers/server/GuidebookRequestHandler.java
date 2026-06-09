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
import com.duom.ardamaps.core.items.ModItems;
import com.duom.ardamaps.core.networking.packets.EmptyPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

public class GuidebookRequestHandler extends ServerPacketHandler<EmptyPacket> {

    /** The channel identifier for the PlayerTeleportPacket. */
    private static final String REQ_CHANNEL = "guidebook_request";

    /**
     * Constructs a new PlayerTeleportHandler.
     */
    public GuidebookRequestHandler() {
        super(REQ_CHANNEL, EmptyPacket::read);
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
    protected void handle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, EmptyPacket packet, PacketSender sender) {

        server.execute(() -> giveGuidebook(player));
    }

    /**
     * Searches the player's inventory for a guidebook. If found, it selects it if it's in the hotbar. If not found, it gives the player a guidebook and selects it if it's inserted into the hotbar.
     *
     * @param player The player to give the guidebook to.
     */
    private void giveGuidebook(ServerPlayerEntity player) {

        // Search full inventory
        for (int i = 0; i < player.getInventory().size(); i++) {

            ItemStack stack = player.getInventory().getStack(i);

            if (stack.isOf(ModItems.GUIDEBOOK)) {

                // If guidebook is in hotbar, select it
                if (i < 9) {

                    player.getInventory().selectedSlot = i;

                    // Sync held item to client
                    player.playerScreenHandler.sendContentUpdates();
                }

                return;
            }
        }

        // No guidebook found - give one
        ItemStack newBook = new ItemStack(ModItems.GUIDEBOOK);

        boolean inserted = player.getInventory().insertStack(newBook);

        if (inserted) {

            // Search hotbar for inserted guidebook
            for (int i = 0; i < 9; i++) {

                ItemStack stack = player.getInventory().getStack(i);

                if (stack.isOf(ModItems.GUIDEBOOK)) {

                    player.getInventory().selectedSlot = i;

                    break;
                }
            }

            // Sync inventory + selected slot
            player.playerScreenHandler.sendContentUpdates();
        }
    }
}
