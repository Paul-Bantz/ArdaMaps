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
import com.duom.ardamaps.core.data.config.shared.Configuration;
import com.duom.ardamaps.core.data.json.ByteArrayTypeAdapter;
import com.duom.ardamaps.core.data.json.DimensionTypeAdapter;
import com.duom.ardamaps.core.data.json.Vec3dTypeAdapter;
import com.duom.ardamaps.core.data.location.BasicLocation;
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import com.duom.ardamaps.gui.ModConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract class for managing configuration files.
 *
 * @param <T> The type of the configuration object - Client or Server.
 */
public abstract class ConfigManager<T extends Configuration<L>, L extends BasicLocation> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    /** Gson instance for JSON serialization/deserialization */
    protected static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3d.class, new Vec3dTypeAdapter())
            .registerTypeAdapter(byte[].class, new ByteArrayTypeAdapter())
            .registerTypeAdapter(Dimension.class, new DimensionTypeAdapter())
            .setDateFormat(ModConstants.DATE_FORMAT)
            .setPrettyPrinting()
            .create();

    /** Location configuration file */
    protected final Path locationFile;

    /** Main configuration file */
    private final Path regionTextureLookupFile;

    /** Main configuration file */
    private final Path configFile;

    /** Underlying configuration object - client/server */
    @Getter
    protected T config;

    /**
     * Constructor for ConfigManager.
     *
     * @param configPath              The path to the configuration file.
     * @param locationConfigPath      The path to the location configuration file.
     * @param regionTextureLookupPath The path to the location configuration file.
     */
    public ConfigManager(String configPath, String locationConfigPath, String regionTextureLookupPath) {

        this.configFile = Path.of(configPath);
        this.locationFile = Path.of(locationConfigPath);
        this.regionTextureLookupFile = Path.of(regionTextureLookupPath);

        this.loadConfig();
        this.loadLocationsConfig();
        this.loadRegionTextureLookup();
    }

    /**
     * Reloads the configuration from file.
     */
    public void reload() {

        loadConfig();
        loadLocationsConfig();
        loadRegionTextureLookup();
    }

    /**
     * Load the config from file.
     */
    @SuppressWarnings("unchecked")
    private void loadConfig() {

        if (Files.exists(configFile)) {

            try (Reader reader = Files.newBufferedReader(configFile)) {

                T defaultConfig = createDefaultConfig();
                T loadedConfig = GSON.fromJson(reader, (Class<T>) defaultConfig.getClass());
                config = Objects.requireNonNullElse(loadedConfig, defaultConfig);

            } catch (IOException e) {

                LOGGER.error("Failed to load configuration", e);
            }
        } else {
            config = createDefaultConfig();
        }

        save();
    }

    /**
     * Load locations from file.
     */
    protected final void loadLocationsConfig() {

        if (Files.exists(locationFile)) {

            try (Reader reader = Files.newBufferedReader(locationFile)) {

                LocationConfig<L> defaultConfig = createDefaultLocationConfig();
                LocationConfig<L> loadedConfig = GSON.fromJson(reader, getLocationConfigType());
                config.setLocationConfig(Objects.requireNonNullElse(loadedConfig, defaultConfig));

            } catch (IOException e) {

                LOGGER.error("Failed to load configuration", e);
            }
        } else {
            config.setLocationConfig(createDefaultLocationConfig());
        }

        saveLocations();
    }

    /**
     * Load region texture lookup from file.
     */
    protected final void loadRegionTextureLookup() {

        RegionLookupTexture defaultConfig = RegionLookupTexture.DEFAULT;

        if (Files.exists(regionTextureLookupFile)) {

            try (Reader reader = Files.newBufferedReader(regionTextureLookupFile)) {

                RegionLookupTexture loadedConfig = GSON.fromJson(reader, RegionLookupTexture.class);
                config.setRegionLookupTexture(Objects.requireNonNullElse(loadedConfig, defaultConfig));

            } catch (IOException e) {

                LOGGER.error("Failed to load region texture lookup", e);
            }
        } else {
            config.setRegionLookupTexture(defaultConfig);
        }

        saveRegionTextureLookup();
    }

    /**
     * Creates the default configuration object.
     */
    protected abstract T createDefaultConfig();

    /**
     * Save the config to file.
     */
    public final void save() {

        save(configFile, config);
    }

    /**
     * Creates the default location configuration object.
     */
    protected abstract LocationConfig<L> createDefaultLocationConfig();

    /**
     * @return The location configuration type
     */
    protected abstract Type getLocationConfigType();

    /**
     * Saves the provided locations to the server config directory
     */
    public final void saveLocations() {

        save(locationFile, config.getLocationConfig());
    }

    /**
     * Backs up the location data on this server
     */
    public final void backupLocations(){

        Path backupFile = locationFile.resolveSibling(locationFile.getFileName() + ".back");
        save(backupFile, config.getLocationConfig());
    }

    /**
     * Saves the provided region texture lookup to the server config directory
     */
    public final void saveRegionTextureLookup() {

        save(regionTextureLookupFile, config.getRegionLookupTexture());
    }

    /**
     * Save the provided data to the specified file path.
     *
     * @param filePath The path to the file.
     * @param data     The data to save.
     */
    protected void save(Path filePath, Object data) {

        if (data == null || filePath == null) return;

        CompletableFuture.runAsync(() -> {

            try {
                if (!Files.exists(filePath.getParent())) {
                    Files.createDirectories(filePath.getParent());
                }

                try (Writer writer = Files.newBufferedWriter(filePath)) {
                    GSON.toJson(data, data.getClass(), writer);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to save configuration", e);
            }

        }, ArdaMaps.IO_EXECUTOR);
    }
}