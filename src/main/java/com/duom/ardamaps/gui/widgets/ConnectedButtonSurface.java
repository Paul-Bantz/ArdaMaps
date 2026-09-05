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

package com.duom.ardamaps.gui.widgets;

import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws vanilla button backgrounds as visually connected vertical stacks.
 */
public final class ConnectedButtonSurface {

    /** Vanilla button texture, blitted raw so per-edge slices can be overridden. */
    private static final Identifier BUTTON = Identifier.withDefaultNamespace("textures/gui/sprites/widget/button.png");

    /** Vanilla highlighted button texture, blitted raw so per-edge slices can be overridden. */
    private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("textures/gui/sprites/widget/button_highlighted.png");

    /** Source texture width of vanilla button sprites. */
    private static final int TEX_W = 200;

    /** Source texture height of vanilla button sprites. */
    private static final int TEX_H = 20;

    /** Uniform nine-slice border size of vanilla button sprites. */
    private static final int BORDER = 3;

    /** Horizontal inset between a connected button's edge and its label. */
    public static final int LABEL_PADDING = 8;

    private ConnectedButtonSurface() {

    }

    /**
     * Draws one button row with independently controlled top and bottom edges.
     *
     * @param context        The GUI draw context.
     * @param x              The destination x coordinate.
     * @param y              The destination y coordinate.
     * @param width          The destination width.
     * @param height         The destination height.
     * @param drawTopEdge    True to draw the top border edge.
     * @param drawBottomEdge True to draw the bottom border edge.
     * @param highlighted    True to use the highlighted vanilla button texture.
     */
    public static void draw(GuiGraphicsExtractor context, int x, int y, int width, int height,
                            boolean drawTopEdge, boolean drawBottomEdge, boolean highlighted) {

        int v = drawTopEdge ? 0 : BORDER;
        int regionHeight = TEX_H - (drawTopEdge ? 0 : BORDER) - (drawBottomEdge ? 0 : BORDER);

        GuiTextures.blitNineSlicedStretched(context, highlighted ? BUTTON_HIGHLIGHTED : BUTTON,
                x, y, width, height,
                BORDER, drawTopEdge ? BORDER : 0, BORDER, drawBottomEdge ? BORDER : 0,
                TEX_W, regionHeight, 0, v, TEX_W, TEX_H, ModConstants.COLOR_WHITE);
    }

    /**
     * Draws one row in a connected list.
     *
     * @param context     The GUI draw context.
     * @param x           The destination x coordinate.
     * @param y           The destination y coordinate.
     * @param width       The destination width.
     * @param height      The destination height.
     * @param index       The zero-based row index.
     * @param count       The total row count.
     * @param highlighted True to use the highlighted vanilla button texture.
     */
    public static void drawInList(GuiGraphicsExtractor context, int x, int y, int width, int height,
                                  int index, int count, boolean highlighted) {

        draw(context, x, y, width, height, index == 0, index == count - 1, highlighted);
    }
}
