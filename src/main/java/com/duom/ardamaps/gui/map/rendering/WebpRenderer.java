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
import com.duom.ardamaps.core.data.map.cameras.FlatMapCamera;
import com.duom.ardamaps.core.data.map.providers.HttpImageProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.opengl.GL11;

/**
 * A map renderable that can display WEBP images as map layers.
 */
public class WebpRenderer extends MapRenderable {

    /** The camera used to determine how the map image is rendered based on the current view. */
    private final FlatMapCamera mapCamera;

    /** The path to the currently displayed image, used to fetch the texture from the provider. */
    private String displayedImage;

    /**
     * Constructs a new WebpRenderer.
     * The camera must already have {@link FlatMapCamera#setDimension} called before this constructor
     * (guaranteed by MapScreen which builds the camera first).
     *
     * @param camera       The pre-built, dimension-aware camera for this renderer.
     * @param textRenderer The text renderer for displaying loading text or other information.
     */
    public WebpRenderer(FlatMapCamera camera, TextRenderer textRenderer) {

        super(camera, textRenderer);
        this.mapCamera = camera;
    }

    /**
     * Sets the map layer to be displayed by this viewer.
     * Note: {@code setDimension} is intentionally absent - the camera already has its dimension set by MapScreen.
     *
     * @param layer       The map layer definition containing the type and path.
     * @param renderScale the preferred render scale to display the map
     */
    @Override
    public void configure(MapLayerDefinition layer, double renderScale) {

        mapCamera.setCameraZoomBounds(layer.minZoom(), layer.maxZoom());
        mapCamera.setIdentityZoom(layer.identityZoom());
        mapCamera.setScale(layer.scale());
        mapCamera.setPreferredRenderScale(renderScale);

        displayedImage = layer.path();
    }

    /**
     * Renders the WEBP image onto the map.
     *
     * @param context The drawing context.
     */
    @Override
    public void render(DrawContext context) {

        renderMap(context);
        renderFogOfWar();
    }

    /**
     * Renders the map image based on the current camera settings and the texture provided by the HttpImageProvider.
     * <p>
     * This method handles sub-pixel rendering to ensure smooth movement and zooming of the map. It calculates the
     * appropriate screen position for the map image based on the camera's world position and scale, and then draws
     * the texture onto the screen. If the texture is not yet available, it renders a loading text instead.
     *
     * @param context The drawing context.
     */
    private void renderMap(DrawContext context) {

        double scale = mapCamera.scale();
        var renderWidth = mapCamera.getWorldTextureWidth();
        var renderHeight = mapCamera.getWorldTextureHeight();

        // Convert camera world position to image coordinates
        double cameraImageX = (mapCamera.getWorldX() - getDimension().getXMin()) / mapCamera.getBlocksPerPixel();
        double cameraImageY = (mapCamera.getWorldZ() - getDimension().getZMin()) / mapCamera.getBlocksPerPixel();

        // Calculate screen position to centre the camera
        double viewportCenterX = mapCamera.getViewportWidth() / 2.0;
        double viewportCenterY = mapCamera.getViewportHeight() / 2.0;

        // Keep x and y as doubles for sub-pixel rendering
        double screenX = viewportCenterX - cameraImageX * scale;
        double screenY = viewportCenterY - cameraImageY * scale;

        var provider = ArdaMapsClient.getHttpImageProvider();
        var texture = provider.getTexture(displayedImage);

        if (texture == null) {
            super.renderLoadingText(context);
            return;
        }

        syncTextureDimensions(provider);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // Translate for sub-pixel accuracy
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(screenX - Math.floor(screenX), screenY - Math.floor(screenY), 0);

        context.drawTexture(
                texture,
                (int) Math.floor(screenX),
                (int) Math.floor(screenY),
                0,
                0,
                renderWidth,
                renderHeight,
                renderWidth,
                renderHeight
        );

        matrices.pop();
        RenderSystem.disableBlend();
    }

    /**
     * Synchronizes the texture dimensions with the map camera.
     */
    private void syncTextureDimensions(HttpImageProvider provider) {

        if (mapCamera.getImageWidth() != provider.getTextureWidth(displayedImage) ||
                mapCamera.getImageHeight() != provider.getTextureHeight(displayedImage)) {

            mapCamera.setImageWidth(provider.getTextureWidth(displayedImage));
            mapCamera.setImageHeight(provider.getTextureHeight(displayedImage));

            // Ensure the camera zoom is set to match the visual pixels per block for accurate rendering if required
            mapCamera.setZoomToMatchVisualPixelsPerBlock();
        }
    }
}