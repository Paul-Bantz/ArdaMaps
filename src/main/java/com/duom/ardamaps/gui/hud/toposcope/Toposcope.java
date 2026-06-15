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

package com.duom.ardamaps.gui.hud.toposcope;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.server.PlayerTeleportPacket;
import com.duom.ardamaps.core.networking.packets.server.PlayerWarpPacket;
import com.duom.ardamaps.gui.hud.toposcope.rendering.ToposcopeRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Handles the toposcope functionality, including overlay toggling and landmark interaction.
 */
public class Toposcope {

    /** Indicates whether the toposcope overlay is currently enabled. */
    public static boolean overlayEnabled = false;

    /** Tracks the previous state of the left mouse button to detect clicks. */
    private boolean leftMouseButtonWasDown = false;

    /** Tracks the previous state of the right mouse button to detect clicks. */
    private boolean rightMouseButtonWasDown = false;

    /**
     * Set to true for the duration of the tick in which a left-click on a location was consumed.
     * Prevents the render-stale {@code hoveredLocation} from causing a missed suppression after
     * a teleport clears the hover state before {@code AttackBlockCallback} fires.
     */
    private static boolean locationClickConsumed = false;

    /**
     * Set to true for the duration of the tick in which a right-click on a location was consumed.
     * Symmetric guard for right-click suppression callbacks.
     */
    private static boolean rightClickConsumed = false;

    /**
     * Registers event handlers for the toposcope functionality.
     */
    public void registerRenderer() {

        HudRenderCallback.EVENT.register((drawContext, v) -> ToposcopeRenderer.render(drawContext));
        registerMouseHoveringCallbacks();
    }

    /**
     * Registers the locations mouse hovering even sinks to suppress vanilla interactions when
     * the overlay is active and the player is hovering over a location marker.
     */
    private void registerMouseHoveringCallbacks() {

        // Suppress vanilla left-click: block breaking
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (shouldSuppressLeftClick())
                return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        // Suppress vanilla left-click: entity attack
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (shouldSuppressLeftClick())
                return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        // Suppress vanilla right-click: block interaction
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (shouldSuppressRightClick())
                return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        // Suppress vanilla right-click: entity interaction
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (shouldSuppressRightClick())
                return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        // Suppress vanilla right-click: item use in air
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (shouldSuppressRightClick())
                return TypedActionResult.fail(player.getStackInHand(hand));
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }

    /**
     * @return true if the overlay is enabled and player is hovering a location
     */
    private static boolean isMouseOverLocation() {
        return overlayEnabled && ToposcopeRenderer.getHoveredLocation() != null;
    }

    /**
     * @return true if a vanilla left-click action should be suppressed this tick.
     * Covers both the live hover state and the tick in which a location click was consumed,
     * guarding against the render-stale race after teleportation.
     */
    private static boolean shouldSuppressLeftClick() {
        return overlayEnabled && (locationClickConsumed || isMouseOverLocation());
    }

    /**
     * @return true if a vanilla right-click action should be suppressed this tick.
     * Covers both the live hover state and the tick in which a waypoint click was consumed.
     */
    private static boolean shouldSuppressRightClick() {
        return overlayEnabled && (rightClickConsumed || isMouseOverLocation());
    }

    /**
     * Toggles the overlay on or off based on the provided enabled state (driven by the V keybind).
     *
     * @param client  The Minecraft client instance.
     * @param enabled {@code true} if the toposcope should be visible.
     */
    public void toggleOverlay(MinecraftClient client, boolean enabled) {

        if (client.player == null) return;

        overlayEnabled = enabled;
    }

    /**
     * Handles mouse click events to detect clicks on landmarks.
     *
     * @param client The Minecraft client instance.
     */
    @SuppressWarnings("DataFlowIssue")
    public void handleMouseClick(MinecraftClient client) {

        if (client.player == null) return;

        // Reset tick-stable suppression flags at the start of each tick.
        locationClickConsumed = false;
        rightClickConsumed = false;

        long handle = client.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (isValidLeftClick(client, leftDown)) {

            locationClickConsumed = true;
            locationSelected(ToposcopeRenderer.getHoveredLocation());

        } else if (isValidRightClick(client, rightDown)) {

            rightClickConsumed = true;
            var location = ToposcopeRenderer.getHoveredLocation();
            var activeWaypoint = ToposcopeRenderer.getLocationActiveWaypoint(location);

            if (activeWaypoint.isPresent()) {
                ArdaMapsClient.CONFIG.removeWaypoint(activeWaypoint.get());
            } else {
                var pos = location.getPosition();
                ArdaMapsClient.CONFIG.setWaypoint(pos.x, pos.z, location.getWorld());
            }
        }

        leftMouseButtonWasDown = leftDown;
        rightMouseButtonWasDown = rightDown;
    }

    /**
     * Validates whether the left mouse button click should trigger a location selection.
     *
     * @param client   The Minecraft client instance.
     * @param leftDown {@code true} if the left mouse button is currently pressed.
     * @return {@code true} if the click is valid for selecting a location, {@code false} otherwise.
     */
    private boolean isValidLeftClick(MinecraftClient client, boolean leftDown) {

        return client.currentScreen == null &&
                leftDown && !leftMouseButtonWasDown &&
                overlayEnabled &&
                ToposcopeRenderer.getHoveredLocation() != null;
    }

    /**
     * Handles teleportation to the clicked landmark.
     *
     * @param hoveredLocation The landmark that was clicked.
     */
    private void locationSelected(@Nullable LocationClient hoveredLocation) {

        if (hoveredLocation != null && hoveredLocation.isVisited()) {

            if (hoveredLocation.getWarp() != null && !hoveredLocation.getWarp().isEmpty()) {

                var packet = new PlayerWarpPacket(hoveredLocation.getWarp());
                PacketRegistry.PLAYER_WARP_REQUEST.send(packet);

            } else {

                PlayerTeleportPacket packet = new PlayerTeleportPacket(hoveredLocation.getPosition().x,
                        hoveredLocation.getPosition().y,
                        hoveredLocation.getPosition().z,
                        Client.currentDimensionId());

                PacketRegistry.PLAYER_TELEPORT_REQUEST.send(packet);
            }
        }
    }

    /**
     * Validates whether the right mouse button click should trigger a waypoint set.
     *
     * @param client    The Minecraft client instance.
     * @param rightDown {@code true} if the right mouse button is currently pressed.
     * @return {@code true} if the click is valid for placing a waypoint over a location, {@code false} otherwise.
     */
    private boolean isValidRightClick(MinecraftClient client, boolean rightDown) {

        return client.currentScreen == null &&
                rightDown && !rightMouseButtonWasDown &&
                overlayEnabled &&
                ToposcopeRenderer.getHoveredLocation() != null;
    }

    /**
     * Checks if the player is currently hovering over a location marker.
     *
     * @return True if hovering over a location, false otherwise.
     */
    public boolean isHoveringLocation() {

        return ToposcopeRenderer.getHoveredLocation() != null;
    }
}