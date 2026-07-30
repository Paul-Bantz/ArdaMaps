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
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.gui.ModConstants;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Abstract base class for renderable map layers.
 */
public abstract class MapRenderable {

    /** Text renderer for rendering placeholder text */
    protected final Font textRenderer;

    /** The camera used for coordinate conversions and viewport queries. Injected externally - MapScreen owns and builds it. */
    @Getter
    protected final MapCamera camera;

    /** Fog-of-war exploration state rendered over this map layer. */
    @Getter
    protected final PlayerExploration exploration;

    /**
     * Constructor for MapRenderable.
     * The camera must already have its {@link MapCamera#setDimension(Dimension)} called before this constructor runs,
     * so that the exploration state can be resolved immediately.
     *
     * @param camera       The pre-built, fully configured camera for this renderable.
     * @param textRenderer The text renderer for drawing the placeholder text.
     * @param exploration  The fog-of-war exploration state to render for this map layer.
     */
    public MapRenderable(MapCamera camera, Font textRenderer, PlayerExploration exploration) {

        this.camera = camera;
        this.textRenderer = textRenderer;
        this.exploration = exploration;
    }

    /**
     * Sets the map layer definition for this renderable.
     *
     * @param layer       The map layer definition to set.
     * @param renderScale the preferred render scale
     */
    public abstract void configure(MapLayerDefinition layer, double renderScale);

    /**
     * Renders the map layer.
     *
     * @param context The DrawContext to render with.
     */
    public abstract void render(GuiGraphics context);

    /**
     * Releases resources owned by this renderable.
     */
    public void close() {
        // Default renderables do not own closeable resources.
    }

    /**
     * Renders a loading text centered in the viewport.
     *
     * @param context The DrawContext to render with.
     */
    protected void renderLoadingText(GuiGraphics context) {

        context.drawCenteredString(
                textRenderer,
                Component.translatable("ardamaps.client.map.screen.loading"),
                camera.getViewportWidth() / 2,
                camera.getViewportHeight() / 2,
                ModConstants.COLOR_WHITE);
    }

    /**
     * Renders the fog of war overlay on top of the map layer, using a custom shader to combine the paper texture and fog mask.
     */
    protected void renderFogOfWar() {

        if (ArdaMapsClient.CONFIG.isMapRevealAll()) return;
        if (!FogOfWarShader.isLoaded()) return;
        if (exploration == null || exploration.getFogTextureId() == null) return;

        var pos = camera.worldToScreenCoordinates(new Vec2d(getDimension().getXMin(), getDimension().getZMin()));
        var renderHeight = camera.getWorldTextureHeight();
        var renderWidth = camera.getWorldTextureWidth();
        var screenX = pos.x();
        var screenY = pos.y();

        try {
            RenderSystem.setShader(FogOfWarShader::fogOfWar);

            // Calculate paper tiling (repeat every 256 pixels)
            float scaleX = renderWidth / 256.0f;
            float scaleY = renderHeight / 256.0f;
            float centerX = (float) ((camera.getWorldX() - getDimension().getXMin()) / (double) getDimension().getWidth());
            float centerY = (float) ((camera.getWorldZ() - getDimension().getZMin()) / (double) getDimension().getHeight());
            FogOfWarShader.setTextureScale(scaleX, scaleY);
            FogOfWarShader.setZoomCenter(centerX, centerY);

            var textureManager = Minecraft.getInstance().getTextureManager();

            // Activate texture unit 0 and bind paper texture
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            textureManager.bindForSetup(ModConstants.FOG_OF_WAR_TEXTURE);

            // Activate texture unit 1 and bind fog mask
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            textureManager.bindForSetup(exploration.getFogTextureId());

            // Use bilinear filtering so cell edges blur smoothly
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            buffer.vertex(screenX, screenY + renderHeight, 0).uv(0, 1).endVertex();
            buffer.vertex(screenX + renderWidth, screenY + renderHeight, 0).uv(1, 1).endVertex();
            buffer.vertex(screenX + renderWidth, screenY, 0).uv(1, 0).endVertex();
            buffer.vertex(screenX, screenY, 0).uv(0, 0).endVertex();

            // Draw directly
            BufferUploader.drawWithShader(buffer.end());

            RenderSystem.disableBlend();
        } finally {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        }
    }

    /**
     * Convenience accessor so subclasses do not need to dereference through the camera.
     *
     * @return The Dimension associated with this renderable's map layer.
     */
    protected Dimension getDimension() {
        return camera.getDimension();
    }
}
