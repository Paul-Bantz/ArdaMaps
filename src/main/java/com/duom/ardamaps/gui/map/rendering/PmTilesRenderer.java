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
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A map viewer that renders map tiles from PMTiles files.
 */
public class PmTilesRenderer extends MapRenderable {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PmTilesRenderer.class);

    /** Approximate decoded PMTiles tile cost for common 256x256 RGBA tiles. */
    static final long APPROX_DECODED_TILE_BYTES = 256L * 256L * 4L;

    /** The camera used to determine which tiles are visible and how they should be rendered based on the current view. */
    private final PmTilesMapCamera mapCamera;

    /** Tile provider to load tiles from */
    private TileProvider<PmTileKey> tileProvider;

    /** Layer name whose PMTiles source failed to load, or {@code null} when loading succeeded. */
    @Nullable
    private String loadError;

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
            loadError = null;

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

            loadError = layer.layer();
            LOGGER.error("Failed to load PMTiles layer: {}", layer.layer(), e);
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
            if (loadError != null) {
                context.centeredText(
                        textRenderer,
                        Component.translatable("ardamaps.client.map.screen.layer_load_failed", loadError),
                        camera.getViewportWidth() / 2,
                        camera.getViewportHeight() / 2,
                        ModConstants.COLOR_WHITE);
                return;
            }
            super.renderLoadingText(context);
            return;
        }

        /* Rendering */

        renderMap(context);
        renderFogOfWar(context);
    }

    /**
     * Renders the visible map tiles in two draw passes.
     * <p>
     * Tile loading is bounded and prioritised via {@link TileProvider#beginFrame()} /
     * {@link TileProvider#request} / {@link TileProvider#endFrame()} across two tiers, most urgent
     * first: primary-zoom tiles actually in the viewport (ranked by distance from the viewport
     * centre), the one-tile ring just outside the viewport, coarse-zoom tiles in the viewport
     * backing the immediate visual fallback, and the adjacent PMTiles zoom-step viewport at
     * {@code primaryZ + 1} when its estimated decoded footprint fits the speculative budget.
     * Primary-zoom requests are only registered once the camera has settled
     * ({@link com.duom.ardamaps.core.data.map.cameras.MapCamera#isSettled()}); during a fast pan or
     * zoom only the viewport's coarse fallback is requested.
     * </p>
     * <p>
     * A classification pass resolves primary tiles and their already-loaded coarse fallbacks before
     * anything is drawn. Fallbacks are deduplicated by the resolved {@link PmTileKey} because many
     * primary tiles can map to the same coarser tile; drawing that ancestor repeatedly creates
     * alpha-blend overdraw and flicker. All fallback quads are drawn before primary quads so a
     * blurry ancestor can never repaint over a sharp tile that has already loaded.
     * </p>
     *
     * @param context the draw context
     */
    private void renderMap(GuiGraphicsExtractor context) {

        int minZoom = tileProvider.getMinZoom();
        boolean settled = mapCamera.isSettled();

        tileProvider.beginFrame();

        Set<PmTileKey> tilesToDisplay = mapCamera.getVisibleTiles();
        requestTilesForFrame(minZoom, settled);

        RenderPlan plan = classifyTiles(tilesToDisplay, minZoom, settled);

        tileProvider.endFrame();

        if (plan.primaryTiles().isEmpty() && plan.fallbackMap().isEmpty()) {
            tileProvider.protectDrawnTiles(Set.of());
            super.renderLoadingText(context);
            return;
        }

        Set<PmTileKey> drawnTiles = new HashSet<>();
        plan.primaryTiles().forEach(tile -> drawnTiles.add(tile.key()));
        drawnTiles.addAll(plan.fallbackMap().keySet());
        tileProvider.protectDrawnTiles(drawnTiles);

        boolean debugMode = ArdaMapsClient.CONFIG.isMapDebugDisplay();

        // Pass 1: coarse fallback base layer, drawn first so it cannot overpaint sharp primaries.
        drawTilePass(context, plan.fallbackMap().values(), debugMode);

        // Pass 2: primary tiles, drawn over the fallback base wherever they are available.
        drawTilePass(context, plan.primaryTiles(), debugMode);
    }

    /**
     * Registers tile load requests for this frame across multiple priority tiers: coarse fallback tiles,
     * primary-zoom prefetch ring, and optionally the finer zoom-step viewport if budget permits.
     * Only primary-zoom tiles are requested when the camera is settled; during pans/zooms only the
     * coarse fallback is requested to avoid queuing tiles that will scroll off screen.
     *
     * @param minZoom The minimum zoom level to use for coarse-tile fallback.
     * @param settled Whether the camera has been still long enough to request fine-grained primary tiles.
     */
    void requestTilesForFrame(int minZoom, boolean settled) {

        // Tier 1: coarse-zoom tiles within the current viewport - the immediate visual fallback.
        for (PmTileKey key : mapCamera.getVisibleTiles(minZoom)) {
            tileProvider.request(key, TileProvider.VIEWPORT_FALLBACK_PRIORITY_BASE
                    + mapCamera.centerTileDistance(key.x, key.y, minZoom));
        }

        if (!settled) return;

        int primaryZ = mapCamera.getTileSourceClampedZoom();
        for (PmTileKey key : mapCamera.getRequestTiles(primaryZ, 1)) {
            tileProvider.request(key, TileProvider.PRIMARY_PREFETCH_PRIORITY_BASE
                    + mapCamera.centerTileDistance(key.x, key.y, primaryZ));
        }

        int zoomStep = primaryZ + 1;
        if (zoomStep <= tileProvider.getMaxZoom()) {
            Set<PmTileKey> zoomStepTiles = mapCamera.getVisibleTiles(zoomStep);
            long estimatedBytes = (long) zoomStepTiles.size() * APPROX_DECODED_TILE_BYTES;
            if (estimatedBytes > TileProvider.zoomStepByteCeiling()) return;

            for (PmTileKey key : zoomStepTiles) {
                tileProvider.request(key, TileProvider.ZOOM_STEP_PRIORITY_BASE
                        + mapCamera.centerTileDistance(key.x, key.y, zoomStep));
            }
        }
    }

    /**
     * Classifies visible tiles into primary (already loaded) and fallback (coarser ancestors) tiers,
     * deduplicating fallback tiles so a shared coarser ancestor is drawn only once. Requests primary
     * tiles with viewport-distance-weighted priority if the camera is settled.
     *
     * @param tilesToDisplay The set of primary-zoom tiles currently in viewport.
     * @param minZoom The minimum zoom level available; tiles degrade to ancestors at or below this.
     * @param settled Whether to register primary tile requests (true) or just classify fallbacks (false).
     * @return A render plan with primary tiles and deduplicated fallback map grouped by resolved key.
     */
    RenderPlan classifyTiles(Set<PmTileKey> tilesToDisplay, int minZoom, boolean settled) {

        List<TileDraw> primaryTiles = new ArrayList<>();
        Map<PmTileKey, TileDraw> fallbackMap = new LinkedHashMap<>();

        for (PmTileKey key : tilesToDisplay) {

            if (settled) {
                int ring = mapCamera.centerTileDistance(key.x, key.y, key.z);
                tileProvider.request(key, TileProvider.PRIMARY_VIEWPORT_PRIORITY_BASE + ring);
            }

            Optional<Identifier> tex = tileProvider.peek(key);

            if (tex.isPresent()) {
                var screenPos = mapCamera.tilePositionOnViewport(key.x, key.y, key.z);
                primaryTiles.add(new TileDraw(tex.get(), (float) screenPos.x(), (float) screenPos.y(), key));
                continue;
            }

            var fallbackTile = findFallbackTile(key, minZoom);
            PmTileKey fallbackKey = fallbackTile.getA();

            if (fallbackTile.getB().isPresent() && !fallbackMap.containsKey(fallbackKey)) {
                var fallbackPos = mapCamera.tilePositionOnViewport(fallbackKey.x, fallbackKey.y, fallbackKey.z);
                fallbackMap.put(fallbackKey, new TileDraw(
                        fallbackTile.getB().get(),
                        (float) fallbackPos.x(),
                        (float) fallbackPos.y(),
                        fallbackKey));
            }
        }

        return new RenderPlan(primaryTiles, fallbackMap);
    }

    private void drawTilePass(GuiGraphicsExtractor context, Iterable<TileDraw> tiles, boolean debugMode) {

        for (TileDraw tile : tiles) {
            int renderSize = getDisplayedTileSize(tile.key().z);

            GuiRenderStateAccess.add(context, new PmTilesTileRenderState(
                    tile.texture(),
                    new org.joml.Matrix3x2f(context.pose()),
                    tile.x0(),
                    tile.y0(),
                    tile.x0() + renderSize,
                    tile.y0() + renderSize,
                    GuiRenderStateAccess.scissorArea(context)));

            if (debugMode) drawDebugLines(context, tile.key(), Math.round(tile.x0()), Math.round(tile.y0()), renderSize);
        }
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

    /**
     * Rendering plan for a frame, separating already-loaded primary tiles from their fallback ancestors.
     * Fallback tiles are deduplicated by key to avoid redundant draw calls when multiple primary tiles
     * map to the same ancestor.
     *
     * @param primaryTiles List of tiles loaded at the primary zoom level, ready to draw.
     * @param fallbackMap Deduplicated map of coarser ancestors keyed by their actual {@link PmTileKey}.
     */
    record RenderPlan(List<TileDraw> primaryTiles, Map<PmTileKey, TileDraw> fallbackMap) {

    }

    /**
     * Lightweight carrier for a resolved tile ready to be drawn.
     *
     * @param texture The loaded tile texture identifier.
     * @param x0      Left edge of the quad in screen pixels (floating-point for sub-pixel accuracy).
     * @param y0      Top edge of the quad in screen pixels (floating-point for sub-pixel accuracy).
     * @param key     The tile's actual key; {@code key.z} drives quad size for coarse fallbacks.
     */
    record TileDraw(Identifier texture, float x0, float y0, PmTileKey key) {

    }
}
