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

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * Definition of a map layer in the configuration.
 * <br/>A layer is defined as :
 * <ul>
 *     <li>"layer": The name of the layer (e.g., "Map (Server)")</li>
 *     <li>"type": The source type of the layer (e.g., "pmtiles")</li>
 *     <li>"remote": Whether the layer is hosted remotely (true/false)</li>
 *     <li>"identity_zoom": The zoom level at which the layer is displayed at native resolution ie : 1 pixel = 1 block</li>
 *     <li>"lod_factor": Distance multiplier between LOD levels (Bluemap configuration only)</li>
 *     <li>"min_zoom": The minimum zoom level allowed</li>
 *     <li>"max_zoom": The maximum zoom level allowed</li>
 *     <li>"path": The path or URL to the layer data
 * </ul>
 * Example JSON:
 * <pre>
 *   {
 *       "layer": "Map (Server)",
 *       "type": "pmtiles",
 *       "remote": true,
 *       "identity_zoom": 8,
 *       "min_zoom": 2,
 *       "max_zoom": 16,
 *       "path": "<remote_host>/map.pmtiles"
 *   }
 * </pre>
 *
 * @param layer        The name of the layer (e.g., "Map (Server)")
 * @param type         The source type of the layer (e.g., "pmtiles")
 * @param remote       Whether the layer is hosted remotely (true/false)
 * @param identityZoom The zoom level at which the layer is displayed at native resolution ie : 1 pixel = 1 block
 * @param lodFactor    Bluemap lod factor
 * @param minZoom      The minimum zoom level allowed
 * @param maxZoom      The maximum zoom level allowed
 * @param tileSize     The tile size in pixels (default is 256)
 * @param scale        The scale factor to apply to the layer (default is 1.0)
 * @param path         The path or URL to the layer data
 * @param icon         The icon to use for the layer (optional)
 */
public record MapLayerDefinition(@SerializedName("layer") String layer, @SerializedName("type") MapLayerSource type,
                                 @SerializedName("remote") boolean remote,
                                 @SerializedName("identity_zoom") int identityZoom,
                                 @SerializedName("lod_factor") double lodFactor,
                                 @SerializedName("min_lod") int minLod, @SerializedName("max_lod") int maxLod,
                                 @SerializedName("min_zoom") int minZoom, @SerializedName("max_zoom") int maxZoom,
                                 @SerializedName("tile_size") int tileSize, @SerializedName("scale") double scale,
                                 @SerializedName("path") String path, @SerializedName("icon") String icon) {

    /** Special Layer definition for a simple grid. This is intended as a fallback when no other layers are defined */
    public static final MapLayerDefinition DEFAULT_GRID_LAYER = new MapLayerDefinition("Grid",
            MapLayerSource.GRID,
            false,
            8,
            1.0,
            1,3,
            1, 14,
            256,
            1.0,
            "",
            "");

    /**
     * @return The name of the layer (e.g., "Map (Server)").
     */
    @Override
    public String layer() {
        return layer;
    }

    /**
     * @return The path or URL to the layer data.
     */
    @Override
    public String path() {
        return path;
    }

    /**
     * @return Whether the layer is hosted remotely (true/false).
     */
    @Override
    public boolean remote() {
        return remote;
    }

    /**
     * @return The source type of the layer (e.g., "pmtiles").
     */
    @Override
    public MapLayerSource type() {
        return type;
    }

    /**
     * @return The zoom level at which the layer is displayed at native resolution ie : 1 pixel = 1 block.
     */
    @Override
    public int identityZoom() {
        return identityZoom;
    }

    /**
     * @return The maximum zoom level allowed.
     */
    @Override
    public int maxZoom() {
        return maxZoom;
    }

    /**
     * @return The minimum zoom level allowed.
     */
    @Override
    public int minZoom() {
        return minZoom;
    }

    /**
     * @return The scale factor to apply to the layer (default is 1.0).
     */
    @Override
    public double scale() {
        return scale;
    }

    /**
     * @return The icon to use for the layer (optional).
     */
    @Override
    public String icon() {
        return icon;
    }

    /**
     * @return The tile size in pixels (default is 256).
     */
    @Override
    public int tileSize() {
        return tileSize;
    }

    /**
     * @return The distance multiplier between LOD levels (Bluemap configuration only).
     */
    @Override
    public double lodFactor() {
        return lodFactor;
    }

    /**
     * Checks if this MapLayerDefinition is equal to another object.
     *
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {

        if (!(o instanceof MapLayerDefinition that)) return false;

        return remote == that.remote &&
                identityZoom == that.identityZoom &&
                Double.compare(lodFactor, that.lodFactor) == 0 &&
                minLod == that.minLod &&
                maxLod == that.maxLod &&
                minZoom == that.minZoom &&
                maxZoom == that.maxZoom &&
                Double.compare(scale, that.scale) == 0 &&
                Objects.equals(layer, that.layer) &&
                type == that.type &&
                Objects.equals(path, that.path) &&
                Objects.equals(icon, that.icon);
    }

    /**
     * Computes the hash code for this MapLayerDefinition.
     *
     * @return The hash code of this MapLayerDefinition.
     */
    @Override
    public int hashCode() {

        return Objects.hash(layer, type, remote, lodFactor, minLod, maxLod, identityZoom, minZoom, maxZoom, scale, path, icon);
    }
}