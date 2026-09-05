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

package com.duom.ardamaps.gui.screens.rendering;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.core.data.map.region.RegionShape;
import com.duom.ardamaps.gui.ModConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Renders the HUD overlays drawn on top of the map: coordinate readout, region-name tooltip,
 * loading-tiles debug panel, and the no-map placeholder. Owns the dirty-tracking caches for
 * coordinates and region lookups so the main screen does not have to carry that state.
 */
@Environment(EnvType.CLIENT)
public class MapOverlayRenderer {

    /** Ardacraft logo display size used for the placeholder screen. */
    private static final int ARDACRAFT_LOGO_SIZE = 64;

    /** Half the Ardacraft logo size, used to centre it on screen. */
    private static final int ARDACRAFT_LOGO_HALF_SIZE = ARDACRAFT_LOGO_SIZE / 2;

    /** Margin above the bottom edge of the map frame for the coordinate label. */
    private static final int COORDINATES_LABEL_MARGIN = 4;

    /** Cached text for unknown region tooltip to avoid repeated translations and allocations. */
    private final String unknownRegionText = Component.translatable("ardamaps.client.map.screen.unknown.region").getString();

    /** Last world X coordinate used for the region lookup; avoids redundant point tests. */
    private int lastRegionWorldX = Integer.MIN_VALUE;

    /** Last world Z coordinate used for the region lookup; avoids redundant point tests. */
    private int lastRegionWorldZ = Integer.MIN_VALUE;

    /** Animated label for the region name under the mouse cursor. */
    private final AnimatedRegionLabel regionLabel = new AnimatedRegionLabel();

    /** Last world X coordinate emitted to the coordinate label. */
    private int lastCoordinatesX = Integer.MIN_VALUE;

    /** Last world Z coordinate emitted to the coordinate label. */
    private int lastCoordinatesZ = Integer.MIN_VALUE;

    /** Cached coordinate readout drawn on the bottom edge of the map frame. */
    private Component coordinatesLabel = Component.empty();

    /**
     * Updates both dirty caches (coordinates and region lookup) for the current frame.
     * Call this once per frame before calling the render methods.
     *
     * @param mouseX      The current mouse x position in screen space.
     * @param mouseY      The current mouse y position in screen space.
     * @param camera      The active map camera, or {@code null} when no map is loaded.
     * @param exploration The current player exploration state, or {@code null} when unavailable.
     * @param frame       The frame renderer used for hit-testing.
     * @param padding     The map-frame padding in pixels.
     */
    public void update(int mouseX, int mouseY, @Nullable MapCamera camera,
                       @Nullable PlayerExploration exploration, MapFrameRenderer frame, int padding) {

        updateCoordinates(mouseX, mouseY, camera, frame, padding);
        updateRegionUnderMouse(mouseX, mouseY, camera, exploration, frame, padding);
    }

    /**
     * Updates the cached coordinate label from the mouse position (or player position when outside the frame).
     *
     * @param mouseX  The current mouse x position.
     * @param mouseY  The current mouse y position.
     * @param camera  The active map camera, or {@code null} when no map is loaded.
     * @param frame   The frame renderer used for hit-testing.
     * @param padding The map-frame padding in pixels.
     */
    private void updateCoordinates(int mouseX, int mouseY, @Nullable MapCamera camera,
                                   MapFrameRenderer frame, int padding) {

        if (camera == null) return;

        Vec2d worldCoordinates = Client.playerPosition2d();

        if (frame.coordinatesInFrame(mouseX, mouseY, padding))
            worldCoordinates = camera.screenToWorldCoordinates(mouseX, mouseY);

        int x = (int) Math.floor(worldCoordinates.x());
        int z = (int) Math.floor(worldCoordinates.y());
        if (x == lastCoordinatesX && z == lastCoordinatesZ) return;

        lastCoordinatesX = x;
        lastCoordinatesZ = z;
        coordinatesLabel = Component.literal(String.format("X:%d, Z:%d", x, z));
    }

