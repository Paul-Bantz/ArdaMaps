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
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.map.cameras.PmTilesMapCamera;
import com.duom.ardamaps.core.data.map.providers.PMTilesFileTileProvider;
import com.duom.ardamaps.core.data.map.providers.PMTilesHttpTileProvider;
import com.duom.ardamaps.core.data.map.providers.TileProvider;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
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
     * @param exploration  The fog-of-war exploration state to render for this map layer.
     */
    public PmTilesRenderer(PmTilesMapCamera camera, Font textRenderer, PlayerExploration exploration) {

        super(camera, textRenderer, exploration);
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

            TileProvider<PmTileKey> newProvider = layer.remote()
                    ? PMTilesHttpTileProvider.init(layer.path())
                    : PMTilesFileTileProvider.init(layer.path());

            if (tileProvider != null) tileProvider.close();
            tileProvider = newProvider;

            mapCamera.setTilesZoomBounds(tileProvider.getMinZoom(), tileProvider.getMaxZoom());
            mapCamera.setCameraZoomBounds(layer.minZoom(), layer.maxZoom());
            mapCamera.setIdentityZoom(layer.identityZoom());
            mapCamera.setPreferredZoom(layer.preferredZoom());
            mapCamera.updateZoom();
            mapCamera.setScale(layer.scale());
            mapCamera.setTileSize(layer.tileSize());
            mapCamera.setPreferredRenderScale(renderScale);
            mapCamera.setZoomToMatchVisualPixelsPerBlock();

            // Pin the coarsest zoom outside the LRU so it can never be evicted by request churn at
            // other zoom levels. The full-map pyramid itself is now loaded incrementally through the
            // per-frame bounded/prioritised pipeline in renderMap(), at the lowest urgency tier,
            // rather than being force-loaded here - that let a large coarse pyramid flood the
            // executor queue ahead of whatever the player actually pans to first.
            tileProvider.setPinnedZoom(tileProvider.getMinZoom());

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
    public void render(GuiGraphicsExtractor context) {

        if (tileProvider == null) {
            super.renderLoadingText(context);
            return;
        }

        /* Rendering */

        renderMap(context);
        renderFogOfWar(context);
    }

    /**
     * Priority offset for coarse-zoom tiles within the current viewport - the immediate visual
     * fallback for a primary tile that hasn't loaded yet. Ranked behind primary-zoom tiles (whose
     * priority is just their centre-tile ring, 0-based), so a viewport miss still resolves quickly
     * without displacing what's actually on screen.
     */
    private static final int VIEWPORT_FALLBACK_PRIORITY_BASE = 10_000;

    /**
     * Renders the visible map tiles in a single pass.
     * For each tile at the current zoom level, if it is not yet loaded, its coarse fallback tile
     * <p>
     * Tile loading is bounded and prioritised via {@link TileProvider#beginFrame()} /
     * {@link TileProvider#request} / {@link TileProvider#endFrame()} across two tiers, most urgent
     * first: primary-zoom tiles actually in the viewport (ranked by distance from the viewport
     * centre), and coarse-zoom tiles in the viewport backing the immediate visual fallback. Loading
     * is scoped strictly to what's currently visible - there is no proactive background preload for
     * parts of the map the player hasn't panned to. Primary-zoom requests are only registered once
     * the camera has settled
     * ({@link com.duom.ardamaps.core.data.map.cameras.MapCamera#isSettled()}); during a fast pan or
     * zoom only the viewport's coarse fallback is requested.
     * </p>
     *
     * @param context the draw context
     */
    private void renderMap(GuiGraphicsExtractor context) {

        int minZoom = tileProvider.getMinZoom();
        boolean settled = mapCamera.isSettled();

        tileProvider.beginFrame();

        // Tier 1: coarse-zoom tiles within the current viewport - the immediate visual fallback.
        for (PmTileKey key : mapCamera.getVisibleTiles(minZoom)) {
            tileProvider.request(key, VIEWPORT_FALLBACK_PRIORITY_BASE + mapCamera.centerTileDistance(key.x, key.y, minZoom));
        }

        Set<PmTileKey> tilesToDisplay = mapCamera.getVisibleTiles();

        boolean debugMode = ArdaMapsClient.CONFIG.isMapDebugDisplay();
        for (PmTileKey key : tilesToDisplay) {

            // Tier 0: primary-zoom tiles actually in the viewport - the most urgent tier.
            if (settled) {
                int ring = mapCamera.centerTileDistance(key.x, key.y, key.z);
                tileProvider.request(key, ring);
            }

            Optional<Identifier> tex = tileProvider.peek(key);

            PmTileKey renderKey = key;

            if (tex.isEmpty()) {

                var fallbackTile = findFallbackTile(key, minZoom);
                renderKey = fallbackTile.getA();
                tex = fallbackTile.getB();

                if (tex.isEmpty()) continue; // fallback not loaded yet either
            }

            int renderSize = getDisplayedTileSize(renderKey.z);
            var screenPos = mapCamera.tilePositionOnViewport(renderKey.x, renderKey.y, renderKey.z);
            int roundedX = (int) Math.round(screenPos.x());
            int roundedY = (int) Math.round(screenPos.y());

            Identifier currentTexture = tex.get();

            var matrices = context.pose();
            matrices.pushMatrix();
            float offsetX = (float) (screenPos.x() - roundedX);
            float offsetY = (float) (screenPos.y() - roundedY);
            matrices.translate(offsetX, offsetY);

            context.blit(
                    RenderPipelines.GUI_TEXTURED,
                    currentTexture,
                    roundedX, roundedY,
                    0, 0,
                    renderSize, renderSize,
                    renderSize, renderSize
            );
            matrices.popMatrix();

            if (debugMode) drawDebugLines(context, renderKey, roundedX, roundedY, renderSize);
        }

        tileProvider.endFrame();
    }

    /**
     * Finds the fallback tile for the given tile key by traversing up the zoom levels until a loaded tile is found or the minimum zoom level is reached.
     *
     * @param key     the original tile key
     * @param minZoom the minimum zoom level to stop at
     * @return the key of the fallback tile if found, or null if no fallback tile is available
     */
    private Tuple<PmTileKey, Optional<Identifier>> findFallbackTile(PmTileKey key, int minZoom) {

        PmTileKey current = key;

        while (current.z > minZoom) {
            current = new PmTileKey(
                    current.z - 1,
                    current.x >> 1,
                    current.y >> 1
            );

            Optional<Identifier> tex = tileProvider.peek(current);
            if (tex.isPresent()) return new Tuple<>(current, tex);

        }

        return new Tuple<>(key, Optional.empty());
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
    private void drawDebugLines(GuiGraphicsExtractor context, PmTileKey key, int screenX, int screenY, int renderSize) {

        // Draw red outline
        context.outline(screenX, screenY, renderSize, renderSize, ModConstants.COLOR_RED);

        // Draw tile ID text
        String tileId = String.format("Z:%d X:%d Y:%d", key.z, key.x, key.y);
        context.text(
                textRenderer,
                tileId,
                screenX + 5,
                screenY + 5,
                ModConstants.COLOR_WHITE,
                true
        );
    }

    /**
     * Releases tile provider resources owned by this renderer.
     */
    @Override
    public void close() {

        if (tileProvider != null) {
            tileProvider.close();
            tileProvider = null;
        }
    }

    /**
     * {@inheritDoc}
     * PMTiles archives have no per-tile URL, so each line is just the tile key.
     */
    @Override
    public List<String> getDebugLoadingLines() {

        if (tileProvider == null) return List.of();

        return tileProvider.getLoadingTiles().stream()
                .map(PmTileKey::toString)
                .toList();
    }
}
