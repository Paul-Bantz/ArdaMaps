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
import lombok.Getter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

import java.util.List;

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
    public abstract void render(GuiGraphicsExtractor context);

    /**
     * Releases resources owned by this renderable.
     */
    public void close() {
        // Default renderables do not own closeable resources.
    }

    /**
     * Returns one debug line per tile currently loading for this renderable, used by the
     * map_debug_display "currently loading tiles" panel. Renderables with no asynchronous
     * tile provider (e.g. flat-image/grid layers) have nothing to report.
     *
     * @return The debug lines describing in-flight tiles, or an empty list if not applicable.
     */
    public List<String> getDebugLoadingLines() {
        return List.of();
    }

    /**
     * Renders a loading text centered in the viewport.
     *
     * @param context The DrawContext to render with.
     */
    protected void renderLoadingText(GuiGraphicsExtractor context) {

        context.centeredText(
                textRenderer,
                Component.translatable("ardamaps.client.map.screen.loading"),
                camera.getViewportWidth() / 2,
                camera.getViewportHeight() / 2,
                ModConstants.COLOR_WHITE);
    }

    /**
     * Renders the fog of war overlay on top of the map layer, using a custom shader to combine the paper texture and fog mask.
     */
    protected void renderFogOfWar(GuiGraphicsExtractor context) {

        if (ArdaMapsClient.CONFIG.isMapRevealAll()) return;
        if (exploration == null || exploration.getFogTextureId() == null) return;

        var pos = camera.worldToScreenCoordinates(new Vec2d(getDimension().getXMin(), getDimension().getZMin()));
        var renderHeight = camera.getWorldTextureHeight();
        var renderWidth = camera.getWorldTextureWidth();
        var screenX = pos.x();
        var screenY = pos.y();

        float scaleX = renderWidth / 256.0f;
        float scaleY = renderHeight / 256.0f;
        float centerX = (float) ((camera.getWorldX() - getDimension().getXMin()) / (double) getDimension().getWidth());
        float centerY = (float) ((camera.getWorldZ() - getDimension().getZMin()) / (double) getDimension().getHeight());

        float paperU0 = transformedPaperUv(0.0F, centerX, scaleX);
        float paperV0 = transformedPaperUv(0.0F, centerY, scaleY);
        float paperU1 = transformedPaperUv(1.0F, centerX, scaleX);
        float paperV1 = transformedPaperUv(1.0F, centerY, scaleY);

        GuiRenderStateAccess.add(context, new FogOfWarRenderState(
                ModConstants.FOG_OF_WAR_TEXTURE,
                exploration.getFogTextureId(),
                new Matrix3x2f(context.pose()),
                (float) screenX,
                (float) screenY,
                (float) (screenX + renderWidth),
                (float) (screenY + renderHeight),
                paperU0,
                paperV0,
                paperU1,
                paperV1,
                GuiRenderStateAccess.scissorArea(context)));
    }

    /**
     * Convenience accessor so subclasses do not need to dereference through the camera.
     *
     * @return The Dimension associated with this renderable's map layer.
     */
    protected Dimension getDimension() {
        return camera.getDimension();
    }

    private static float transformedPaperUv(float uv, float center, float scale) {

        return (uv - center) * scale + center;
    }
}
