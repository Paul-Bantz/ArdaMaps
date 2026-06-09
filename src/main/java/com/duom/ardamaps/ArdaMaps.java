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

package com.duom.ardamaps;

import com.cronutils.model.Cron;
import com.duom.ardamaps.api.ArdaMapsApi;
import com.duom.ardamaps.api.ArdaMapsApiEntrypoint;
import com.duom.ardamaps.api.ArdaMapsApiImpl;
import com.duom.ardamaps.core.commands.ServerCommands;
import com.duom.ardamaps.core.data.config.ServerConfigManager;
import com.duom.ardamaps.core.data.config.server.ServerConfig;
import com.duom.ardamaps.core.data.location.ExternalLocationSource;
import com.duom.ardamaps.core.items.ModItems;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.scheduling.CronScheduleHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The main class for the Arda Maps mod, responsible for initializing the mod, setting up configurations, registering commands and network packets, and handling server lifecycle events.
 * This class implements the ModInitializer interface from the Fabric API, which allows it to run initialization code when the mod is loaded.
 */
public class ArdaMaps implements ModInitializer {

    /** Executor for IO operations */
    public static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    /** Separate scheduler for periodic location refresh */
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    /** The mod identifier. */
    public static final String MOD_ID = "ardamaps";

    /** Logger instance for the mod. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdaMaps.class);

    /** Server configuration manager instance. */
    public static ServerConfigManager CONFIG_MANAGER;

    /** Server configuration instance. */
    public static ServerConfig CONFIG;

    /** The Minecraft server instance. */
    public static MinecraftServer SERVER;

    /**
     * The timestamp of the last successful location refresh, or {@code null} if no refresh has
     * occurred since the server started (the persisted timestamp from {@link com.duom.ardamaps.core.data.config.LocationConfig} is authoritative for pre-startup history).
     */
    public static volatile ZonedDateTime lastRefreshTime = null;

    /**
     * The timestamp of the next scheduled CRON-driven location refresh, or {@code null} if the
     * scheduler has not been initialized yet.
     */
    public static volatile ZonedDateTime nextScheduledRefresh = null;

    /**
     * The active CRON expression string being used for scheduling (after validation / fallback).
     */
    public static volatile String activeCronExpression = null;

    @Override
    public void onInitialize() {

        ArdaMaps.LOGGER.info("Initializing Arda Maps");

        ArdaMapsApiImpl.initialize();

        // Invoke all mods that registered an ardamaps:api entrypoint.
        // This runs after all mod initializers have completed, giving consumers a fully
        // ready API to register their LocationSource (or any other setup).
        invokeApiEntrypoints();

        // Config initialization only on the server side
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {

            CONFIG_MANAGER = new ServerConfigManager(
                    "./config/arda-maps/server.json",
                    "./config/arda-maps/server-locations.json",
                    "./config/arda-maps/region-texture-lookup.json"
            );

            CONFIG = CONFIG_MANAGER.getConfig();

            // Register server lifecycle events
            ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        }

        ModItems.register();
        PacketRegistry.init();
        ServerCommands.register();

        ServerLifecycleEvents.SERVER_STOPPING.register(this::onStop);
    }

    /**
     * Initialization logic run on server start
     * @param server the server instance
     */
    private void onServerStarted(MinecraftServer server) {

        SERVER = server;
        CONFIG_MANAGER.validateDimensionConfiguration(server);
        scheduleLocationRefresh();
    }

    /**
     * Queries Fabric for all mods that registered an {@code ardamaps:api} entrypoint and
     * calls {@link ArdaMapsApiEntrypoint#onApiReady(ArdaMapsApi)} on each of them.
     * Invoked once from {@link #onServerStarted(MinecraftServer)} so the API is fully
     * initialized before consumers receive the callback.
     */
    private void invokeApiEntrypoints() {

        ArdaMapsApi api = ArdaMapsApi.getInstance();

        for (EntrypointContainer<ArdaMapsApiEntrypoint> container :
                FabricLoader.getInstance().getEntrypointContainers(MOD_ID + ":api", ArdaMapsApiEntrypoint.class)) {

            String modId = container.getProvider().getMetadata().getId();
            try {
                LOGGER.info("[ArdaMaps] Invoking ardamaps:api entrypoint for mod '{}'", modId);
                container.getEntrypoint().onApiReady(api);
            } catch (Exception e) {
                LOGGER.error("[ArdaMaps] Exception in ardamaps:api entrypoint of mod '{}': {}", modId, e.getMessage(), e);
            }
        }
    }

