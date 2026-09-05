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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Handler for guidebook requests, responsible for giving or selecting the guidebook in a player's inventory.
 */
public class GuidebookRequestHandler extends ServerPacketHandler<EmptyPacket> {

    /** The channel identifier for the guidebook request packet. */
    private static final String REQ_CHANNEL = "guidebook_request";

    /**
     * Constructs a new GuidebookRequestHandler.
     */
    public GuidebookRequestHandler() {
        super(REQ_CHANNEL, EmptyPacket.TYPE, EmptyPacket.CODEC);
    }

    /**
     * Handles the guidebook request by giving the player a guidebook or selecting an existing one from their inventory.
     *
     * @param server The Minecraft server instance.
     * @param player The player to give the guidebook to.
     * @param packet The empty packet containing the request.
     */
    @Override
    protected void handle(MinecraftServer server, ServerPlayer player, EmptyPacket packet) {

        server.execute(() -> giveGuidebook(player));
    }

    /**
     * Searches the player's inventory for a guidebook. If found, it selects it if it's in the hotbar. If not found, it gives the player a guidebook and selects it if it's inserted into the hotbar.
     *
     * @param player The player to give the guidebook to.
     */
    private void giveGuidebook(ServerPlayer player) {

        // Search full inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {

            ItemStack stack = player.getInventory().getItem(i);

            if (stack.is(ModItems.GUIDEBOOK)) {

                // If guidebook is in hotbar, select it
                if (i < 9) {

                    player.getInventory().setSelectedSlot(i);

                    // Sync held item to client
                    player.inventoryMenu.broadcastChanges();
                }

                return;
            }
        }

        // No guidebook found - give one
        ItemStack newBook = new ItemStack(ModItems.GUIDEBOOK);

        boolean inserted = player.getInventory().add(newBook);

        if (inserted) {

            // Search hotbar for inserted guidebook
            for (int i = 0; i < 9; i++) {

                ItemStack stack = player.getInventory().getItem(i);

                if (stack.is(ModItems.GUIDEBOOK)) {

                    player.getInventory().setSelectedSlot(i);

                    break;
                }
            }

            // Sync inventory + selected slot
            player.inventoryMenu.broadcastChanges();
        }
    }
}
