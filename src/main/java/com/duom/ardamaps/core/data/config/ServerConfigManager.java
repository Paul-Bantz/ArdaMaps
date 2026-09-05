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
     * @param regionTextureLookupPath The path to the region texture lookup file.
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
     * @param worlds The loaded world descriptors.
     */
    public void validateDimensionConfiguration(Iterable<ServerWorldDefinition> worlds) {

        validateRangeConfiguration();

        if (!this.config.isAutoGenerateMissingDimensions()) return;

        /*
         For each defined dimension on this server check if we have a matching configured dimension.
         If not generate a default one.
         */
        for (var world : worlds) {

            var dimensionId = world.dimensionId();

            if (this.config.getDimensions().stream().noneMatch(d -> d.getId().equals(dimensionId))) {

                LOGGER.warn("Dimension {} is not defined in the configuration - generating default definition", dimensionId);

                Dimension defaultDimension = new Dimension(world.dimensionName(), dimensionId,
                        1,
                        (int) world.boundWest(),
                        (int) world.boundEast(),
                        (int) world.boundNorth(),
                        (int) world.boundSouth(),
                        false,
                        true);

                defaultDimension.getMapLayers().add(MapLayerDefinition.DEFAULT_GRID_LAYER);

                this.config.getDimensions().add(defaultDimension);
            }
        }
    }

    /**
     * Minimal world data needed to generate missing dimension config.
     */
    public record ServerWorldDefinition(String dimensionId,
                                        String dimensionName,
                                        double boundWest,
                                        double boundEast,
                                        double boundNorth,
                                        double boundSouth) {
    }

    /**
     * Logs warnings for range configuration shapes that are valid but likely surprising.
     */
    private void validateRangeConfiguration() {

        for (Dimension dimension : this.config.getDimensions()) {
            dimension.getMapLayers().stream()
                    .filter(layer -> !layer.hasRanges())
                    .filter(layer -> layer.type() != MapLayerSource.GRID)
                    .filter(layer -> layer.path() == null || layer.path().isBlank())
                    .forEach(layer -> LOGGER.warn(
                            "Dimension {} layer '{}' has no path and no ranges. Layer path is only optional when ranges are defined.",
                            dimension.getId(),
                            layer.layer()));

            dimension.getMapLayers().stream()
                    .filter(MapLayerDefinition::hasRanges)
                    .flatMap(layer -> layer.ranges().stream())
                    .filter(range -> range.path() == null || range.path().isBlank())
                    .forEach(range -> LOGGER.warn(
                            "Dimension {} range {} has no path. Ranged layers require a path on every range.",
                            dimension.getId(),
                            range.index()));

            if (!dimension.hasRanges()) continue;

            List<MapLayerRange> canonicalRanges = dimension.getExplorationRanges();
            boolean hasFlatLayer = dimension.getMapLayers().stream().anyMatch(layer -> !layer.hasRanges());

            if (hasFlatLayer) {
                LOGGER.warn("Dimension {} mixes ranged and non-ranged map layers. Non-ranged layers will use the canonical range set for exploration.", dimension.getId());
            }

            dimension.getMapLayers().stream()
                    .filter(MapLayerDefinition::hasRanges)
                    .skip(1)
                    .filter(layer -> !sameRanges(canonicalRanges, layer.ranges()))
                    .forEach(layer -> LOGGER.warn(
                            "Dimension {} layer '{}' declares ranges that differ from the canonical first ranged layer. Exploration uses the first ranged layer only.",
                            dimension.getId(),
                            layer.layer()));
        }
    }

    /**
     * Compares range indices and Y bounds in order.
     *
     * @param expected The canonical range list.
     * @param actual   The range list to compare against the canonical list.
     * @return True when both lists have the same indices and Y bounds in the same order.
     */
    private boolean sameRanges(List<MapLayerRange> expected, List<MapLayerRange> actual) {

        if (expected == null || actual == null) return expected == actual;
        if (expected.size() != actual.size()) return false;

        for (int i = 0; i < expected.size(); i++) {
            MapLayerRange left = expected.get(i);
            MapLayerRange right = actual.get(i);

            if (left.index() != right.index()
                    || left.rangeMinY() != right.rangeMinY()
                    || left.rangeMaxY() != right.rangeMaxY()) {
                return false;
            }
        }

        return true;
    }
}