    /**
     * Parses the configured CRON expression (falling back to the default on failure), determines
     * whether a startup refresh is needed, runs one if so, then arms the first self-rescheduling
     * one-shot trigger.
     */
    private void scheduleLocationRefresh() {

        // Parse & validate - falls back to default "0 3 */4 * *" with a WARN if needed
        Cron cron = CronScheduleHelper.parse(CONFIG.getRefreshCron());
        activeCronExpression = CONFIG.getRefreshCron() != null && !CONFIG.getRefreshCron().isBlank()
                ? CONFIG.getRefreshCron()
                : CronScheduleHelper.DEFAULT_CRON;

        ZonedDateTime now = ZonedDateTime.now();

        List<?> locations = CONFIG.getLocationConfig().getLocations();
        Date lastUpdateDate = CONFIG.getLocationConfig().getLastUpdate();

        boolean locationsDataEmpty = locations == null || locations.isEmpty();

        if (locationsDataEmpty)
            ArdaMaps.LOGGER.info("No locations found on startup - running initial location refresh");

        // Detect staleness: compare the most recent past CRON fire time with the persisted lastUpdate
        boolean stale = false;
        if (!locationsDataEmpty && lastUpdateDate != null) {

            Optional<ZonedDateTime> lastDue = CronScheduleHelper.lastExecution(cron, now);

            if (lastDue.isPresent()) {
                ZonedDateTime lastUpdate = lastUpdateDate.toInstant().atZone(now.getZone());
                stale = lastUpdate.isBefore(lastDue.get());
            }

        } else if (!locationsDataEmpty) {

            ArdaMaps.LOGGER.info("Location data is stale (last CRON trigger has passed without a refresh) - refreshing on startup");

            // lastUpdate is null - treat as stale
            stale = true;
        }

        if (locationsDataEmpty || stale) {

            ExternalLocationSource.fetchLocations().thenAccept(ServerCommands::saveLocationData)
                    .exceptionally(ex -> {
                        ArdaMaps.LOGGER.warn("Failed to fetch locations on startup: {}", ex.getMessage());
                        return null;
                    });
        }

        // Arm the first self-rescheduling trigger
        scheduleNextCronTrigger(cron);
    }

    /**
     * Computes the delay to the next CRON execution and arms a one-shot task that, after firing,
     * immediately re-arms itself for the following execution. This gives true CRON semantics
     * without requiring a Quartz dependency.
     *
     * @param cron   The validated CRON definition.
     */
    private static void scheduleNextCronTrigger(Cron cron) {

        ZonedDateTime now = ZonedDateTime.now();
        Optional<ZonedDateTime> next = CronScheduleHelper.nextExecution(cron, now);

        if (next.isEmpty()) {
            ArdaMaps.LOGGER.warn("[ArdaMaps] Could not compute next CRON execution time - scheduled refresh is disabled.");
            nextScheduledRefresh = null;
            return;
        }

        nextScheduledRefresh = next.get();
        long delayMs = nextScheduledRefresh.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();

        ArdaMaps.LOGGER.info("[ArdaMaps] Next scheduled location refresh: {} (in ~{} minutes)",
                nextScheduledRefresh, delayMs / 60_000);

        SCHEDULER.schedule(() -> {

            ArdaMaps.LOGGER.info("[ArdaMaps] CRON-triggered location refresh firing");
            ExternalLocationSource.fetchLocations().thenAccept(ServerCommands::saveLocationData)
                    .exceptionally(ex -> {
                        ArdaMaps.LOGGER.warn("Failed to fetch locations: {}", ex.getMessage());
                        return null;
                    });

            // Re-arm for the next execution
            scheduleNextCronTrigger(cron);

        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles server stopping event. Clean up executors to prevent resource leaks.
     *
     * @param minecraftServer The Minecraft server instance.
     */
    @SuppressWarnings("unused")
    private void onStop(MinecraftServer minecraftServer) {

        SERVER = null;

        shutdownExecutor(IO_EXECUTOR, "IO_EXECUTOR");
        shutdownExecutor(SCHEDULER, "SCHEDULER");
    }

    /**
     * Shuts down an executor service gracefully, with a timeout and forced shutdown if necessary.
     *
     * @param executor The executor service to shut down.
     * @param name     A name for the executor, used in logging.
     */
    private static void shutdownExecutor(ExecutorService executor, String name) {

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                ArdaMaps.LOGGER.warn("{} did not terminate in time, forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
