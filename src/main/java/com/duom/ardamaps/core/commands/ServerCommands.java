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
import com.duom.ardamaps.core.consumers.ArdaRegionsHook;
import com.duom.ardamaps.core.data.config.LocationConfig;
import com.duom.ardamaps.core.data.location.ExternalLocationSource;
import com.duom.ardamaps.core.data.location.LocationServer;
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
                (dispatcher, registryAccess, environment) ->
                        registerCommands(dispatcher)
        );
    }

    /**
     * Registers server commands for the mod.
     *
     * @param dispatcher The command dispatcher to register commands with.
     */
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal(ArdaMaps.MOD_ID)
                        .requires(source -> source.getServer() != null)
                        .then(CommandManager.literal("refresh")
                                .then(CommandManager.literal("configuration")
                                        .executes(ServerCommands::refreshConfiguration)))
                        .then(CommandManager.literal("refresh")
                                .then(CommandManager.literal("locations")
                                        .executes(ServerCommands::refreshLocationData)))
                        .then(CommandManager.literal("refresh")
                                .then(CommandManager.literal("regions")
                                        .executes(ServerCommands::refreshRegionLookupData)))
                        .then(CommandManager.literal("debug")
                                .executes(ServerCommands::debugRefreshSchedule)
                                .then(CommandManager.literal("regions")
                                        .executes(ServerCommands::debugRegionLookupData))));
    }

    /**
     * Refreshes the mod configuration by reloading it from disk.
     *
     * @param ignoredServerCommandSourceCommandContext The command context.
     * @return The result of the command execution.
     */
    private static int refreshConfiguration(CommandContext<ServerCommandSource> ignoredServerCommandSourceCommandContext) {

        LOGGER.info("Refreshing configuration");

        ArdaMaps.CONFIG_MANAGER.reload();
        ArdaMaps.CONFIG_MANAGER.validateDimensionConfiguration(ArdaMaps.SERVER);

        ArdaMaps.CONFIG = ArdaMaps.CONFIG_MANAGER.getConfig();

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Refreshes region lookup texture data.
     *
     * @param ignoredCommandSource The command context.
     * @return The result of the command execution.
     */
    private static int refreshRegionLookupData(CommandContext<ServerCommandSource> ignoredCommandSource) {

        LOGGER.info("Refreshing region lookup texture data");
        var count = 0;

        for (var entry : ArdaMaps.CONFIG.getDimensions()) {

            if (!entry.isSupportsArdaRegions())
                continue;

            LOGGER.info("Generating region lookup texture for dimension {}", entry.getId());

            ArdaRegionsHook.generateRegionLookup(entry.getId(), (RegionLookupTexture regionLookup) -> {

                if (regionLookup == null) {
                    LOGGER.info("No region data found for dimension {}", entry.getId());
                    return;
                }

                ArdaMaps.CONFIG.setRegionLookupTexture(regionLookup);
                ArdaMaps.CONFIG_MANAGER.saveRegionTextureLookup();
            });

            count++;
        }

        if (count == 0)
            LOGGER.warn("No dimension definitions with region data found in configuration, skipping region lookup texture generation");
        else
            LOGGER.info("Refreshing {} textures", count);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Saves the current region lookup texture data to a debug file.
     *
     * @param ignoredCommandSource The command context.
     * @return The result of the command execution.
     */
    private static int debugRegionLookupData(CommandContext<ServerCommandSource> ignoredCommandSource) {

        LOGGER.info("Dumping region lookup texture data to file");

        ArdaMaps.IO_EXECUTOR.submit(() -> {

            try {

                RegionLookupTexture texture = ArdaMaps.CONFIG.getRegionLookupTexture();
                texture.debugSaveToFile(new File("region_lookup_debug.png"));

            } catch (IOException e) {

                LOGGER.error("Failed to save region lookup texture debug image", e);
            }
        });

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
    private static int debugRefreshSchedule(CommandContext<ServerCommandSource> ignoredCtx) {

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
     * Refreshes location data from the registered LocationSource.
     *
     * @param ignoredCommandSource The command context.
     * @return The result of the command execution.
     */
    @SuppressWarnings("SameReturnValue")
    private static int refreshLocationData(CommandContext<ServerCommandSource> ignoredCommandSource) {

        ExternalLocationSource.fetchLocations().thenAccept(ServerCommands::saveLocationData)
                .exceptionally(ex -> {
                    LOGGER.warn("Failed to fetch locations: {}", ex.getMessage());
                    return null;
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

        // Update the in-memory last-refresh timestamp for the debug command
        ArdaMaps.lastRefreshTime = ZonedDateTime.now();
    }
}