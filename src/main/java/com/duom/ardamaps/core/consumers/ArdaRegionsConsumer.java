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

package com.duom.ardamaps.core.consumers;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.map.Region;
import com.duom.ardamaps.core.data.map.RegionLookupBuilder;
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import com.duom.ardamaps.core.integration.RegionProvider;
import com.duom.ardamaps.core.integration.Regions;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.client.PlayerExplorationPacket;
import mc.ardacraft.ardaregions.api.ArdaRegionsAPI;
import mc.ardacraft.ardaregions.api.ArdaRegionsApiEntrypoint;
import mc.ardacraft.ardaregions.api.data.ApiRegion;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Consumer for the Arda Regions API that listens for server discovery events.
 */
public class ArdaRegionsConsumer implements ArdaRegionsApiEntrypoint, RegionProvider {

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdaRegionsConsumer.class);

    /** ArdaRegions API instance, set when the API is ready. */
    private ArdaRegionsAPI api;

    /**
     * Initializes this consumer when the ArdaRegions API becomes ready on the server.
     *
     * @param api the ArdaRegions API instance
     */
    @Override
    public void onApiReady(ArdaRegionsAPI api) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) return;

        LOGGER.info("Arda Regions API is ready, registering consumer.");
        this.api = api;
        Regions.register(this);
        registerServerListeners();
    }

    /**
     * Registers event listeners for region discovery events from the ArdaRegions API.
     */
    private void registerServerListeners() {
        api.getRegionDiscoveredEvent().register((player, region) -> {
            Optional<ApiRegion> apiRegion = api.getRegionAPI().getRegion(region);

            if (apiRegion.isEmpty()) return;

            ServerPlayer playerEntity = ArdaMaps.SERVER.getPlayerList().getPlayer(player);
            if (playerEntity == null) return;

            ApiRegion resolvedRegion = apiRegion.get();
            ApiRegion resolvedParentRegion = getParentRegion(player, resolvedRegion);

            PacketRegistry.PLAYER_EXPLORATION_EVENT.send(playerEntity, buildPlayerExplorationPacket(resolvedParentRegion, resolvedRegion));
        });
    }

    /**
     * Resolves the parent region for a given region if all siblings have been discovered.
     *
     * @param player         the player who discovered the region
     * @param resolvedRegion the region that was discovered
     * @return the parent region if all siblings are discovered, null otherwise
     */
    private ApiRegion getParentRegion(UUID player, ApiRegion resolvedRegion) {
        ApiRegion resolvedParentRegion = null;
        boolean shouldReturnParentRegion = true;

        if (resolvedRegion.getParentId() != null) {
            Optional<ApiRegion> parentRegion = api.getRegionAPI().getRegion(resolvedRegion.getParentId());

            if (parentRegion.isPresent()) {
                resolvedParentRegion = parentRegion.get();

                for (var childId : resolvedParentRegion.getChildrenIds()) {
                    if (resolvedRegion.getId().equals(childId)) continue;

                    if (api.getExplorationAPI().hasDiscovered(player, childId)) {
                        shouldReturnParentRegion = false;
                        break;
                    }
                }
            }
        }

        return shouldReturnParentRegion ? resolvedParentRegion : null;
    }

    /**
     * Builds a player exploration packet from region data for transmission to the client.
     *
     * @param parentRegion the parent region (may be null)
     * @param subRegion    the discovered sub-region
     * @return a new player exploration packet
     */
    private @NonNull PlayerExplorationPacket buildPlayerExplorationPacket(ApiRegion parentRegion, ApiRegion subRegion) {
        var regionPolygons = transformRegionPolygons(subRegion);
        var parentRegionPolygons = transformRegionPolygons(parentRegion);

        return new PlayerExplorationPacket(
                "minecraft:overworld",
                subRegion.getId(),
                parentRegionPolygons,
                regionPolygons
        );
    }

    /**
     * Transforms ArdaRegions polygon data to internal Vec2d format.
     *
     * @param region the region to transform polygons from (may be null)
     * @return a list of polygons as Vec2d coordinate lists
     */
    private @NonNull List<List<Vec2d>> transformRegionPolygons(ApiRegion region) {
        if (region == null) return List.of();

        var transformedPolygons = new ArrayList<List<Vec2d>>();

        for (var polygon : region.getPolygons()) {
            var vertices = new ArrayList<Vec2d>();

            for (var vertex : polygon.getVertices()) {
                vertices.add(new Vec2d(vertex.getX(), vertex.getZ()));
            }

            transformedPolygons.add(vertices);
        }

        return transformedPolygons;
    }

    /**
     * Generates a region lookup texture for the given dimension asynchronously.
     *
     * @param dimensionId the dimension identifier
     * @param callback    callback receiving the generated texture, or null if no regions exist
     */
    @Override
    public void generateRegionLookup(String dimensionId, Consumer<RegionLookupTexture> callback) {
        if (api == null || api.getRegionAPI() == null) {
            LOGGER.warn("Arda Regions API is not ready, skipping region lookup generation.");
            return;
        }

        var dimension = ArdaMaps.CONFIG.getDimensions().stream()
                .filter(dimensionDefinition -> dimensionDefinition.getId().equals(dimensionId))
                .findFirst();

        if (dimension.isEmpty()) {
            LOGGER.warn("No dimension found for '{}', skipping region lookup generation.", dimensionId);
            return;
        }

        ArdaMaps.IO_EXECUTOR.submit(() -> generateLut(dimension.get(), callback));
    }

    /**
     * Builds a region lookup texture from the given dimension's regions.
     *
     * @param dimension the dimension to generate lookup for
     * @param callback  callback receiving the generated texture
     */
    private void generateLut(Dimension dimension, Consumer<RegionLookupTexture> callback) {
        var regionsForDimension = api.getRegionAPI().getRegionsByWorld(dimension.getId());

        if (regionsForDimension == null || regionsForDimension.isEmpty()) {
            callback.accept(null);
            return;
        }

        List<Region> regionList = new ArrayList<>();
        List<List<List<Vec2d>>> regionPolygons = new ArrayList<>();
        Map<String, Integer> regionIdToIndex = new LinkedHashMap<>();

        for (ApiRegion apiRegion : regionsForDimension) {
            if (apiRegion.getParentId() != null) continue;

            regionIdToIndex.put(apiRegion.getId(), regionList.size());
            regionList.add(new Region(apiRegion.getId(), apiRegion.getName()));
            regionPolygons.add(new ArrayList<>());
        }

        for (ApiRegion apiRegion : api.getRegionAPI().getAllRegions()) {
            if (apiRegion.getParentId() != null) continue;

            Integer index = regionIdToIndex.get(apiRegion.getId());
            if (index == null) continue;

            regionPolygons.get(index).addAll(transformRegionPolygons(apiRegion));
        }

        if (regionList.isEmpty()) {
            callback.accept(null);
            return;
        }

        RegionLookupTexture texture = RegionLookupBuilder.build(dimension, regionList, regionPolygons);
        callback.accept(new RegionLookupTexture(texture.pixels(), texture.regions(), texture.texWidth(),
                texture.texHeight(), texture.dimensionId(), new Date()));
    }
}
