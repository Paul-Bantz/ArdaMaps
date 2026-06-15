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

package com.duom.ardamaps.core.data.config;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.UnitSystem;
import com.duom.ardamaps.core.data.config.client.ClientConfig;
import com.duom.ardamaps.core.data.config.client.ClientProgress;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Configuration manager for loading and saving client settings.
 */
public class ClientConfigManager extends ConfigManager<ClientConfig, LocationClient> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfigManager.class);

    /** Stores the client's exploration progress */
    private final Path clientProgressFile;

    /**
     * Constructor for ConfigManager.
     *
     * @param configPath              The path to the configuration file.
     * @param locationConfigPath      The path to the location configuration file.
     * @param regionTextureLookupPath The path to the region texture lookup file.
     * @param clientProgressPath      The path to the client progress file.
     */
    public ClientConfigManager(String configPath, String locationConfigPath, String regionTextureLookupPath, String clientProgressPath) {

        super(configPath, locationConfigPath, regionTextureLookupPath);

        clientProgressFile = Path.of(clientProgressPath);
        loadClientProgress();
    }

    /**
     * Load the client's exploration progress from file.
     */
    private void loadClientProgress() {

        var defaultProgress = createDefaultProgress();

        if (Files.exists(clientProgressFile)) {

            try (Reader reader = Files.newBufferedReader(clientProgressFile)) {

                ClientProgress progress = GSON.fromJson(reader, ClientProgress.class);
                config.setClientProgress(progress);
                synchronizeLocationExplorationProgress();

            } catch (JsonIOException | JsonSyntaxException | IOException e) {

                LOGGER.error("Failed to load exploration progress", e);
                backupClientProgress();
                config.setClientProgress(defaultProgress);

            }
        } else {

            config.setClientProgress(defaultProgress);
        }

        saveProgress();
    }

    /**
     * Synchronizes the exploration progress of incoming locations with the client's progress. This is called whenever
     * a location update is received with "raw" location data from the server.
     * Sets the progression fields in each location and persist locally.
     *
     * @param locations the locations to synchronize
     */
    public void synchronizeAndUpdateLocationExplorationProgress(LocationConfig<LocationClient> locations) {

        config.setLocationConfig(locations);
        synchronizeLocationExplorationProgress();
        saveProgress();
    }

    /**
     * Synchronizes the exploration progress of each location based on the loaded client progress.
     * Resyncs the exploration state of each location based on the {@link com.duom.ardamaps.core.data.PlayerExploration}
     * This avoids inconsistent states between the client's exploration progress and the individual location's exploration state.
     */
    public void synchronizeLocationExplorationProgress() {

        var clientProgress = config.getClientProgress();

        for (LocationClient location : config.getLocations()) {

            var worldId = location.getWorld();
            var explorationData = clientProgress.getExplorationState(worldId, false);

            boolean visited = clientProgress.getVisitedLocationIds().contains(location.getId());
            ExplorationState explored = ExplorationState.HIDDEN;

            if (explorationData != null) {

                var position = location.getPosition();
                explored = explorationData.stateAtWorldPos(position.getX(), position.getZ());
            }

            location.synchronizeProgress(explored, visited);
        }
    }

    /**
     * Backups the client progress to a *.bak file in case of loading failure, allowing users to recover their progress if the file was corrupted.
     */
    private void backupClientProgress() {

        if (Files.exists(clientProgressFile)) {

            try {

                Files.copy(clientProgressFile, clientProgressFile.resolveSibling(clientProgressFile.getFileName() + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            } catch (IOException e) {

                LOGGER.error("Failed to backup exploration progress", e);
            }
        }
    }

    /**
     * Creates the default client progress.
     *
     * @return A ClientProgress object with default settings.
     */
    private ClientProgress createDefaultProgress() {

        return new ClientProgress();
    }

    /**
     * Save the client's exploration progress to file.
     */
    public void saveProgress() {
        CompletableFuture.runAsync(() -> {

            try {
                if (!Files.exists(clientProgressFile.getParent())) {
                    Files.createDirectories(clientProgressFile.getParent());
                }

                try (Writer writer = Files.newBufferedWriter(clientProgressFile)) {
                    GSON.toJson(config.getClientProgress(), ClientProgress.class, writer);
                }

            } catch (IOException e) {
                LOGGER.error("Failed to save exploration progress file", e);
            }

        }, ArdaMaps.IO_EXECUTOR);
    }

    /**
     * Create the default client configuration.
     *
     * @return A ClientConfig object with default settings.
     */
    @Override
    protected ClientConfig createDefaultConfig() {

        ClientConfig config = new ClientConfig();
        config.setUnitSystem(UnitSystem.IMPERIAL);
        config.setToposcopeDrawDistance(31f);
        config.setCompassDrawDistance(31f);

        return config;
    }

    /**
     * Creates the default location configuration.
     *
     * @return A LocationConfig object with default settings.
     */
    @Override
    protected LocationConfig<LocationClient> createDefaultLocationConfig() {

        LocationConfig<LocationClient> defaultConfig = new LocationConfig<>();

        defaultConfig.setLastUpdate(new Date(0L));
        defaultConfig.setLocations(List.of());

        return defaultConfig;
    }

    /**
     * Gets the type of the location configuration.
     *
     * @return The Type of LocationConfig with LocationClient.
     */
    @Override
    protected Type getLocationConfigType() {
        return new TypeToken<LocationConfig<LocationClient>>() {
        }.getType();
    }
}