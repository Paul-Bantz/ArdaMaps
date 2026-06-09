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
import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.client.PlayerExplorationPacket;
import mc.ardacraft.ardaregions.api.ArdaRegionsAPI;
import mc.ardacraft.ardaregions.api.ArdaRegionsApiEntrypoint;
import mc.ardacraft.ardaregions.api.data.ApiRegion;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Consumer for the Arda Regions API that listens for client discovery popup events
 * and updates the ArdaRegionsState accordingly.
 */
public class ArdaRegionsHook implements ArdaRegionsApiEntrypoint {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdaRegionsHook.class);

    /** Maximum size (in pixels) of either dimension of the region lookup texture. */
    private static final int REGION_LOOKUP_MAX_DIMENSION = 512;

    /** Singleton instance of the ArdaRegionsAPI, set when the API is ready. */
    private static ArdaRegionsAPI API_INSTANCE;

    /**
     * Gets the singleton instance of the ArdaRegionsAPI.
     *
     * @return The ArdaRegionsAPI instance.
     */
    @SuppressWarnings("unused")
    public static ArdaRegionsAPI getApiInstance() {
        return API_INSTANCE;
    }

    /**
     * Generates a region lookup texture as a flat ARGB {@code int[]} array.
     * <p>
     * The texture has at most {@value #REGION_LOOKUP_MAX_DIMENSION} pixels on its
     * longest side while preserving the {@link Dimension} aspect ratio.
     * Each pixel stores the ARGB colour of the root region whose polygon covers
     * that world position. Pixels not covered by any region are {@code 0xFF000000}
     * (opaque black). When regionPolygon overlap, the <em>last-written</em> region wins.
     * </p>
     * <p><b>Only {@code "minecraft:overworld"} is supported.</b> If {@code dimensionId} is anything
     * else this method returns immediately without invoking the callback.</p>
     *
     * @param dimensionId The dimension for which to generate the LUT.
     *                    Only {@code "minecraft:overworld"} is currently supported.
     * @param callback    A callback to receive the generated {@link RegionLookupTexture}.
     */
    public static void generateRegionLookup(String dimensionId, Consumer<RegionLookupTexture> callback) {

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
     * Internal method to generate the region lookup texture for a given dimension definition.
     *
     * @param dimension   The dimension definition containing world bounds and other info.
     * @param callback    A callback to receive the generated {@link RegionLookupTexture}.
     */
    private static void generateLut(Dimension dimension, Consumer<RegionLookupTexture> callback) {

        // Compute texture dimensions preserving aspect ratio
        double worldW = dimension.getWidth();
        double worldH = dimension.getHeight();

        // Width is always greater than height in Ardacraft
        double scale = REGION_LOOKUP_MAX_DIMENSION / worldW;

        int texW = Math.max(1, (int) Math.round(worldW * scale));
        int texH = Math.max(1, (int) Math.round(worldH * scale));

        double scaleX = (double) texW / worldW;   // pixels per world-X unit
        double scaleZ = (double) texH / worldH;   // pixels per world-Z unit

        // Collect root regions and assign a 1-based byte index (0 = no region)
        List<Region> regionList = new ArrayList<>();
        Map<String, Byte> regionIdToIndex = new LinkedHashMap<>();

        byte nextIndex = 1;
        var regionsForDimension = API_INSTANCE.getRegionAPI().getRegionsByWorld(dimension.getId());

        // Exit early if no regions for this dimension - callback no texture
        if (regionsForDimension == null || regionsForDimension.isEmpty()) {

            callback.accept(null);

        } else {

            // Index only root regions (those without a parent) since sub-regions are not rendered separately on the map
            for (ApiRegion apiRegion : regionsForDimension) {

                if (apiRegion.getParentId() != null) continue;

                regionList.add(new Region(apiRegion.getId(), apiRegion.getName()));
                regionIdToIndex.put(apiRegion.getId(), nextIndex++);
            }

            Region[] regions = regionList.toArray(new Region[0]);

            // Allocate pixel buffer (0 = no region, JVM zero-initialises byte[])
            byte[] pixels = new byte[texW * texH];

            // Rasterize each root region polygon
            for (ApiRegion apiRegion : API_INSTANCE.getRegionAPI().getAllRegions()) {

                if (apiRegion.getParentId() != null) continue;

                Byte index = regionIdToIndex.get(apiRegion.getId());
                if (index == null) continue;

                for (var polygon : apiRegion.getPolygons()) {

                    List<Vec2d> vertices = new ArrayList<>();
                    for (var vertex : polygon.getVertices()) {
                        double px = (vertex.getX() - dimension.getXMin()) * scaleX;
                        double pz = (vertex.getZ() - dimension.getZMin()) * scaleZ;
                        vertices.add(new Vec2d(px, pz));
                    }

                    rasterizePolygon(pixels, texW, texH, vertices, index);
                }
            }

            callback.accept(new RegionLookupTexture(pixels, regions, texW, texH, dimension.getId(), new Date()));
        }
    }

    /**
     * Rasterize a single polygon into {@code pixels} using a Scanline fill.
     * Coordinates in {@code vertices} are already in texture-pixel space.
     * Last-written wins: any previous pixel values are silently overwritten.
     *
     * @param pixels   Target pixel buffer (ARGB, row-major, row = Z axis).
     * @param texW     Width of the texture in pixels.
     * @param texH     Height of the texture in pixels.
     * @param vertices Polygon vertices in pixel space (x = texture X, y = texture Z/row).
     * @param color    Opaque ARGB colour to fill the polygon with.
     */
    private static void rasterizePolygon(byte[] pixels, int texW, int texH,
                                         List<Vec2d> vertices, byte color) {

        if (vertices.size() < 3) return;

        // Determine scanline Y range (clamped to texture bounds)
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Vec2d v : vertices) {
            if (v.y() < minY) minY = v.y();
            if (v.y() > maxY) maxY = v.y();
        }

        int yStart = Math.max(0, (int) Math.ceil(minY));
        int yEnd = Math.min(texH - 1, (int) Math.floor(maxY));

        int n = vertices.size();

        for (int y = yStart; y <= yEnd; y++) {

            List<Double> intersections = getIntersectionsWithScanline(vertices, y, n);

            // Sort intersections and fill between pairs
            intersections.sort(Double::compareTo);

            for (int i = 0; i + 1 < intersections.size(); i += 2) {
                int xStart = Math.max(0, (int) Math.ceil(intersections.get(i)));
                int xEnd = Math.min(texW - 1, (int) Math.floor(intersections.get(i + 1)));
                for (int x = xStart; x <= xEnd; x++) {
                    pixels[y * texW + x] = color;
                }
            }
        }
    }

    /**
     * Computes the X coordinates of the intersections between a polygon and a horizontal scanline at Y.
     *
     * @param vertices Polygon vertices in pixel space (x = texture X, y = texture Z/row).
     * @param y        Y coordinate of the scanline.
     * @param n        Number of vertices in the polygon.
     * @return A list of X coordinates where the scanline intersects the polygon edges.
     */
    private static @NonNull List<Double> getIntersectionsWithScanline(List<Vec2d> vertices, int y, int n) {
        double scanY = y + 0.5; // sample at pixel centre

        // Collect all X intersections of polygon edges with the scanline
        List<Double> intersections = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Vec2d a = vertices.get(i);
            Vec2d b = vertices.get((i + 1) % n);

            double ay = a.y(), by = b.y();
            if (ay == by) continue; // horizontal edge – skip

            // Check if scanline crosses this edge
            if ((scanY < Math.min(ay, by)) || (scanY >= Math.max(ay, by))) continue;

            double t = (scanY - ay) / (by - ay);
            intersections.add(a.x() + t * (b.x() - a.x()));
        }
        return intersections;
    }

    /**
     * Called when the Arda Regions API is ready.
     * Registers a listener for client discovery popup events to update the displaying state.
     *
     * @param api the Arda Regions API instance
     */
    @Override
    public void onApiReady(ArdaRegionsAPI api) {

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) return;
        LOGGER.info("Arda Regions API is ready, registering consumer.");

        API_INSTANCE = api;

        registerServerListeners();
    }

    /**
     * Registers server-side event listeners for Arda Regions API events. Server-side only.
     * Upon discovery of a region by a player, retrieves the relevant <b>parent</b> region data and sends a PlayerExplorationPacket
     * to the client to update the exploration status for the discovered region along with its parent.
     * If siblings of the discovered region exist and have already been discovered by the player, the parent region will
     * not be included in the packet since it is already revealed on the client.
     */
    private void registerServerListeners() {

        ArdaRegionsHook.API_INSTANCE.getRegionDiscoveredEvent().register((player, region) -> {

            Optional<ApiRegion> apiRegion = ArdaRegionsHook.API_INSTANCE.getRegionAPI().getRegion(region);

            // No region found - silently ignore
            if (apiRegion.isEmpty()) return;

            ServerPlayerEntity playerEntity = ArdaMaps.SERVER.getPlayerManager().getPlayer(player);

            // Player not found (shouldn't happen) - silently ignore
            if (playerEntity == null) return;

            ApiRegion resolvedRegion = apiRegion.get();
            ApiRegion resolvedParentRegion = getParentRegion(player, resolvedRegion);

            PacketRegistry.PLAYER_EXPLORATION_EVENT.send(playerEntity, buildPlayerExplorationPacket(resolvedParentRegion, resolvedRegion));
        });
    }

    /**
     * Retrieves the parent region of the given resolved region if the player has not yet discovered any of its siblings.
     *
     * @param player         The UUID of the player for whom to check discovery status.
     * @param resolvedRegion The region that was just discovered and for which we want to find the parent region.
     * @return The parent ApiRegion if the player has not discovered any sibling regions, or null if there is no parent or if any sibling is already discovered.
     */
    private static ApiRegion getParentRegion(UUID player, ApiRegion resolvedRegion) {

        ApiRegion resolvedParentRegion = null;
        boolean shouldReturnParentRegion = true;

        if (resolvedRegion.getParentId() != null) {

            Optional<ApiRegion> parentRegion = ArdaRegionsHook.API_INSTANCE.getRegionAPI().getRegion(resolvedRegion.getParentId());

            if (parentRegion.isPresent()) {

                resolvedParentRegion = parentRegion.get();

                for (var childId : resolvedParentRegion.getChildrenIds()) {

                    if (resolvedRegion.getId().equals(childId)) continue; // skip the currently discovered region

                    if (ArdaRegionsHook.API_INSTANCE.getExplorationAPI().hasDiscovered(player, childId)) {

                        shouldReturnParentRegion = false;
                        break;
                    }
                }
            }
        }
        return shouldReturnParentRegion ? resolvedParentRegion : null;
    }

    /**
     * Builds a PlayerExplorationPacket for the given discovered region and its parentRegion region.
     * If the player has not yet discovered the parentRegion region, the packet will contain both the discovered region polygons
     * and the parentRegion region polygon.
     *
     * @param parentRegion the parentRegion region
     * @param subRegion    the discovered region
     * @return A PlayerExplorationPacket containing information about the discovered region and its parentRegion.
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
     * Transforms the polygons of an ApiRegion into a list of polygons represented as lists of Vec2d vertices.
     *
     * @param region the ApiRegion whose polygons to transform
     * @return A list of polygons, where each polygon is a list of Vec2d vertices in world coordinates (x = world X, y = world Z).
     */
    private @NonNull List<List<Vec2d>> transformRegionPolygons(ApiRegion region) {

        if (region == null) return List.of();

        var rawPolygons = region.getPolygons();

        var transformedPolygons = new ArrayList<List<Vec2d>>();

        for (var polygon : rawPolygons) {

            var vertices = new ArrayList<Vec2d>();

            for (var vertex : polygon.getVertices()) {
                vertices.add(new Vec2d(vertex.getX(), vertex.getZ()));
            }

            transformedPolygons.add(vertices);
        }

        return transformedPolygons;
    }
}

