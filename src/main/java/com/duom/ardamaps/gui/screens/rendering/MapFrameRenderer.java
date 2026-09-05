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

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.gui.ModConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;

/**
 * Renderer for the frame around the map.
 * This is a convenience class to keep the map frame rendering logic separate from the main map rendering code.
 * <br/>- Suppresses warnings about fields that could be local variables, as these fields are used to cache values across
 * render calls and avoid redundant calculations.
 */
public class MapFrameRenderer {

    /** Map frame texture layout constants */
    private static final int FRAME_TEXTURE_SIZE = 512;

    /** corner square size */
    private static final int FRAME_CORNER_SIZE = 32;

    private static final float GUI_SCALE = .25f;

    /** short edge of the frame bars (width for vertical bars, height for horizontal bars) */
    private static final int FRAME_BAR_SHORT_EDGE = FRAME_CORNER_SIZE;

    /** long edge of the frame bars (height for vertical bars, width for horizontal bars) */
    private static final int FRAME_BAR_LONG_EDGE = 384;

    /** number of pixels to overlap frame segments to avoid gaps due to rounding when scaling and positioning the frame bars */
    private static final int PIXEL_OVERLAP = 2;

    /** inner padding from the frame to the map area, in scaled pixels. This ensures the frame does not overlap the map area and provides a consistent margin around the map regardless of zoom level. This value is scaled by GUI_SCALE to maintain consistent spacing at different zoom levels. */
    private static final int SCALED_FRAME_INNER_PADDING = (int) (25 * GUI_SCALE);

    /** Cached scaled dimensions for rendering, updated when zoom changes to avoid redundant calculations during rendering */
    private int scaledCornerSize;

    /** The size of the frame texture . This is used to calculate UV coordinates when rendering the frame segments. This value is updated in updateIfDirty() when the layout changes. */
    private int scaledFrameTextureSize;

    /** The short edge of the frame bars. This value is updated in updateIfDirty() when the layout changes. */
    private int scaledFrameBarShortEdge;

    /** The long edge of the frame bars. This value is updated in updateIfDirty() when the layout changes. */
    private int scaledFrameBarLongEdge;

    /** Cached screen-space top left coordinate */
    private Vec2d topLeft = new Vec2d(0, 0);

    /** Cached screen-space top right coordinate */
    private Vec2d topRight = new Vec2d(0, 0);

    /** Cached screen-space bottom left coordinate */
    private Vec2d bottomLeft = new Vec2d(0, 0);

    /** Cached screen-space bottom right coordinate */
    private Vec2d bottomRight = new Vec2d(0, 0);

    private BackgroundRenderer.GuiLayout cachedLayout;

    /**
     * Render the world border frame around the Arda world bounds.
     * The frame is drawn at world-space edges (with a small margin).
     *
     * @param context The draw context
     */
    public void render(DrawContext context, BackgroundRenderer.GuiLayout layout) {

        updateIfDirty(layout);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        drawMapFrameTopEdge(context, topLeft, topRight);
        drawMapFrameBottomEdge(context, bottomLeft, bottomRight);
        drawMapFrameLeftEdge(context, topLeft, bottomLeft);
        drawMapFrameRightEdge(context, topRight, bottomRight);

        drawMapCorners(context, topLeft, topRight, bottomRight, bottomLeft);

        RenderSystem.disableBlend();
    }

