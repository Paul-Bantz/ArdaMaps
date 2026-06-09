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

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.map.cameras.GridCamera;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A simple renderer that renders a simple grid and provides dimension data to the map screen.
 * Used as a placeholder when no layer is selected or for unsupported layer types.
 */
public class GridRenderer extends MapRenderable {

    /** Grid cell sizes (in blocks) to choose from; the smallest that gives ≥ MIN_CELL_PX on screen is picked. */
    private static final int[] GRID_STEPS = {
            1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096
    };

    /** Minimum on-screen cell size in pixels before we switch to a coarser grid step. */
    private static final int MIN_CELL_PX = 40;

    private static final int COLOR_GRID_LINE  = 0x60FFFFFF; // semi-transparent white
    private static final int COLOR_BORDER_LINE = 0xCCFFFFFF; // brighter for world border
    private static final int COLOR_BACKGROUND  = 0xFF1A1A2E; // dark blue background

    private final GridCamera camera;

    /**
     * Constructs a new GridRenderer.
     * The camera must already have {@link GridCamera#setDimension} called before this constructor
     * (guaranteed by MapScreen which builds the camera first).
     *
     * @param camera       The pre-built, dimension-aware camera for this renderer.
     * @param textRenderer The text renderer for loading / info text.
     */
    public GridRenderer(GridCamera camera, TextRenderer textRenderer) {
        super(camera, textRenderer);
        this.camera = camera;
    }

    @Override
    public void configure(MapLayerDefinition layer, double renderScale) {

        camera.setCameraZoomBounds(1, 14);
        camera.setPreferredRenderScale(renderScale);
        camera.setZoomToMatchVisualPixelsPerBlock();
    }

    @Override
    public void render(DrawContext context) {
        renderBackground(context);
        renderGrid(context);
        renderFogOfWar();
    }

    /** Fill the entire viewport with the background colour. */
    private void renderBackground(DrawContext context) {
        context.fill(0, 0, camera.getViewportWidth(), camera.getViewportHeight(), COLOR_BACKGROUND);
    }

    /**
     * Draw an adaptive grid whose cell size (in blocks) is the smallest entry in {@link #GRID_STEPS}
     * that produces cells of at least {@link #MIN_CELL_PX} pixels on screen.
     */
    private void renderGrid(DrawContext context) {

        double scale = camera.scale(); // pixels per block

        // Pick the grid step that gives cells >= MIN_CELL_PX on screen
        int step = GRID_STEPS[GRID_STEPS.length - 1];
        for (int s : GRID_STEPS) {
            if (s * scale >= MIN_CELL_PX) {
                step = s;
                break;
            }
        }

        // Compute visible world rectangle
        Vec2d topLeft     = camera.screenToWorldCoordinates(0, 0);
        Vec2d bottomRight = camera.screenToWorldCoordinates(camera.getViewportWidth(), camera.getViewportHeight());

        double worldLeft   = topLeft.x();
        double worldTop    = topLeft.y();
        double worldRight  = bottomRight.x();
        double worldBottom = bottomRight.y();

        // Clamp to dimension bounds so we only draw within the world
        int dimXMin = getDimension().getXMin();
        int dimXMax = getDimension().getXMax();
        int dimZMin = getDimension().getZMin();
        int dimZMax = getDimension().getZMax();

        double drawLeft   = Math.max(worldLeft,   dimXMin);
        double drawRight  = Math.min(worldRight,  dimXMax);
        double drawTop    = Math.max(worldTop,    dimZMin);
        double drawBottom = Math.min(worldBottom, dimZMax);

        if (drawLeft >= drawRight || drawTop >= drawBottom) return;

        // First grid line X position (aligned to step)
        long firstX = (long) Math.ceil(drawLeft  / step) * step;
        long firstZ = (long) Math.ceil(drawTop   / step) * step;

        // Vertical grid lines
        for (long wx = firstX; wx <= drawRight; wx += step) {
            Vec2d top    = camera.worldToScreenCoordinates(wx, drawTop);
            Vec2d bottom = camera.worldToScreenCoordinates(wx, drawBottom);
            int sx = (int) Math.round(top.x());
            int sy0 = (int) Math.round(top.y());
            int sy1 = (int) Math.round(bottom.y());
            int color = (wx == dimXMin || wx == dimXMax) ? COLOR_BORDER_LINE : COLOR_GRID_LINE;
            context.fill(sx, sy0, sx + 1, sy1, color);
        }

        // Horizontal grid lines
        for (long wz = firstZ; wz <= drawBottom; wz += step) {
            Vec2d left  = camera.worldToScreenCoordinates(drawLeft,  wz);
            Vec2d right = camera.worldToScreenCoordinates(drawRight, wz);
            int sy = (int) Math.round(left.y());
            int sx0 = (int) Math.round(left.x());
            int sx1 = (int) Math.round(right.x());
            int color = (wz == dimZMin || wz == dimZMax) ? COLOR_BORDER_LINE : COLOR_GRID_LINE;
            context.fill(sx0, sy, sx1, sy + 1, color);
        }
    }
}
