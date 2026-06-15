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

package com.duom.ardamaps.gui.map.rendering;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.map.cameras.PmTilesMapCamera;
import com.duom.ardamaps.core.data.map.providers.PMTilesFileTileProvider;
import com.duom.ardamaps.core.data.map.providers.PMTilesHttpTileProvider;
import com.duom.ardamaps.core.data.map.providers.TileProvider;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import com.duom.ardamaps.gui.ModConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * A map viewer that renders map tiles from PMTiles files.
 */
public class PmTilesRenderer extends MapRenderable {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PmTilesRenderer.class);
    
    /** The camera used to determine which tiles are visible and how they should be rendered based on the current view. */
    private final PmTilesMapCamera mapCamera;

    /** Tile provider to load tiles from */
    private TileProvider<PmTileKey> tileProvider;

    /**
     * Constructs a new PmTilesRenderer.
     * The camera must already have {@link PmTilesMapCamera#setDimension} called before this constructor
     * (guaranteed by MapScreen which builds the camera first).
     *
     * @param camera       The pre-built, dimension-aware camera for this renderer.
     * @param textRenderer The text renderer for displaying loading text or other information.
     */
    public PmTilesRenderer(PmTilesMapCamera camera, TextRenderer textRenderer) {

        super(camera, textRenderer);
        this.mapCamera = camera;
    }

    /**
     * Sets the displayed map layer.
     * Note: {@code setDimension} is intentionally absent - the camera already has its dimension set by MapScreen.
     *
     * @param layer       the map layer definition
     * @param renderScale the preferred render scale to display the map
     */
    @Override
    public void configure(MapLayerDefinition layer, double renderScale) {

        try {

            if (layer.remote())
                tileProvider = PMTilesHttpTileProvider.init(layer.path());
            else
                tileProvider = PMTilesFileTileProvider.init(layer.path());

            mapCamera.setTilesZoomBounds(tileProvider.getMinZoom(), tileProvider.getMaxZoom());
            mapCamera.setCameraZoomBounds(layer.minZoom(), layer.maxZoom());
            mapCamera.setIdentityZoom(layer.identityZoom());
            mapCamera.setScale(layer.scale());
            mapCamera.setTileSize(layer.tileSize());
            mapCamera.setPreferredRenderScale(renderScale);
            mapCamera.setZoomToMatchVisualPixelsPerBlock();

            // Eagerly preload minimum zoom tiles so fallbacks are available as quickly as possible
            mapCamera.getVisibleTiles(tileProvider.getMinZoom()).forEach(key -> tileProvider.eagerLoadTile(key));

        } catch (IOException e) {

            LOGGER.error("Failed to load PMTiles layer: {}", layer.layer());
        }
    }

    /**
     * Renders the visible map tiles and fog of war overlay.
     * <p>
     * If the tile provider is not yet initialized, displays a loading message instead.
     *
     * @param context the draw context
     */
    @Override
    public void render(DrawContext context) {

        if (tileProvider == null) {
            super.renderLoadingText(context);
            return;
        }

        /* Rendering */

        renderMap(context);
        renderFogOfWar();
    }

    /**
     * Renders the visible map tiles in a single pass.
     * For each tile at the current zoom level, if it is not yet loaded, its coarse fallback tile
     *
     * @param context the draw context
     */
    private void renderMap(DrawContext context) {

        int minZoom = tileProvider.getMinZoom();

        // Preload min-zoom tiles so fallbacks are always available
        Set<PmTileKey> minZoomTiles = mapCamera.getVisibleTiles(minZoom);
        minZoomTiles.forEach(key -> tileProvider.get(key));

        Set<PmTileKey> tilesToDisplay = mapCamera.getVisibleTiles();

        // Trigger load for current-zoom tiles
        tilesToDisplay.forEach(key -> tileProvider.get(key));

        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        boolean debugMode = ArdaMapsClient.CONFIG.isMapDebugDisplay();

        for (PmTileKey key : tilesToDisplay) {

            Optional<Identifier> tex = tileProvider.get(key);

            PmTileKey renderKey = key;

            if (tex.isEmpty()) {

                var fallbackTile = findFallbackTile(key, minZoom);
                renderKey = fallbackTile.getLeft();
                tex = fallbackTile.getRight();

                if (tex.isEmpty()) continue; // fallback not loaded yet either
            }

            int renderSize = getDisplayedTileSize(renderKey.z);
            var screenPos = mapCamera.tilePositionOnViewport(renderKey.x, renderKey.y, renderKey.z);
            int roundedX = (int) Math.round(screenPos.x());
            int roundedY = (int) Math.round(screenPos.y());

            Identifier currentTexture = tex.get();

            var matrices = context.getMatrices();
            matrices.push();
            double offsetX = screenPos.x() - roundedX;
            double offsetY = screenPos.y() - roundedY;
            matrices.translate(offsetX, offsetY, 0);

            context.drawTexture(
                    currentTexture,
                    roundedX, roundedY,
                    0, 0,
                    renderSize, renderSize,
                    renderSize, renderSize
            );
            matrices.pop();

            if (debugMode) drawDebugLines(context, renderKey, roundedX, roundedY, renderSize);
        }
    }

    /**
     * Finds the fallback tile for the given tile key by traversing up the zoom levels until a loaded tile is found or the minimum zoom level is reached.
     *
     * @param key     the original tile key
     * @param minZoom the minimum zoom level to stop at
     * @return the key of the fallback tile if found, or null if no fallback tile is available
     */
    private Pair<PmTileKey, Optional<Identifier>> findFallbackTile(PmTileKey key, int minZoom) {

        PmTileKey current = key;

        do {
            current = new PmTileKey(
                    current.z - 1,
                    current.x >> 1,
                    current.y >> 1
            );

            Optional<Identifier> tex = tileProvider.get(current);
            if (tex.isPresent()) return new Pair<>(current, tex);

        } while (current.z >= minZoom);

        return new Pair<>(key, Optional.empty());
    }

    /**
     * Gets the displayed tile size for the given zoom level, using caching to avoid redundant calculations.
     *
     * @return the displayed tile size
     */
    private int getDisplayedTileSize(int tileZoom) {

        return mapCamera.displayedTileSize(tileZoom);
    }

    /**
     * Draw tile separation lines and tile info
     *
     * @param context    the draw context
     * @param key        the tile key
     * @param screenX    screen X position of the tile
     * @param screenY    screen Y position of the tile
     * @param renderSize size of the rendered tile
     */
    private void drawDebugLines(DrawContext context, PmTileKey key, int screenX, int screenY, int renderSize) {

        // Draw red outline
        context.drawBorder(screenX, screenY, renderSize, renderSize, ModConstants.COLOR_RED);

        // Draw tile ID text
        String tileId = String.format("Z:%d X:%d Y:%d", key.z, key.x, key.y);
        context.drawText(
                textRenderer,
                tileId,
                screenX + 5,
                screenY + 5,
                ModConstants.COLOR_WHITE,
                true
        );
    }
}