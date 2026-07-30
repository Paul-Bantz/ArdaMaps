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

import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.map.cameras.BlueMapCamera;
import com.duom.ardamaps.core.data.map.providers.BlueMapTileProvider;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Tuple;
import org.joml.Matrix3x2f;

import java.util.*;

/**
 * Renderer for BlueMap layers using PMTiles.
 * <br/>This class handles rendering map tiles from a PMTiles source, managing visible tiles based on the camera position and zoom level.
 * It optimizes rendering by minimizing texture binds and only rendering fully loaded tiles.
 * <br/>Tiles are rendered in LOD-grouped batched passes: primary tiles first, then fallback tiles grouped by
 * their actual resolved LOD, so that shader uniforms are updated at most once per unique LOD per frame.
 */
public class BlueMapRenderer extends MapRenderable {

    /** Sunlight strength passed to the tile shader (0 = block-lit only, 1 = full sun). */
    private static final float SUNLIGHT_STRENGTH = 0.7f;

    /** Minimum brightness when block light is zero (ambient occlusion floor). */
    private static final float AMBIENT_LIGHT = 0.3f;

    /** Camera for managing view and visible tiles */
    private final BlueMapCamera mapCamera;

    /** Tile provider for fetching tile textures from PMTiles source */
    private BlueMapTileProvider provider;

    /**
     * Constructor for BlueMapRenderer.
     * The camera must already have Dimension set before this constructor is called
     * (guaranteed by MapScreen which builds the camera first).
     *
     * @param camera       The pre-built, dimension-aware camera for this renderer.
     * @param textRenderer TextRenderer instance for rendering loading text when tiles are not yet available.
     * @param exploration  The fog-of-war exploration state to render for this map layer.
     */
    public BlueMapRenderer(BlueMapCamera camera, Font textRenderer, PlayerExploration exploration) {

        super(camera, textRenderer, exploration);
        this.mapCamera = camera;
    }

    /**
     * Configures the renderer with the given map layer definition, initializing the tile provider and camera settings based on the layer configuration.
     * Note: {@code setDimension} is intentionally absent - the camera already has its dimension set by MapScreen.
     *
     * @param layer       MapLayerDefinition containing configuration for the map layer to render
     * @param renderScale the preferred render scale to display the map
     */
    @Override
    public void configure(MapLayerDefinition layer, double renderScale) {

        provider = new BlueMapTileProvider(layer.path(), layer.minLod(), layer.maxLod());

        mapCamera.setCameraZoomBounds(layer.minZoom(), layer.maxZoom());
        mapCamera.setIdentityZoom(layer.identityZoom());
        mapCamera.setPreferredZoom(layer.preferredZoom());
        mapCamera.updateZoom();
        mapCamera.setScale(layer.scale());
        mapCamera.setTileSize(layer.tileSize());
        mapCamera.setLodFactor(layer.lodFactor());
        mapCamera.setPreferredRenderScale(renderScale);
        mapCamera.setZoomToMatchVisualPixelsPerBlock();

        // Configure-time preload intentionally bypasses get() debounce.
        mapCamera.getVisibleTiles(mapCamera.getCoarsestZoom()).forEach(key -> provider.eagerLoadTile(key));
    }

    /**
     * Renders the map layer. If the tile provider is not yet initialized, it renders a loading text. Otherwise,
     * it renders the visible tiles based on the current camera view.
     *
     * @param context DrawContext for rendering operations
     */
    @Override
    public void render(GuiGraphicsExtractor context) {

        // Handle loading state - if provider is not initialized, show placeholder
        if (provider == null) {
            super.renderLoadingText(context);
            return;
        }

        renderMap(context);
        renderFogOfWar(context);
    }

    /**
     * Releases tile provider resources owned by this renderer.
     */
    @Override
    public void close() {

        if (provider != null) {
            provider.close();
            provider = null;
        }
    }