    /**
     * Updates the cached region name under the mouse. Uses integer world-coordinate dirty-tracking
     * so exact polygon tests are skipped while the mouse remains within the same block.
     *
     * @param mouseX      The current mouse x position.
     * @param mouseY      The current mouse y position.
     * @param camera      The active map camera, or {@code null} when no map is loaded.
     * @param exploration The current player exploration state, or {@code null} when unavailable.
     * @param frame       The frame renderer used for hit-testing.
     * @param padding     The map-frame padding in pixels.
     */
    private void updateRegionUnderMouse(int mouseX, int mouseY, @Nullable MapCamera camera,
                                        @Nullable PlayerExploration exploration,
                                        MapFrameRenderer frame, int padding) {

        if (camera == null || !frame.coordinatesInFrame(mouseX, mouseY, padding)) {

            setRegionNames(null, null);
            lastRegionWorldX = Integer.MIN_VALUE;
            lastRegionWorldZ = Integer.MIN_VALUE;
            return;
        }

        var index = ArdaMapsClient.getRegionSpatialIndex(camera.getDimension().getId());
        if (index == null) {

            setRegionNames(null, null);
            return;
        }

        Vec2d world = camera.screenToWorldCoordinates(mouseX, mouseY);

        if (exploration != null) {

            var mouseOverExploration = exploration.stateAtWorldPos(world.x(), world.y());

            if (mouseOverExploration.ordinal() < ExplorationState.VISIBLE.ordinal() && !ArdaMapsClient.CONFIG.isMapRevealAll()) {

                setRegionNames(unknownRegionText, null);
                lastRegionWorldX = Integer.MIN_VALUE;
                lastRegionWorldZ = Integer.MIN_VALUE;
                return;
            }
        }

        int worldX = (int) Math.floor(world.x());
        int worldZ = (int) Math.floor(world.y());

        if (worldX == lastRegionWorldX && worldZ == lastRegionWorldZ) return;

        lastRegionWorldX = worldX;
        lastRegionWorldZ = worldZ;
        RegionShape leaf = index.getRegionAt(world.x(), world.y());
        if (leaf == null) {
            setRegionNames(null, null);
            return;
        }

        RegionShape root = index.rootOf(leaf);
        setRegionNames(root.name(), root == leaf ? null : leaf.name());
    }

    /**
     * Updates the top-level and subregion hover labels together.
     *
     * @param topName The top-level region name, or null to hide it.
     * @param subName The subregion name, or null to hide it.
     */
    private void setRegionNames(@Nullable String topName, @Nullable String subName) {

        regionLabel.setRegion(topName, subName);
    }

    /**
     * Renders the coordinate label centred at the bottom edge of the content area.
     *
     * @param context     The draw context.
     * @param font        The font renderer.
     * @param contentArea The padded content area of the map.
     */
    public void renderCoordinates(GuiGraphicsExtractor context, Font font, BackgroundRenderer.GuiLayout contentArea) {

        int labelX = contentArea.topLeftX() + contentArea.guiWidth() / 2 - font.width(coordinatesLabel) / 2;
        int labelY = contentArea.topLeftY() + contentArea.guiHeight() - font.lineHeight - COORDINATES_LABEL_MARGIN;
        context.text(font, coordinatesLabel, labelX, labelY, ModConstants.COLOR_WHITE, true);
    }

    /**
     * Renders top-level and subregion hover labels at the top-centre of the content area.
     *
     * @param context     The draw context.
     * @param font        The font renderer.
     * @param contentArea The padded content area of the map.
     */
    public void renderRegionName(GuiGraphicsExtractor context, Font font, BackgroundRenderer.GuiLayout contentArea) {

        int centerX = contentArea.topLeftX() + contentArea.guiWidth() / 2;
        int topY = contentArea.topLeftY() - regionLabel.height(font) / 2;
        regionLabel.render(context, font, centerX, topY);
    }

    /**
     * Renders the Ardacraft logo and a "no map selected" message as a placeholder when no map is loaded.
     *
     * @param context The draw context.
     * @param font    The font renderer.
     * @param width   The screen width.
     * @param height  The screen height.
     */
    public void renderPlaceholder(GuiGraphicsExtractor context, Font font, int width, int height) {

        var centerX = width / 2;
        var centerY = height / 2;

        context.blit(RenderPipelines.GUI_TEXTURED, ModConstants.ARDACRAFT_LOGO,
                centerX - ARDACRAFT_LOGO_HALF_SIZE,
                centerY - ARDACRAFT_LOGO_HALF_SIZE,
                0, 0,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE);

        context.centeredText(
                font,
                Component.translatable("ardamaps.client.map.screen.no.map.selected"),
                centerX,
                centerY + ARDACRAFT_LOGO_HALF_SIZE + font.lineHeight,
                ModConstants.COLOR_WHITE);
    }

    /**
     * Renders the tile-loading debug panel anchored to the bottom-left of the screen.
     * Only called when the vanilla debug overlay is visible.
     *
     * @param context      The draw context.
     * @param font         The font renderer.
     * @param screenHeight The full screen height.
     * @param lines        The sorted list of currently loading tile identifiers.
     */
    public void renderDebugPanel(GuiGraphicsExtractor context, Font font, int screenHeight, List<String> lines) {

        int x = 4;
        int lineHeight = font.lineHeight + 1;
        int headerY = screenHeight - 4 - font.lineHeight;
        int maxLines = Math.max(0, (screenHeight - 8) / lineHeight - 1);

        context.text(font, "Currently loading " + lines.size() + " tiles", x, headerY, ModConstants.COLOR_WHITE, true);

        int renderedLines = Math.min(lines.size(), maxLines);
        for (int i = 0; i < renderedLines; i++) {

            String line = lines.get(i);
            int y = headerY - lineHeight * (i + 1);
            context.text(font, line, x, y, ModConstants.COLOR_WHITE, true);
        }
    }
}
