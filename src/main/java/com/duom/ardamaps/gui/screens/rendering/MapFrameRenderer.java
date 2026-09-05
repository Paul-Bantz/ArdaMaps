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

import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Renderer for the frame around the map.
 * This is a convenience class to keep the map frame rendering logic separate from the main map rendering code.
 * <br/>- Suppresses warnings about fields that could be local variables, as these fields are used to cache values across
 * render calls and avoid redundant calculations.
 */
public class MapFrameRenderer {

    /** Inset from the drawn frame rect to the usable map area, in the sprite's own pixels. */
    private static final int FRAME_INSET = 8;

    /** Cached content area from the latest render pass, used for hit-testing. */
    private BackgroundRenderer.GuiLayout layout;

    /**
     * Render the world border frame around the Arda world bounds.
     * The frame is drawn at world-space edges (with a small margin).
     *
     * @param context The draw context
     * @param layout  The map frame layout being rendered.
     */
    public void render(GuiGraphicsExtractor context, BackgroundRenderer.GuiLayout layout) {

        if (layout == null) return;

        this.layout = layout;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, ModConstants.MAP_FRAME_SPRITE,
                layout.topLeftX() - FRAME_INSET, layout.topLeftY() - FRAME_INSET,
                layout.guiWidth() + FRAME_INSET * 2, layout.guiHeight() + FRAME_INSET * 2);
    }

    /**
     * Check if the given screen coordinates are within the map frame area, with an additional margin.
     * This can be used to check if coordinates are near the frame edges, for example when determining if the scale bar should be rendered.
     *
     * @param screenX x coordinate on the screen
     * @param screenY y coordinate on the screen
     * @param margin  additional margin from the frame edges to consider as "in frame"
     * @return true if the coordinates are within the frame area plus margin, false otherwise
     */
    public boolean coordinatesInFrame(double screenX, double screenY, double margin) {

        if (layout == null) return false;

        return screenX >= layout.topLeftX() + margin && screenX <= layout.topLeftX() + layout.guiWidth() - margin
                && screenY >= layout.topLeftY() + margin && screenY <= layout.topLeftY() + layout.guiHeight() - margin;
    }
}