    /**
     * Update cached values if the camera zoom or viewport dimensions have changed.
     * This avoids redundant calculations during rendering and ensures the frame scales correctly.
     * Corner position is always updated to account for view panning
     */
    private void updateIfDirty(BackgroundRenderer.GuiLayout layout) {

        if (layout == null) return;

        // Update cached dimensions if they have changed
        if (!layout.equals(cachedLayout)) {

            cachedLayout = layout;

            int x = layout.topLeftX() + layout.guiWidth() + SCALED_FRAME_INNER_PADDING;
            int y = layout.topLeftY() + layout.guiHeight() + SCALED_FRAME_INNER_PADDING;

            // Compute screen-space corners of the frame
            topLeft = new Vec2d(layout.topLeftX() - SCALED_FRAME_INNER_PADDING, layout.topLeftY() - SCALED_FRAME_INNER_PADDING);
            topRight = new Vec2d(x, layout.topLeftY() - SCALED_FRAME_INNER_PADDING);
            bottomLeft = new Vec2d(layout.topLeftX() - SCALED_FRAME_INNER_PADDING, y);
            bottomRight = new Vec2d(x, y);

            scaledCornerSize = (int) Math.ceil(FRAME_CORNER_SIZE * GUI_SCALE);
            scaledFrameTextureSize = (int) Math.ceil(FRAME_TEXTURE_SIZE * GUI_SCALE);
            scaledFrameBarShortEdge = (int) Math.ceil(FRAME_BAR_SHORT_EDGE * GUI_SCALE);
            scaledFrameBarLongEdge = (int) Math.ceil(FRAME_BAR_LONG_EDGE * GUI_SCALE);
        }
    }

