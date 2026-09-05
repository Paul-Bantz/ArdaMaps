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

package com.duom.ardamaps.core.commands;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.location.ExternalLocationSource;
import com.duom.ardamaps.core.data.location.LocationServer;
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.data.map.region.RegionGeometryPruner;
import com.duom.ardamaps.core.integration.Regions;
import com.duom.ardamaps.core.networking.packets.client.RegionsGeometryResponsePacket;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class responsible for registering server commands for ArdaMaps.
 */
public class ServerCommands {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerCommands.class);

    /** Date / time format */
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    /**
     * Registers the server commands for the mod.
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, _, _) ->
                        registerCommands(dispatcher)
        );
    }

    /**
     * Registers server commands for the mod.
     *
     * @param dispatcher The command dispatcher to register commands with.
     */
    @SuppressWarnings("ConstantValue")
    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal(ArdaMaps.MOD_ID)
                        .requires(source -> source.getServer() != null)
                        .then(Commands.literal("refresh")
                                .then(Commands.literal("configuration")
                                        .executes(ServerCommands::refreshConfiguration)))
                        .then(Commands.literal("refresh")
                                .then(Commands.literal("locations")
                                        .executes(ServerCommands::refreshLocationData)))
                        .then(Commands.literal("refresh")
                                .then(Commands.literal("regions")
                                        .executes(ServerCommands::refreshRegionLookupData)))
                        .then(Commands.literal("debug")
                                .executes(ServerCommands::debugRefreshSchedule)
                                .then(Commands.literal("regions")
                                        .executes(ServerCommands::debugRegionLookupData))));
    }

    /**
     * Refreshes the mod configuration by reloading it from disk.
     *
     * @param ignoredServerCommandSourceCommandContext The command context.
     * @return The result of the command execution.
     */
    private static int refreshConfiguration(CommandContext<CommandSourceStack> ignoredServerCommandSourceCommandContext) {

        LOGGER.info("Refreshing configuration");

        ArdaMaps.CONFIG_MANAGER.reload();
        ArdaMaps.CONFIG_MANAGER.validateDimensionConfiguration(ArdaMaps.serverWorldDefinitions(ArdaMaps.SERVER));

        ArdaMaps.CONFIG = ArdaMaps.CONFIG_MANAGER.getConfig();

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Refreshes location data from the registered LocationSource.
     *
     * @param ignoredCommandSource The command context.
     * @return The result of the command execution.
     */
    @SuppressWarnings("SameReturnValue")
    private static int refreshLocationData(CommandContext<CommandSourceStack> ignoredCommandSource) {

        ExternalLocationSource.fetchLocations().thenAccept(ServerCommands::saveLocationData)
                .exceptionally(ex -> {
                    LOGGER.warn("Failed to fetch locations: {}", ex.getMessage());
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Refreshes region geometry data.
     *
     * @param ignoredCommandSource The command context.
     * @return The result of the command execution.
     */
    private static int refreshRegionLookupData(CommandContext<CommandSourceStack> ignoredCommandSource) {

        LOGGER.info("Refreshing region geometry data");

        if (!Regions.isAvailable()) {
            LOGGER.warn("Region provider unavailable, skipping region geometry generation");
            return Command.SINGLE_SUCCESS;
        }

        var count = 0;
        var locations = ArdaMaps.CONFIG.getLocationConfig() == null
                ? List.<LocationServer>of()
                : ArdaMaps.CONFIG.getLocationConfig().getLocations();
        ArdaMaps.CONFIG.clearRegionGeometry();
        ArdaMaps.CONFIG_MANAGER.saveRegionGeometry();

        for (var entry : ArdaMaps.CONFIG.getDimensions()) {

            if (!entry.isSupportsArdaRegions())
                continue;

            LOGGER.info("Generating region geometry for dimension {}", entry.getId());

            Regions.generateRegionGeometry(entry.getId(), (RegionGeometry regionGeometry) -> {

                if (regionGeometry == null) {
                    LOGGER.info("No region data found for dimension {}", entry.getId());
                    return;
                }

                RegionGeometry pruned = RegionGeometryPruner.prune(regionGeometry, locations);
                ArdaMaps.CONFIG.setRegionGeometry(pruned);
                ArdaMaps.CONFIG_MANAGER.saveRegionGeometry();
            });

            count++;
        }

        if (count == 0)
            LOGGER.warn("No dimension definitions with region data found in configuration, skipping region geometry generation");
        else
            LOGGER.info("Refreshing geometry for {} dimension(s)", count);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Logs the current refresh schedule status (active CRON expression, last refresh time, and
     * next planned refresh time) to the server log. Output is intentionally server-log-only -
     * nothing is sent back to the command source.
     *
     * @param ignoredCtx The command context.
     * @return {@link Command#SINGLE_SUCCESS}.
     */
    private static int debugRefreshSchedule(CommandContext<CommandSourceStack> ignoredCtx) {

        // Last refresh: prefer the in-memory ZDT set after each run; fall back to the
        // persisted Date inside LocationConfig for pre-startup history.
        String lastRefreshStr;

        if (ArdaMaps.lastRefreshTime != null) {

            lastRefreshStr = ArdaMaps.lastRefreshTime.format(DATETIME_FMT);

        } else {

            Date persisted = ArdaMaps.CONFIG.getLocationConfig().getLastUpdate();
            lastRefreshStr = (persisted != null)
                    ? persisted + " (persisted, pre-startup)"
                    : "never";
        }

        String nextRefreshStr = (ArdaMaps.nextScheduledRefresh != null)
                ? ArdaMaps.nextScheduledRefresh.format(DATETIME_FMT)
                : "not scheduled";

        String cronStr = (ArdaMaps.activeCronExpression != null)
                ? ArdaMaps.activeCronExpression
                : "(unknown)";

        LOGGER.info("-- {} Debug --", ArdaMaps.MOD_ID);
        LOGGER.info("- Active CRON   : {}", cronStr);
        LOGGER.info("- Last refresh  : {}", lastRefreshStr);
        LOGGER.info("- Next refresh  : {}", nextRefreshStr);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Logs the current region geometry statistics.
     *
     * @param ignoredCommandSource The command context.
     * @return The result of the command execution.
     */
    private static int debugRegionLookupData(CommandContext<CommandSourceStack> ignoredCommandSource) {

        LOGGER.info("Dumping region geometry statistics");

        ArdaMaps.IO_EXECUTOR.submit(() -> {
            for (var entry : ArdaMaps.CONFIG.getRegionGeometryByDimension().entrySet()) {
                RegionGeometry geometry = entry.getValue();
                int rings = 0;
                int vertices = 0;

                for (var shape : geometry.regions()) {
                    rings += shape.rings().length;
                    for (int[] ring : shape.rings()) {
                        vertices += ring.length / 2;
                    }
                }

                int packetBytes = new RegionsGeometryResponsePacket(List.of(geometry), List.of(entry.getKey())).build().readableBytes();
                LOGGER.info("- {}: {} shapes, {} rings, {} vertices, {} encoded packet bytes, last update {}",
                        entry.getKey(), geometry.regions().length, rings, vertices, packetBytes, geometry.lastUpdate());
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Saves the fetched location list to config and persists it.
     * Shared between the command handler and the scheduled/startup refresh.
     *
     * @param locations The list of fetched {@link LocationServer} objects.
     */
    public static void saveLocationData(List<LocationServer> locations) {

        if (locations == null || locations.isEmpty()) {

            LOGGER.warn("No location data found from REST API. Not persisting empty data.");
            return;
        }

        if (ArdaMaps.CONFIG.getLocationConfig() != null &&
                ArdaMaps.CONFIG.getLocationConfig().getLocations() != null &&
                !ArdaMaps.CONFIG.getLocationConfig().getLocations().isEmpty()) {

            LOGGER.info("Backing up previous location data");
            ArdaMaps.CONFIG_MANAGER.backupLocations();
        }

        var updateDate = new Date();

        LOGGER.info("Persisting location data with {} entries (updated at {})", locations.size(), updateDate);

        LocationConfig<LocationServer> config = new LocationConfig<>();
        config.setLocations(locations);
        config.setLastUpdate(updateDate);

        ArdaMaps.CONFIG.setLocationConfig(config);
        ArdaMaps.CONFIG_MANAGER.saveLocations();
        repruneRegionGeometry(locations);

        // Update the in-memory last-refresh timestamp for the debug command
        ArdaMaps.lastRefreshTime = ZonedDateTime.now();
    }

    /**
     * Re-prunes stored region geometry after location data changes.
     *
     * @param locations The refreshed locations.
     */
    private static void repruneRegionGeometry(List<LocationServer> locations) {

        if (ArdaMaps.CONFIG.getRegionGeometryByDimension() == null
                || ArdaMaps.CONFIG.getRegionGeometryByDimension().isEmpty()) {
            return;
        }

        List<RegionGeometry> changed = new ArrayList<>();

        for (RegionGeometry geometry : ArdaMaps.CONFIG.getRegionGeometryByDimension().values()) {
            RegionGeometry pruned = RegionGeometryPruner.prune(geometry, locations);
            if (pruned != geometry)
                changed.add(new RegionGeometry(pruned.dimensionId(), pruned.regions(), new Date()));
        }

        if (changed.isEmpty()) return;

        for (RegionGeometry geometry : changed)
            ArdaMaps.CONFIG.setRegionGeometry(geometry);

        ArdaMaps.CONFIG_MANAGER.saveRegionGeometry();
    }
}
