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

package com.duom.ardamaps.gui.screens.map;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.server.PlayerRangedTeleportPacket;
import com.duom.ardamaps.core.networking.packets.server.PlayerTeleportPacket;
import com.duom.ardamaps.gui.widgets.ContextMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Factory that assembles the right-click context menu for the map screen.
 * Extracted from {@code MapScreen} to keep menu-entry composition testable and separate from input handling.
 */
@Environment(EnvType.CLIENT)
public final class MapContextMenuFactory {

    /** Utility class; not instantiated. */
    private MapContextMenuFactory() {

    }

    /**
     * Builds the {@link ContextMenu} for a right-click on the map.
     *
     * @param mouseX              Screen x position of the click.
     * @param mouseY              Screen y position of the click.
     * @param screenWidth         Full screen width, used for reveal-direction logic.
     * @param screenHeight        Full screen height, used for reveal-direction logic.
     * @param camera              The active map camera.
     * @param selectedRange       The currently selected vertical range, or {@code null} for unranged layers.
     * @param hoveredWaypoint     The waypoint under the cursor, or {@code null} when none is hovered.
     * @param outsideExploredArea {@code true} when the point is outside the player's explored area.
     * @param switchToLayer       Called with the Y returned by a ranged teleport response to switch the layer.
     * @param closeMenu           Runnable that closes/clears the context menu in the owning screen.
     * @param onTeleport          Runnable invoked when the teleport entry is selected.
     * @return The fully assembled {@link ContextMenu} ready to be displayed.
     */
    public static ContextMenu create(int mouseX, int mouseY, int screenWidth, int screenHeight,
                                     MapCamera camera,
                                     @Nullable MapLayerRange selectedRange,
                                     @Nullable Waypoint hoveredWaypoint,
                                     boolean outsideExploredArea,
                                     Consumer<Double> switchToLayer,
                                     Runnable closeMenu,
                                     Runnable onTeleport) {

        var contextPos = camera.screenToWorldCoordinates(mouseX, mouseY);

        var addWaypointEntry = new ContextMenu.Entry(
                Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint"), () -> {

            setWaypointAt(camera, contextPos.x(), contextPos.y());
            closeMenu.run();
        });

        var teleportToEntry = new ContextMenu.Entry(
                Component.translatable("ardamaps.client.map.screen.context.menu.teleport"), () -> {

            closeMenu.run();
            onTeleport.run();
            teleportTo(camera, selectedRange, contextPos.x(), contextPos.y(), switchToLayer);
        });

        List<ContextMenu.Entry> entries = buildEntries(
                camera, addWaypointEntry, teleportToEntry,
                hoveredWaypoint, outsideExploredArea, closeMenu,
                dimensionId -> ArdaMapsClient.CONFIG.hasWaypoint(dimensionId));

        return new ContextMenu(mouseX, mouseY, screenWidth, screenHeight,
                contextPos.x(), contextPos.y(), entries, ContextMenu.DEFAULT_MIN_WIDTH);
    }

    /**
     * Teleports the player to the given map position.
     *
     * @param camera        The active map camera.
     * @param range         The selected layer range, or {@code null} for unranged layers.
     * @param worldX        The target world X coordinate.
     * @param worldZ        The target world Z coordinate.
     * @param switchToLayer Called with the Y returned by a ranged teleport response to switch the layer.
     */
    public static void teleportTo(MapCamera camera, @Nullable MapLayerRange range, double worldX, double worldZ,
                                  Consumer<Double> switchToLayer) {

        if (range != null) {

            PacketRegistry.PLAYER_RANGED_TELEPORT_REQUEST.send(new PlayerRangedTeleportPacket(
                    worldX,
                    worldZ,
                    camera.getDimension().getId(),
                    range.rangeMinY(),
                    range.rangeMaxY()), response -> {

                if (response.success()) switchToLayer.accept(response.y());
            });

        } else {

            PacketRegistry.PLAYER_TELEPORT_REQUEST.send(
                    new PlayerTeleportPacket(worldX, worldZ, camera.getDimension().getId()));
        }
    }

    /**
     * Sets a waypoint at the given map position.
     *
     * @param camera The active map camera.
     * @param worldX The waypoint world X coordinate.
     * @param worldZ The waypoint world Z coordinate.
     */
    public static void setWaypointAt(MapCamera camera, double worldX, double worldZ) {

        ArdaMapsClient.CONFIG.setWaypoint(worldX, worldZ, camera.getDimension().getId());
    }

    /**
     * Assembles the menu-entry list from the available actions.
     *
     * @param camera              The active map camera.
     * @param addWaypointEntry    The "add waypoint" entry, always available when no waypoint is hovered.
     * @param teleportToEntry     The "teleport here" entry, only added when the point is explored.
     * @param hoveredWaypoint     The waypoint under the cursor, or {@code null}.
     * @param outsideExploredArea {@code true} when the teleport entry should be suppressed.
     * @param closeMenu           Runnable to clear the menu.
     * @param hasWaypoint         Returns {@code true} when a waypoint exists for the given dimension id.
     * @return The assembled entry list.
     */
    static List<ContextMenu.Entry> buildEntries(MapCamera camera,
                                                ContextMenu.Entry addWaypointEntry,
                                                ContextMenu.Entry teleportToEntry,
                                                @Nullable Waypoint hoveredWaypoint,
                                                boolean outsideExploredArea,
                                                Runnable closeMenu,
                                                java.util.function.Predicate<String> hasWaypoint) {

        var items = new ArrayList<ContextMenu.Entry>();

        if (!outsideExploredArea) items.add(teleportToEntry);

        if (hoveredWaypoint != null) {

            var staticWaypoint = Waypoint.copy(hoveredWaypoint);

            var shareEntry = new ContextMenu.Entry(
                    Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.share"), () -> {

                var player = Client.player();
                if (player == null) {
                    closeMenu.run();
                    return;
                }

                var playerName = player.getName().getString();
                var sharedWaypoint = new Waypoint(
                        staticWaypoint.x(),
                        staticWaypoint.z(),
                        String.format("%s [%d,%d]", playerName, staticWaypoint.x(), staticWaypoint.z()),
                        0.5882f, 0f, 1f,
                        playerName,
                        staticWaypoint.dimension());

                Client.mc().keyboardHandler.setClipboard("waypoint:" + Waypoint.toJson(sharedWaypoint));
                player.sendSystemMessage(
                        Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.share.message"));
                closeMenu.run();
            });

            var removeEntry = new ContextMenu.Entry(
                    Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.remove"), () -> {

                ArdaMapsClient.CONFIG.removeWaypoint(staticWaypoint);
                closeMenu.run();
            });

            items.add(shareEntry);
            items.add(removeEntry);

        } else {

            var clearEntry = new ContextMenu.Entry(
                    Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.clear"), () -> {

                ArdaMapsClient.CONFIG.clearWaypoints(camera.getDimension().getId());
                closeMenu.run();
            });

            if (hasWaypoint.test(camera.getDimension().getId())) items.add(clearEntry);

            items.add(addWaypointEntry);
        }

        return items;
    }
}