    /**
     * Draw the top edge of the map frame. This is drawn separately from the bottom edge to ensure it is not clipped
     * by the top viewport edge and remains visible when the camera is panned to the top of the world.
     *
     * @param context  the draw context
     * @param topLeft  the screen coordinates of the top-left corner of the frame
     * @param topRight the screen coordinates of the top-right corner of the frame
     */
    private void drawMapFrameTopEdge(DrawContext context, Vec2d topLeft, Vec2d topRight) {

        int x = (int) topLeft.x() + scaledCornerSize - PIXEL_OVERLAP;
        int endX = (int) (topRight.x() - scaledCornerSize + PIXEL_OVERLAP);
        int y = (int) topLeft.y();

        var edgeU = scaledCornerSize;
        var edgeV = 0;

        while (x < endX) {

            int segW = scaledFrameBarLongEdge;
            if (x + segW > endX) segW = endX - x;

            context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                    x, y,
                    edgeU, edgeV,
                    segW, scaledFrameBarShortEdge,
                    scaledFrameTextureSize, scaledFrameTextureSize);

            x += scaledFrameBarLongEdge;
        }
    }

    /**
     * Draw the bottom edge of the map frame. This is drawn separately from the top edge to ensure it is not clipped
     * by the bottom viewport edge and remains visible when the camera is panned to the bottom of the world.
     *
     * @param context     the draw context
     * @param bottomLeft  the screen coordinates of the bottom-left corner of the frame
     * @param bottomRight the screen coordinates of the bottom-right corner of the frame
     */
    private void drawMapFrameBottomEdge(DrawContext context, Vec2d bottomLeft, Vec2d bottomRight) {

        int x = (int) bottomLeft.x() + scaledCornerSize - PIXEL_OVERLAP;
        int endX = (int) (bottomRight.x() - scaledCornerSize + PIXEL_OVERLAP);
        int y = (int) bottomLeft.y() - scaledCornerSize;

        var edgeU = scaledCornerSize;
        var edgeV = scaledFrameTextureSize - scaledCornerSize;

        while (x < endX) {

            int segW = scaledFrameBarLongEdge;
            if (x + segW > endX) segW = endX - x;

            context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                    x, y,
                    edgeU, edgeV,
                    segW, scaledFrameBarShortEdge,
                    scaledFrameTextureSize, scaledFrameTextureSize);

            x += scaledFrameBarLongEdge;
        }
    }

    /**
     * Draw the left edge of the map frame. This is drawn separately from the right edge to ensure it is not clipped
     * by the left viewport edge and remains visible when the camera is panned to the left of the world.
     *
     * @param context    the draw context
     * @param topLeft    the screen coordinates of the top-left corner of the frame
     * @param bottomLeft the screen coordinates of the bottom-left corner of the frame
     */
    private void drawMapFrameLeftEdge(DrawContext context, Vec2d topLeft, Vec2d bottomLeft) {

        int x = (int) topLeft.x();
        int y = (int) topLeft.y() + scaledCornerSize - PIXEL_OVERLAP;
        int endY = (int) bottomLeft.y() - scaledCornerSize + PIXEL_OVERLAP;

        var edgeU = 0;
        var edgeV = scaledCornerSize;

        while (y < endY) {

            int segH = scaledFrameBarLongEdge;
            if (y + segH > endY) segH = endY - y;

            context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                    x, y,
                    edgeU, edgeV,
                    scaledFrameBarShortEdge, segH,
                    scaledFrameTextureSize, scaledFrameTextureSize);

            y += scaledFrameBarLongEdge;
        }
    }

    /**
     * Draw the right edge of the map frame. This is drawn separately from the left edge to ensure it is not clipped
     * by the right viewport edge and remains visible when the camera is panned to the right of the world.
     *
     * @param context     the draw context
     * @param topRight    the screen coordinates of the top-right corner of the frame
     * @param bottomRight the screen coordinates of the bottom-right corner of the frame
     */
    private void drawMapFrameRightEdge(DrawContext context, Vec2d topRight, Vec2d bottomRight) {

        int x = (int) topRight.x() - scaledCornerSize;
        int y = (int) topRight.y() + scaledCornerSize - PIXEL_OVERLAP;
        int endY = (int) bottomRight.y() - scaledCornerSize + PIXEL_OVERLAP;

        var edgeU = scaledFrameTextureSize - scaledCornerSize;
        var edgeV = scaledCornerSize;

        while (y < endY) {

            int segmentHeight = scaledFrameBarLongEdge;
            if (y + segmentHeight > endY) segmentHeight = endY - y;

            context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                    x, y,
                    edgeU, edgeV,
                    scaledFrameBarShortEdge, segmentHeight,
                    scaledFrameTextureSize, scaledFrameTextureSize);

            y += scaledFrameBarLongEdge;
        }
    }

    /**
     * Draw the corners of the map frame. Corners are drawn separately to ensure they are always visible and not clipped by viewport edges.
     *
     * @param context     the draw context
     * @param topLeft     the screen coordinates of the top-left corner of the frame
     * @param topRight    the screen coordinates of the top-right corner of the frame
     * @param bottomRight the screen coordinates of the bottom-right corner of the frame
     * @param bottomLeft  the screen coordinates of the bottom-left corner of the frame
     */
    private void drawMapCorners(DrawContext context, Vec2d topLeft, Vec2d topRight, Vec2d bottomRight, Vec2d bottomLeft) {

        var cornerUv = scaledFrameTextureSize - scaledCornerSize;

        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                (int) (topLeft.x()), (int) (topLeft.y()),
                0, 0,
                scaledCornerSize, scaledCornerSize,
                scaledFrameTextureSize, scaledFrameTextureSize);

        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                (int) (topRight.x() - scaledCornerSize), (int) (topRight.y()),
                cornerUv, 0,
                scaledCornerSize, scaledCornerSize,
                scaledFrameTextureSize, scaledFrameTextureSize);

        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                (int) (bottomRight.x() - scaledCornerSize), (int) (bottomRight.y() - scaledCornerSize),
                cornerUv, cornerUv,
                scaledCornerSize, scaledCornerSize,
                scaledFrameTextureSize, scaledFrameTextureSize);

        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                (int) (bottomLeft.x()), (int) (bottomLeft.y() - scaledCornerSize),
                0, cornerUv,
                scaledCornerSize, scaledCornerSize,
                scaledFrameTextureSize, scaledFrameTextureSize);
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

        return screenX >= topLeft.x() + margin && screenX <= topRight.x() - margin
                && screenY >= topLeft.y() + margin && screenY <= bottomRight.y() - margin;
    }
}