    /**
     * Renders the map tiles in LOD-grouped batched passes.
     * <p>
     * A single classification loop separates tiles into:
     * <ul>
     *   <li><b>Primary tiles</b> — loaded at the current LOD; all share {@code primaryZ}.</li>
     *   <li><b>Fallback tiles</b> — resolved by {@link #findFallbackTile}, which returns the
     *       <em>first</em> loaded ancestor up the LOD hierarchy. This ancestor may be at any
     *       intermediate LOD, not necessarily {@code coarsestZoom}. Tiles are deduplicated by
     *       their {@link PmTileKey} (many primary tiles can resolve to the same coarser tile)
     *       and then grouped by their actual LOD so shader uniforms and quad geometry are correct.</li>
     * </ul>
     * Sub-pixel precision is preserved by submitting each tile's floating-point screen
     * bounds directly to the GUI render state.
     * </p>
     */
    private void renderMap(GuiGraphicsExtractor context) {

        int coarsestZoom = mapCamera.getCoarsestZoom();
        int primaryZ = mapCamera.getTileSourceClampedZoom();

        // Ensure coarsest-LOD tiles are loaded so fallbacks are always available
        mapCamera.getVisibleTiles(coarsestZoom).forEach(key -> provider.get(key));

        Set<PmTileKey> tilesToDisplay = mapCamera.getVisibleTiles();
        tilesToDisplay.forEach(key -> provider.get(key));

        // Classify
        List<TileDraw> primaryTiles = new ArrayList<>();

        // Deduplicate fallbacks by their PmTileKey: many primary tiles can map to the same coarser
        // tile; drawing it multiple times per frame causes alpha-blend overdraw and flickering.
        // LinkedHashMap preserves insertion order so draw order is deterministic.
        Map<PmTileKey, TileDraw> fallbackMap = new LinkedHashMap<>();

        for (PmTileKey key : tilesToDisplay) {

            Optional<Identifier> tex = provider.get(key);

            if (tex.isPresent()) {
                var screenPos = mapCamera.tilePositionOnViewport(key.x, key.y, key.z);
                primaryTiles.add(new TileDraw(tex.get(), (float) screenPos.x(), (float) screenPos.y(), key.z));

            } else {
                Tuple<PmTileKey, Optional<Identifier>> fallback =
                        findFallbackTile(key, coarsestZoom, mapCamera.getLodFactor());

                PmTileKey fbKey = fallback.getA();
                // Store fbKey.z as the tile's actual LOD — findFallbackTile returns the *first*
                // loaded ancestor which can be at any intermediate LOD, not necessarily coarsestZoom.
                // Using the wrong LOD produces incorrect quad size, UV extents, LodScale and TexelSize.
                if (fallback.getB().isPresent() && !fallbackMap.containsKey(fbKey)) {
                    var fbPos = mapCamera.tilePositionOnViewport(fbKey.x, fbKey.y, fbKey.z);
                    fallbackMap.put(fbKey, new TileDraw(
                            fallback.getB().get(),
                            (float) fbPos.x(), (float) fbPos.y(),
                            fbKey.z));
                }
            }
        }

        // Pass 1: primary LOD tiles (all same lod -> one uniform update)
        if (!primaryTiles.isEmpty()) {
            drawTilePass(context, primaryTiles, primaryZ);
        }

        // Pass 2: fallback tiles, grouped by their actual resolved LOD.
        // Each unique lod gets exactly one uniform update before its tiles are drawn.
        if (!fallbackMap.isEmpty()) {
            Map<Integer, List<TileDraw>> byLod = new LinkedHashMap<>();
            for (TileDraw tile : fallbackMap.values()) {
                byLod.computeIfAbsent(tile.lod(), k -> new ArrayList<>()).add(tile);
            }
            byLod.forEach((lod, tiles) -> drawTilePass(context, tiles, lod));
        }

    }

    /**
     * Finds the nearest loaded fallback tile for the given tile key by traversing up the LOD hierarchy.
     *
     * @param key       The original tile key for which to find a fallback
     * @param maxLod    The maximum LOD level to search up to (coarsest zoom)
     * @param lodFactor The factor by which each LOD level reduces resolution (e.g. 2 means each level halves resolution)
     * @return A pair containing the fallback tile key and its texture identifier if found, or empty if no fallback is loaded
     */
    private Tuple<PmTileKey, Optional<Identifier>> findFallbackTile(PmTileKey key, int maxLod, double lodFactor) {
        PmTileKey current = key;
        if (lodFactor < 1.0) lodFactor = 1.0;

        while (current.z < maxLod) {
            current = new PmTileKey(
                    current.z + 1,
                    (int) Math.floor(current.x / lodFactor),
                    (int) Math.floor(current.y / lodFactor)
            );

            Optional<Identifier> tex = provider.get(current);
            if (tex.isPresent()) return new Tuple<>(current, tex);

        }

        return new Tuple<>(key, Optional.empty());
    }

    /**
     * Draws a batch of tiles that all belong to the same LOD level.
     * Uniforms specific to the LOD ({@code LodScale}, {@code TexelSize}) are set once before
     * iterating, and each tile receives only a texture bind + one quad draw call.
     *
     * @param tiles List of pre-resolved tiles to draw.
     * @param lod   LOD zoom level shared by all tiles in this pass.
     */
    private void drawTilePass(GuiGraphicsExtractor context, List<TileDraw> tiles, int lod) {

        int renderSize = getDisplayedTileSize(lod);
        int imageSize = renderSize + 1;   // BlueMap adds a 1-pixel overlap on the right/bottom edge
        float lodScale = (float) Math.pow(mapCamera.getLodFactor(), lod - 1);
        float texelSizeX = 1f / imageSize;
        Matrix3x2f pose = new Matrix3x2f(context.pose());
        var scissorArea = GuiRenderStateAccess.scissorArea(context);

        for (TileDraw tile : tiles) {
            GuiRenderStateAccess.add(context, new BlueMapTileRenderState(
                    tile.texture(),
                    pose,
                    tile.x0(),
                    tile.y0(),
                    tile.x0() + renderSize,
                    tile.y0() + renderSize,
                    SUNLIGHT_STRENGTH,
                    AMBIENT_LIGHT,
                    lodScale,
                    texelSizeX,
                    scissorArea));
        }
    }

    /**
     * Calculates the displayed tile size based on the current zoom level and camera settings.
     *
     * @param z Zoom level of the tile
     * @return Displayed tile size in pixels
     */
    private int getDisplayedTileSize(int z) {

        return mapCamera.displayedTileSize(z);
    }

    /**
     * Lightweight carrier for a resolved tile ready to be drawn.
     *
     * @param texture The loaded tile texture identifier.
     * @param x0      Left edge of the quad in screen pixels (floating-point for sub-pixel accuracy).
     * @param y0      Top edge of the quad in screen pixels (floating-point for sub-pixel accuracy).
     * @param lod     Actual LOD zoom level of this tile — drives quad size, UV extents and shader uniforms.
     */
    private record TileDraw(Identifier texture, float x0, float y0, int lod) {
    }
}
