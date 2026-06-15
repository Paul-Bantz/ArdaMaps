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

import com.duom.ardamaps.core.data.config.server.ServerConfig;
import com.duom.ardamaps.core.data.location.LocationServer;
import com.duom.ardamaps.core.scheduling.CronScheduleHelper;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Configuration manager for loading and saving server settings.
 */
public class ServerConfigManager extends ConfigManager<ServerConfig, LocationServer> {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfigManager.class);

    /**
     * Constructor for ConfigManager.
     *
     * @param configPath              The path to the configuration file.
     * @param locationConfigPath      The path to the location configuration file.
      *@param regionTextureLookupPath The path to the region texture lookup file.
     */
    public ServerConfigManager(String configPath, String locationConfigPath, String regionTextureLookupPath) {

        super(configPath, locationConfigPath, regionTextureLookupPath);
    }

    /**
    * Creates the default server configuration.
    *
    * @return A ServerConfig object with default settings.
    */
    @Override
    protected ServerConfig createDefaultConfig() {

        ServerConfig serverConfig = new ServerConfig();

        var dimensions = new ArrayList<Dimension>();

        serverConfig.setDimensions(dimensions);
        serverConfig.setRefreshCron(CronScheduleHelper.DEFAULT_CRON);

        return serverConfig;
    }

    /**
     * Creates the default location configuration.
     *
     * @return A LocationConfig object with default settings.
     */
    @Override
    protected LocationConfig<LocationServer> createDefaultLocationConfig() {

        LocationConfig<LocationServer> defaultConfig = new LocationConfig<>();

        defaultConfig.setLastUpdate(new Date(0L));
        defaultConfig.setLocations(List.of());

        return defaultConfig;
    }

    /**
     * Gets the type of the location configuration.
     *
     * @return The Type of LocationConfig with LocationServer.
     */
    @Override
    protected Type getLocationConfigType() {
        return new TypeToken<LocationConfig<LocationServer>>() {
        }.getType();
    }

    /**
     * Checks the dimension configuration on server startup and generate default if non-existent.
     *
     * @param server The Minecraft server instance.
     */
    public void validateDimensionConfiguration(MinecraftServer server) {

        if (!this.config.isAutoGenerateMissingDimensions()) return;

        var worlds = server.getWorlds();

        /*
         For each defined dimension on this server check if we have a matching configured dimension.
         If not generate a default one.
         */
        for (var world : worlds) {

            var dimensionId = world.getRegistryKey().getValue();

            if (this.config.getDimensions().stream().noneMatch(d -> d.getId().equals(dimensionId.toString()))) {

                LOGGER.warn("Dimension {} is not defined in the configuration - generating default definition", dimensionId);

                var border = world.getWorldBorder();
                Dimension defaultDimension = new Dimension(dimensionId.getPath(), dimensionId.toString(),
                        1,
                        (int)border.getBoundWest(),
                        (int)border.getBoundEast(),
                        (int)border.getBoundNorth(),
                        (int)border.getBoundSouth(),
                        false,
                        true);

                MapLayerDefinition defaultLayer = new MapLayerDefinition(dimensionId.getPath(),
                        MapLayerSource.GRID,
                        false,
                        8,
                        1,
                        1,
                        3,
                        14,
                        1,
                        256,
                        1,
                        "", "");

                defaultDimension.getMapLayers().add(defaultLayer);

                this.config.getDimensions().add(defaultDimension);
            }
        }
    }
}