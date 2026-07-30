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
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.networking.packets.client.PlayerTeleportResponsePacket;
import com.duom.ardamaps.core.networking.packets.server.PlayerRangedTeleportPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Handler for the PlayerRangedTeleportPacket, responsible for teleporting the player to the specified coordinates in a
 * given range.
 */
public class PlayerRangedTeleportHandler extends RespondablePacketHandler<PlayerRangedTeleportPacket, PlayerTeleportResponsePacket> {

    /** Logger instance for this handler to log warnings and debug information. */
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerRangedTeleportHandler.class);

    /** The network channel identifier that this handler listens for incoming PlayerRangedTeleportPacket messages. */
    private static final String REQ_CHANNEL = "player_ranged_teleport";

    /** The network channel identifier used to send PlayerTeleportResponsePacket messages back to clients. */
    private static final String RESP_CHANNEL = "player_ranged_teleport_response";

    /**
     * Constructs a new PlayerRangedTeleportHandler and registers it for the specified channel.
     */
    public PlayerRangedTeleportHandler() {
        super(REQ_CHANNEL, PlayerRangedTeleportPacket.TYPE, PlayerRangedTeleportPacket.CODEC,
                RESP_CHANNEL, PlayerTeleportResponsePacket.TYPE, PlayerTeleportResponsePacket.CODEC);
    }

    /**
     * Processes an incoming PlayerRangedTeleportPacket and attempts to teleport the player to a safe position.
     * <p>
     * Resolves the destination world and dimension configuration, scans for a valid teleport position within the
     * specified Y range, and either teleports the player or sends an error message.
     *
     * @param server  The Minecraft server instance.
     * @param player  The player to teleport.
     * @param handler The network handler for this player connection.
     * @param packet  The PlayerRangedTeleportPacket containing the target coordinates and Y scan range.
     * @param sender  The packet sender for responses.
     * @param responder Callback that sends the teleport response and must be called exactly once inside the server task.
     * @return Null because the response is sent asynchronously from the server thread.
     */
    @Override
    protected PlayerTeleportResponsePacket handle(MinecraftServer server, ServerPlayer player,
                                                  ServerGamePacketListenerImpl handler, PlayerRangedTeleportPacket packet,
                                                  PacketSender sender,
                                                  Consumer<PlayerTeleportResponsePacket> responder) {

        server.execute(() -> {

            // Resolve the destination world
            ServerLevel serverWorld = resolveWorld(server, packet.worldId());

            if (serverWorld == null) {
                LOGGER.warn("Unable to resolve ranged teleport world: {}", packet.worldId());
                responder.accept(PlayerTeleportResponsePacket.failed());
                return;
            }

            // Get the dimension configuration and calculate effective scan bounds
            Dimension dimension = resolveDimension(packet.worldId());
            VerticalBounds overallBounds = effectiveOverallBounds(dimension, serverWorld.getMinBuildHeight(), serverWorld.getMaxBuildHeight());
            double x = SafeTeleportScanner.blockCenter(packet.x());
            double z = SafeTeleportScanner.blockCenter(packet.z());

            // Find a safe Y position within the requested range
            OptionalDouble candidateY = findTeleportCandidate(packet.scanMinBoundY(), packet.scanMaxBoundY(), overallBounds,
                    y -> SafeTeleportScanner.standingHeightAt(serverWorld, player, x, y, z));

            if (candidateY.isPresent()) {
                double y = candidateY.getAsDouble();
                player.teleportTo(serverWorld, x, y, z, player.getYRot(), player.getXRot());
                responder.accept(new PlayerTeleportResponsePacket(true, x, y, z));
                return;
            }

            // Send error message if no safe position found
            player.displayClientMessage(Component.literal(String.format("Invalid teleport position at %s %s", (int)packet.x(), (int)packet.z()))
                    .withStyle(ChatFormatting.RED), false);
            responder.accept(PlayerTeleportResponsePacket.failed());
        });

        return null;
    }

    /**
     * Resolves a ServerWorld from the given world identifier.
     *
     * @param server  The Minecraft server instance.
     * @param worldId The world identifier string (e.g., "minecraft:overworld").
     * @return The matching ServerWorld, or null if not found or worldId is null.
     */
    private static ServerLevel resolveWorld(MinecraftServer server, String worldId) {

        if (worldId == null) return null;

        for (ServerLevel world : server.getAllLevels()) {
            if (world.dimension().location().toString().equals(worldId)) return world;
        }

        return null;
    }

    /**
     * Resolves a Dimension configuration from the given world identifier.
     *
     * @param worldId The world identifier string to match against configured dimensions.
     * @return The matching Dimension configuration, or null if not found or config is unavailable.
     */
    private static Dimension resolveDimension(String worldId) {

        if (ArdaMaps.CONFIG == null || ArdaMaps.CONFIG.getDimensions() == null) return null;

        return ArdaMaps.CONFIG.getDimensions().stream()
                .filter(dimension -> dimension.getId().equals(worldId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Calculates the effective overall scan bounds for teleport candidates.
     * <p>
     * Starts with world build-height bounds and optionally narrows to configured exploration ranges from the dimension.
     * Always ensures the bounds accommodate both the player's feet and head positions.
     *
     * @param dimension    Configured dimension with exploration ranges, or null if not configured.
     * @param worldBottomY Inclusive world lower build bound.
     * @param worldTopY    Exclusive world upper build bound.
     * @return Effective bounds for candidate feet positions, clamped to valid build height.
     */
    static VerticalBounds effectiveOverallBounds(Dimension dimension, int worldBottomY, int worldTopY) {

        // Start with world bounds
        int minY = worldBottomY;
        int maxY = worldTopY - 2; // -2 to ensure head block (1 above feet) is below worldTopY

        // Narrow to configured exploration ranges if present
        if (dimension != null) {
            List<MapLayerRange> ranges = dimension.getExplorationRanges();

            if (!ranges.isEmpty()) {
                // Find the minimum Y across all ranges (normalized for reversed min/max)
                minY = ranges.stream()
                        .mapToInt(range -> Math.min(range.rangeMinY(), range.rangeMaxY()))
                        .min()
                        .orElse(worldBottomY);
                // Find the maximum Y across all ranges (normalized for reversed min/max)
                maxY = ranges.stream()
                        .mapToInt(range -> Math.max(range.rangeMinY(), range.rangeMaxY()))
                        .max()
                        .orElse(worldTopY - 2);
            }
        }

        // Clamp to world bounds to ensure validity
        return new VerticalBounds(Math.max(minY, worldBottomY), Math.min(maxY, worldTopY - 2));
    }

    /**
     * Finds a safe teleport candidate Y position using a priority search strategy.
     * <p>
     * Searches in three phases with decreasing priority:
     * 1. Within the selected band (preferred location)
     * 2. Below the selected band (fallback)
     * 3. Above the selected band (last resort)
     *
     * @param selectedMinY  Selected lower Y bound, inclusive (may be reversed with selectedMaxY).
     * @param selectedMaxY  Selected upper Y bound, inclusive (may be reversed with selectedMinY).
     * @param overallBounds Effective bounds clamped to world height and configured ranges.
     * @param resolver      Resolver returning the exact standing Y for safe candidate positions.
     * @return The first safe standing Y candidate in priority order, or empty if none found.
     */
    static OptionalDouble findTeleportCandidate(double selectedMinY, double selectedMaxY, VerticalBounds overallBounds,
                                                IntFunction<OptionalDouble> resolver) {

        if (overallBounds.isEmpty()) return OptionalDouble.empty();

        // Normalize the selected range (handle reversed min/max)
        double normalizedSelectedMin = Math.min(selectedMinY, selectedMaxY);
        double normalizedSelectedMax = Math.max(selectedMinY, selectedMaxY);

        // Clamp the selected range to the overall bounds
        int selectedMin = Math.max((int) Math.ceil(normalizedSelectedMin), overallBounds.minY());
        int selectedMax = Math.min((int) Math.floor(normalizedSelectedMax), overallBounds.maxY());

        if (selectedMin > selectedMax) return OptionalDouble.empty();

        // Phase 1: Search within the selected band (upward from min)
        OptionalDouble inSelectedBand = scanUpward(selectedMin, selectedMax, resolver);
        if (inSelectedBand.isPresent()) return inSelectedBand;

        // Phase 2: Search below the selected band (downward from below min)
        OptionalDouble belowSelectedBand = scanDownward(overallBounds.minY(), selectedMin - 1, resolver);
        if (belowSelectedBand.isPresent()) return belowSelectedBand;

        // Phase 3: Search above the selected band (upward from above max)
        return scanUpward(selectedMax + 1, overallBounds.maxY(), resolver);
    }

    /**
     * Scans upward (low to high) from {@code minY} to {@code maxY}, inclusive, and returns the first resolved standing Y.
     *
     * @param minY     Lower inclusive candidate Y coordinate.
     * @param maxY     Upper inclusive candidate Y coordinate.
     * @param resolver  Resolver returning the exact standing Y for valid candidate positions.
     * @return The first valid standing Y value encountered, or empty if none found or the interval is invalid (minY > maxY).
     */
    static OptionalDouble scanUpward(int minY, int maxY, IntFunction<OptionalDouble> resolver) {

        if (minY > maxY) return OptionalDouble.empty();

        for (int y = minY; y <= maxY; y++) {
            OptionalDouble resolvedY = resolver.apply(y);
            if (resolvedY.isPresent()) return resolvedY;
        }

        return OptionalDouble.empty();
    }

    /**
     * Scans downward (high to low) from {@code maxY} to {@code minY}, inclusive, and returns the first resolved standing Y.
     *
     * @param minY     Lower inclusive candidate Y coordinate.
     * @param maxY     Upper inclusive candidate Y coordinate.
     * @param resolver  Resolver returning the exact standing Y for valid candidate positions.
     * @return The first valid standing Y value encountered, or empty if none found or the interval is invalid (minY > maxY).
     */
    static OptionalDouble scanDownward(int minY, int maxY, IntFunction<OptionalDouble> resolver) {

        if (minY > maxY) return OptionalDouble.empty();

        for (int y = maxY; y >= minY; y--) {
            OptionalDouble resolvedY = resolver.apply(y);
            if (resolvedY.isPresent()) return resolvedY;
        }

        return OptionalDouble.empty();
    }

    /**
     * Represents inclusive vertical bounds for valid player feet Y positions.
     *
     * @param minY Lower inclusive feet Y coordinate.
     * @param maxY Upper inclusive feet Y coordinate.
     */
    record VerticalBounds(
            int minY,
            int maxY
    ) {

        /**
         * Checks if these bounds are empty (invalid).
         *
         * @return True if minY > maxY (no valid range exists), false otherwise.
         */
        boolean isEmpty() {

            return minY > maxY;
        }
    }
}
