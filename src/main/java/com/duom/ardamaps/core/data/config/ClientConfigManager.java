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
import com.duom.ardamaps.core.data.config.client.ProgressWipe;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Configuration manager for loading and saving client settings.
 */
public class ClientConfigManager extends ConfigManager<ClientConfig, LocationClient> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfigManager.class);

    /** Timestamp pattern used in rotated progress backup file names. */
    private static final DateTimeFormatter PROGRESS_BACKUP_FORMAT = DateTimeFormatter.ofPattern("MMddyyHHmm");

    /** Maximum number of progress backups to retain. */
    private static final int MAX_PROGRESS_BACKUPS = 3;

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
        reloadClientProgress();
    }

    /**
     * Reloads the client's exploration progress from file.
     *
     * @return True when progress was loaded from an existing valid file.
     */
    public boolean reloadClientProgress() {

        var defaultProgress = createDefaultProgress();
        boolean loaded = false;

        if (Files.exists(clientProgressFile)) {

            try (Reader reader = Files.newBufferedReader(clientProgressFile)) {

                ClientProgress progress = Objects.requireNonNullElse(GSON.fromJson(reader, ClientProgress.class), defaultProgress);
                config.setClientProgress(progress);
                synchronizeLocationExplorationProgress();
                loaded = true;

            } catch (JsonIOException | JsonSyntaxException | IOException e) {

                LOGGER.error("Failed to load exploration progress", e);
                wipeClientProgress(ProgressWipe.CORRUPT_FILE);

            }
        } else {

            config.setClientProgress(defaultProgress);
            saveProgress();
        }

        return loaded;
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
                explored = explorationData.stateAtWorldPos(position.x(), position.z());
            }

            location.synchronizeProgress(explored, visited);
        }
    }

    /**
     * Save the client's exploration progress to file.
     *
     * @return A future completed when the write has finished.
     */
    public CompletableFuture<Void> saveProgress() {

        ClientProgress snapshot = config.getClientProgress().snapshot();
        final String json;
        try {
            json = GSON.toJson(snapshot, ClientProgress.class);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to serialize exploration progress file", e);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {

            try {
                Path parent = clientProgressFile.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                Path tempFile = clientProgressFile.resolveSibling(clientProgressFile.getFileName() + ".tmp");
                Files.writeString(tempFile, json);

                try {
                    Files.move(tempFile, clientProgressFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, clientProgressFile, StandardCopyOption.REPLACE_EXISTING);
                }

            } catch (RuntimeException | IOException e) {
                LOGGER.error("Failed to save exploration progress file", e);
            }

        }, ArdaMaps.IO_EXECUTOR);
    }

    /**
     * Saves the client's exploration progress and waits briefly for the write to complete.
     */
    public void saveProgressNow() {

        try {
            saveProgress().get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while saving exploration progress file", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.error("Failed to synchronously save exploration progress file", e);
        }
    }

    /**
     * Wipes the client's exploration progress for the provided reason.
     *
     * @param reason The reason for the wipe.
     */
    public void wipeClientProgress(ProgressWipe reason) {

        wipeClientProgress(reason, List.of());
    }

    /**
     * Wipes the client's exploration progress for the provided reason.
     *
     * @param reason     The reason for the wipe.
     * @param dimensions Dimensions used by ranged migration wipes.
     */
    public void wipeClientProgress(ProgressWipe reason, List<Dimension> dimensions) {

        if (reason == ProgressWipe.RANGED_MIGRATION && !config.getClientProgress().migrateRangedExploration(dimensions)) {
            return;
        }

        rotateProgressBackups();

        switch (reason) {
            case FULL_RESET -> config.getClientProgress().reset(false);
            case CORRUPT_FILE -> config.setClientProgress(createDefaultProgress());
            case RANGED_MIGRATION -> {
                // Migration was applied before backing up so stale entries are disposed once.
            }
        }

        saveProgress();
        LOGGER.info("Wiped exploration progress: {}", reason);
    }

    /**
     * Writes a timestamped progress backup and deletes backups beyond the retention count.
     */
    private void rotateProgressBackups() {

        if (!Files.exists(clientProgressFile)) return;

        try {
            Path backupFile = nextProgressBackupPath();
            Files.copy(clientProgressFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to backup exploration progress", e);
        }

        Path parent = Objects.requireNonNullElse(clientProgressFile.getParent(), Path.of("."));
        try (var backups = Files.list(parent)) {
            List<Path> backupFiles = backups
                    .filter(path -> path.getFileName().toString().startsWith(clientProgressFile.getFileName() + ".backup-"))
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .toList();

            for (int idx = MAX_PROGRESS_BACKUPS; idx < backupFiles.size(); idx++) {
                Files.deleteIfExists(backupFiles.get(idx));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to rotate exploration progress backups", e);
        }
    }

    /**
     * Finds the next available timestamped progress backup path.
     *
     * @return A backup path that will not collide with an existing file.
     */
    private Path nextProgressBackupPath() {

        String baseName = clientProgressFile.getFileName() + ".backup-" + PROGRESS_BACKUP_FORMAT.format(LocalDateTime.now());
        Path backupFile = clientProgressFile.resolveSibling(baseName);

        int suffix = 1;
        while (Files.exists(backupFile)) {
            backupFile = clientProgressFile.resolveSibling(baseName + "-" + suffix);
            suffix++;
        }

        return backupFile;
    }

    /**
     * Returns a file's last modified time in millis, treating unreadable files as oldest.
     *
     * @param path The path to inspect.
     * @return Last modified time in milliseconds, or {@link Long#MIN_VALUE}.
     */
    private long lastModified(Path path) {

        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            LOGGER.error("Failed to inspect exploration progress backup {}", path, e);
            return Long.MIN_VALUE;
        }
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
