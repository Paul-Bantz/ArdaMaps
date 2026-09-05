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

package com.duom.ardamaps.gui.widgets.popup;

import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Shared renderer for popup-style paper surfaces.
 */
public final class PopupSurface {

    private PopupSurface() {

    }

    /**
     * Draws the default popup background.
     *
     * @param context The GUI draw context.
     * @param x       The destination x coordinate.
     * @param y       The destination y coordinate.
     * @param width   The destination width.
     * @param height  The destination height.
     */
    public static void drawBackground(GuiGraphicsExtractor context, int x, int y, int width, int height) {

        drawSurface(context, ModConstants.PAPER_SPRITE, x, y, width, height);
    }

    /**
     * Draws a popup paper surface using shared nine-slice geometry.
     *
     * @param context The GUI draw context.
     * @param sprite  The surface sprite identifier.
     * @param x       The destination x coordinate.
     * @param y       The destination y coordinate.
     * @param width   The destination width.
     * @param height  The destination height.
     */
    private static void drawSurface(GuiGraphicsExtractor context, Identifier sprite, int x, int y, int width, int height) {

        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
    }
}
