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

package com.duom.ardamaps.gui.screens.map;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.map.cameras.BlueMapCamera;
import com.duom.ardamaps.core.data.map.cameras.FlatMapCamera;
import com.duom.ardamaps.core.data.map.cameras.GridCamera;
import com.duom.ardamaps.core.data.map.cameras.PmTilesMapCamera;
import com.duom.ardamaps.gui.map.rendering.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;

/**
 * Builds map renderables for selected map layers.
 */
@Environment(EnvType.CLIENT)
public class MapLayerLoader {

    /**
     * Load the map renderable for the selected map layer definition.
     *
     * @param input The layer-construction inputs
     * @return The loaded MapRenderable, or null if loading failed
     */
    public @Nullable MapRenderable load(Input input) {

        var explorationRange = resolveExplorationRange(input.selectedDimension(), input.selectedRange(), input.playerY());
        var progress = ArdaMapsClient.CONFIG.getClientProgress();
        PlayerExploration layerExploration = progress.getExplorationState(
                input.selectedDimension() == null ? null : input.selectedDimension().getId(),
                explorationRange == null ? null : explorationRange.index(),
                true);

        if (layerExploration == null) return null;

        MapLayerDefinition resolvedLayer = resolveLayer(input.mapLayerDefinition(), input.selectedRange(), input.playerY());
        MapRenderable mapRenderable = null;

        switch (input.mapLayerDefinition().type()) {

            case BLUEMAP -> {
                var camera = new BlueMapCamera(input.viewportWidth(), input.viewportHeight(), input.startingCameraX(), input.startingCameraZ());
                camera.setDimension(input.selectedDimension());
                mapRenderable = new BlueMapRenderer(camera, input.textRenderer(), layerExploration);
                mapRenderable.configure(resolvedLayer, input.capturedRenderScale());
            }
            case PMTILES -> {
                var camera = new PmTilesMapCamera(input.viewportWidth(), input.viewportHeight(), input.startingCameraX(), input.startingCameraZ());
                camera.setDimension(input.selectedDimension());
                mapRenderable = new PmTilesRenderer(camera, input.textRenderer(), layerExploration);
                mapRenderable.configure(resolvedLayer, input.capturedRenderScale());
            }
            case WEBP -> {
                var camera = new FlatMapCamera(input.viewportWidth(), input.viewportHeight(), input.startingCameraX(), input.startingCameraZ());
                camera.setDimension(input.selectedDimension());
                mapRenderable = new WebpRenderer(camera, input.textRenderer(), layerExploration);
                mapRenderable.configure(resolvedLayer, input.capturedRenderScale());
            }
            case GRID -> {
                var camera = new GridCamera(input.viewportWidth(), input.viewportHeight(), input.startingCameraX(), input.startingCameraZ());
                camera.setDimension(input.selectedDimension());
                mapRenderable = new GridRenderer(camera, input.textRenderer(), layerExploration);
                mapRenderable.configure(resolvedLayer, input.capturedRenderScale());
            }
        }

        return mapRenderable;
    }

    /**
     * Resolves the exploration range for the layer being loaded.
     *
     * @param selectedDimension The currently selected dimension, or null when unavailable.
     * @param selectedRange     The explicit range selected in the UI, or null to infer one.
     * @param playerY           The player's current Y coordinate, or null to fall back to the first configured range.
     * @return The resolved exploration range, or null when the dimension has no ranges.
     */
    static @Nullable MapLayerRange resolveExplorationRange(@Nullable Dimension selectedDimension,
                                                           @Nullable MapLayerRange selectedRange,
                                                           @Nullable Double playerY) {

        if (selectedRange != null) return selectedRange;

        if (selectedDimension == null || !selectedDimension.hasRanges()) return null;

        return playerY == null
                ? selectedDimension.getExplorationRanges().getFirst()
                : selectedDimension.rangeForY(playerY);
    }

    /**
     * Rebuilds the selected layer definition with the effective path chosen for the resolved vertical range.
     *
     * @param mapLayerDefinition The selected base layer definition.
     * @param selectedRange      The explicit range selected in the UI, or null to derive from player Y.
     * @param playerY            The player's current Y coordinate used for ranged path selection when needed.
     * @return A layer definition whose path matches the active vertical slice.
     */
    static MapLayerDefinition resolveLayer(MapLayerDefinition mapLayerDefinition,
                                           @Nullable MapLayerRange selectedRange,
                                           @Nullable Double playerY) {

        return new MapLayerDefinition(
                mapLayerDefinition.layer(),
                mapLayerDefinition.type(),
                mapLayerDefinition.remote(),
                mapLayerDefinition.identityZoom(),
                mapLayerDefinition.preferredZoom(),
                mapLayerDefinition.lodFactor(),
                mapLayerDefinition.minLod(),
                mapLayerDefinition.maxLod(),
                mapLayerDefinition.minZoom(),
                mapLayerDefinition.maxZoom(),
                mapLayerDefinition.tileSize(),
                mapLayerDefinition.scale(),
                selectedRange != null ? selectedRange.path() : mapLayerDefinition.effectivePath(playerY),
                mapLayerDefinition.effectiveIcon(playerY),
                mapLayerDefinition.ranges());
    }

    /**
     * All inputs required to construct a map renderable without reaching back into the screen.
     *
     * @param selectedDimension   The dimension selected in the UI, or null when none is available yet.
     * @param selectedRange       The explicit vertical range selected by the user, or null when the layer is unranged.
     * @param mapLayerDefinition  The selected layer definition to instantiate.
     * @param capturedRenderScale The previously visible pixels-per-block to preserve across layer switches.
     * @param viewportWidth       The screen-space viewport width used to size the new camera.
     * @param viewportHeight      The screen-space viewport height used to size the new camera.
     * @param startingCameraX     The initial world X coordinate for the new camera.
     * @param startingCameraZ     The initial world Z coordinate for the new camera.
     * @param playerY             The player's current Y coordinate, used for range resolution when no explicit range is selected.
     * @param textRenderer        The text renderer passed to the constructed map renderable.
     */
    public record Input(
            @Nullable Dimension selectedDimension,
            @Nullable MapLayerRange selectedRange,
            MapLayerDefinition mapLayerDefinition,
            double capturedRenderScale,
            int viewportWidth,
            int viewportHeight,
            int startingCameraX,
            int startingCameraZ,
            @Nullable Double playerY,
            Font textRenderer) {
    }
}
