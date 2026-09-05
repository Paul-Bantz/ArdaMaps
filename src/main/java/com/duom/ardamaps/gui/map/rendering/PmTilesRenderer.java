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
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

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
    public PmTilesRenderer(PmTilesMapCamera camera, TextRenderer textRenderer, PlayerExploration exploration) {

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
    public void render(DrawContext context) {

        if (tileProvider == null) {
            if (loadError != null) {
                context.drawCenteredTextWithShadow(
                        textRenderer,
                        Text.translatable("ardamaps.client.map.screen.layer_load_failed", loadError),
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
        renderFogOfWar();
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
     * Render the PMTiles layer for the current frame.
     *
     * @param context Draw context used for rendering.
     */
    private void renderMap(DrawContext context) {

        int minZoom = tileProvider.getMinZoom();
        Set<PmTileKey> tilesToDisplay = mapCamera.getVisibleTiles();
        boolean settled = mapCamera.isSettled();

        tileProvider.beginFrame();

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

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        boolean debugMode = ArdaMapsClient.CONFIG.isMapDebugDisplay();

        drawTilePass(context, orderedFallbacks(plan), debugMode);
        drawTilePass(context, plan.primaryTiles(), debugMode);
    }

    /**
     * Request viewport, prefetch, and adjacent-zoom tiles for the current frame.
     *
     * @param minZoom Minimum zoom level available from the PMTiles source.
     * @param settled Whether the camera has settled enough to prefetch.
     */
    void requestTilesForFrame(int minZoom, boolean settled) {

        for (PmTileKey key : mapCamera.getVisibleTiles(minZoom)) {
            tileProvider.request(key, TileProvider.VIEWPORT_FALLBACK_PRIORITY_BASE
                    + mapCamera.centerTileDistance(key.x, key.y, key.z));
        }

        if (!settled) return;

        int primaryZ = mapCamera.getTileSourceClampedZoom();
        for (PmTileKey key : mapCamera.getRequestTiles(primaryZ, 1)) {
            tileProvider.request(key, TileProvider.PRIMARY_PREFETCH_PRIORITY_BASE
                    + mapCamera.centerTileDistance(key.x, key.y, primaryZ));
        }

        int zoomStep = primaryZ + 1;
        if (zoomStep <= tileProvider.getMaxZoom()) {
            for (PmTileKey key : mapCamera.getVisibleTiles(zoomStep)) {
                tileProvider.request(key, TileProvider.ZOOM_STEP_PRIORITY_BASE
                        + mapCamera.centerTileDistance(key.x, key.y, zoomStep));
            }
        }
    }

    /**
     * Classify visible tiles into primary draws and fallback draws.
     *
     * @param tilesToDisplay Tiles currently visible in the viewport.
     * @param minZoom Minimum zoom level available from the PMTiles source.
     * @param settled Whether the camera has settled enough to prefetch.
     * @return Render plan with primary and fallback tiles split out.
     */
    RenderPlan classifyTiles(Set<PmTileKey> tilesToDisplay, int minZoom, boolean settled) {

        List<TileDraw> primaryTiles = new ArrayList<>();
        Map<PmTileKey, TileDraw> fallbackMap = new LinkedHashMap<>();

        for (PmTileKey key : tilesToDisplay) {

            if (settled) {
                tileProvider.request(key, TileProvider.PRIMARY_VIEWPORT_PRIORITY_BASE
                        + mapCamera.centerTileDistance(key.x, key.y, key.z));
            }

            Optional<Identifier> tex = tileProvider.peek(key);
            if (tex.isPresent()) {
                var screenPos = mapCamera.tilePositionOnViewport(key.x, key.y, key.z);
                primaryTiles.add(new TileDraw(tex.get(), (float) screenPos.x(), (float) screenPos.y(), key));
                continue;
            }

            var fallbackTile = findFallbackTile(key, minZoom);
            PmTileKey fallbackKey = fallbackTile.getLeft();
            if (fallbackTile.getRight().isPresent() && !fallbackMap.containsKey(fallbackKey)) {
                var fallbackPos = mapCamera.tilePositionOnViewport(fallbackKey.x, fallbackKey.y, fallbackKey.z);
                fallbackMap.put(fallbackKey, new TileDraw(
                        fallbackTile.getRight().get(),
                        (float) fallbackPos.x(),
                        (float) fallbackPos.y(),
                        fallbackKey));
            }
        }

        return new RenderPlan(primaryTiles, fallbackMap);
    }

    /**
     * Orders the resolved fallback tiles coarsest-first (ascending {@code z}) for drawing.
     * <p>
     * {@link #findFallbackTile} can resolve missing primaries to several coarseness levels in the
     * same frame (previous-primary {@code z-1} down to {@code minZoom}), and a coarser tile's
     * footprint overlaps finer ones. Drawing coarsest-first guarantees a finer fallback always
     * paints over the coarser tile it overlaps (blur → sharp); primaries then paint over all of them.
     *
     * @param plan the classified render plan for this frame
     * @return the fallback draws sorted ascending by tile zoom
     */
    List<TileDraw> orderedFallbacks(RenderPlan plan) {

        List<TileDraw> ordered = new ArrayList<>(plan.fallbackMap().values());
        ordered.sort(Comparator.comparingInt(t -> t.key().z));
        return ordered;
    }

    /**
     * Draw a batch of tiles to the screen.
     *
     * @param context Draw context used for rendering.
     * @param tiles Tiles to draw.
     * @param debugMode Whether to overlay tile borders and labels.
     */
    private void drawTilePass(DrawContext context, Iterable<TileDraw> tiles, boolean debugMode) {

        var textureManager = MinecraftClient.getInstance().getTextureManager();

        for (TileDraw tile : tiles) {
            int renderSize = getDisplayedTileSize(tile.key().z);
            int roundedX = Math.round(tile.x0());
            int roundedY = Math.round(tile.y0());

            Identifier currentTexture = tile.texture();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            textureManager.bindTexture(currentTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            RenderSystem.setShaderTexture(0, currentTexture);

            context.drawTexture(
                    currentTexture,
                    roundedX, roundedY,
                    0, 0,
                    renderSize, renderSize,
                    renderSize, renderSize
            );

            if (debugMode) drawDebugLines(context, tile.key(), roundedX, roundedY, renderSize);
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

        while (current.z > minZoom) {
            current = new PmTileKey(
                    current.z - 1,
                    current.x >> 1,
                    current.y >> 1
            );

            Optional<Identifier> tex = tileProvider.peek(current);
            if (tex.isPresent()) return new Pair<>(current, tex);

        }

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
     * Draw diagnostic borders and tile coordinates over a rendered tile.
     *
     * @param context Draw context used for rendering.
     * @param key Tile key being rendered.
     * @param screenX Screen X coordinate of the tile.
     * @param screenY Screen Y coordinate of the tile.
     * @param renderSize Rendered size in pixels.
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
     * Classified tiles for one render pass.
     */
    record RenderPlan(List<TileDraw> primaryTiles, Map<PmTileKey, TileDraw> fallbackMap) {

    }

    /**
     * Resolved tile draw parameters for a PMTiles quad.
     */
    record TileDraw(Identifier texture, float x0, float y0, PmTileKey key) {

    }
}
