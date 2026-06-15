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

import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.map.cameras.BlueMapCamera;
import com.duom.ardamaps.core.data.map.providers.BlueMapTileProvider;
import com.duom.ardamaps.core.data.map.tiles.PmTileKey;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

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
     */
    public BlueMapRenderer(BlueMapCamera camera, TextRenderer textRenderer) {

        super(camera, textRenderer);
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
    public void render(DrawContext context) {

        // Handle loading state - if provider is not initialized, show placeholder
        if (provider == null) {
            super.renderLoadingText(context);
            return;
        }

        renderMap();
        renderFogOfWar();
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
     * Sub-pixel precision is preserved by encoding the exact floating-point screen position directly
     * into vertex coordinates rather than using a matrix push/translate/pop.
     * </p>
     */
    private void renderMap() {

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
                Pair<PmTileKey, Optional<Identifier>> fallback =
                        findFallbackTile(key, coarsestZoom, (int) mapCamera.getLodFactor());

                PmTileKey fbKey = fallback.getLeft();
                // Store fbKey.z as the tile's actual LOD — findFallbackTile returns the *first*
                // loaded ancestor which can be at any intermediate LOD, not necessarily coarsestZoom.
                // Using the wrong LOD produces incorrect quad size, UV extents, LodScale and TexelSize.
                if (fallback.getRight().isPresent() && !fallbackMap.containsKey(fbKey)) {
                    var fbPos = mapCamera.tilePositionOnViewport(fbKey.x, fbKey.y, fbKey.z);
                    fallbackMap.put(fbKey, new TileDraw(
                            fallback.getRight().get(),
                            (float) fbPos.x(), (float) fbPos.y(),
                            fbKey.z));
                }
            }
        }

        // Shared shader setup (once for the whole frame)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(BlueMapTileShader::blueMapTile);
        BlueMapTileShader.setSunlightStrength(SUNLIGHT_STRENGTH);
        BlueMapTileShader.setAmbientLight(AMBIENT_LIGHT);

        // Pass 1: primary LOD tiles (all same lod -> one uniform update)
        if (!primaryTiles.isEmpty()) {
            drawTilePass(primaryTiles, primaryZ);
        }

        // Pass 2: fallback tiles, grouped by their actual resolved LOD.
        // Each unique lod gets exactly one uniform update before its tiles are drawn.
        if (!fallbackMap.isEmpty()) {
            Map<Integer, List<TileDraw>> byLod = new LinkedHashMap<>();
            for (TileDraw tile : fallbackMap.values()) {
                byLod.computeIfAbsent(tile.lod(), k -> new ArrayList<>()).add(tile);
            }
            byLod.forEach((lod, tiles) -> drawTilePass(tiles, lod));
        }

        RenderSystem.disableBlend();
    }

    /**
     * Finds the nearest loaded fallback tile for the given tile key by traversing up the LOD hierarchy.
     *
     * @param key       The original tile key for which to find a fallback
     * @param maxLod    The maximum LOD level to search up to (coarsest zoom)
     * @param lodFactor The factor by which each LOD level reduces resolution (e.g. 2 means each level halves resolution)
     * @return A pair containing the fallback tile key and its texture identifier if found, or empty if no fallback is loaded
     */
    private Pair<PmTileKey, Optional<Identifier>> findFallbackTile(PmTileKey key, int maxLod, int lodFactor) {
        PmTileKey current = key;

        do {
            current = new PmTileKey(
                    current.z + 1,
                    Math.floorDiv(current.x, lodFactor),
                    Math.floorDiv(current.y, lodFactor)
            );

            Optional<Identifier> tex = provider.get(current);
            if (tex.isPresent()) return new Pair<>(current, tex);

        } while (current.z <= maxLod);

        return new Pair<>(key, Optional.empty());
    }

    /**
     * Draws a batch of tiles that all belong to the same LOD level.
     * Uniforms specific to the LOD ({@code LodScale}, {@code TexelSize}) are set once before
     * iterating, and each tile receives only a texture bind + one quad draw call.
     *
     * @param tiles List of pre-resolved tiles to draw.
     * @param lod   LOD zoom level shared by all tiles in this pass.
     */
    private void drawTilePass(List<TileDraw> tiles, int lod) {

        int renderSize = getDisplayedTileSize(lod);
        int imageSize = renderSize + 1;   // BlueMap adds a 1-pixel overlap on the right/bottom edge
        float uMax = (float) renderSize / imageSize;
        float vMax = (float) renderSize / (imageSize * 2);
        float lodScale = (float) Math.pow(mapCamera.getLodFactor(), lod - 1);

        // Set LOD-dependent uniforms once for the whole pass
        BlueMapTileShader.setLodScale(lodScale);
        BlueMapTileShader.setTexelSize(1f / imageSize, 1f / (imageSize * 2));

        var textureManager = MinecraftClient.getInstance().getTextureManager();
        Tessellator tessellator = Tessellator.getInstance();

        for (TileDraw tile : tiles) {

            // Bind texture and set NEAREST filtering (parameters are stored per GL texture object
            // so newly loaded tiles also get the correct filter on first bind)
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            textureManager.bindTexture(tile.texture());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            RenderSystem.setShaderTexture(0, tile.texture());

            float x0 = tile.x0();
            float y0 = tile.y0();

            // Sub-pixel offset baked directly into vertex positions — no matrix push/translate/pop
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            buffer.vertex(x0, y0 + renderSize, 0).texture(0, vMax).next();
            buffer.vertex(x0 + renderSize, y0 + renderSize, 0).texture(uMax, vMax).next();
            buffer.vertex(x0 + renderSize, y0, 0).texture(uMax, 0).next();
            buffer.vertex(x0, y0, 0).texture(0, 0).next();
            BufferRenderer.drawWithGlobalProgram(buffer.end());
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