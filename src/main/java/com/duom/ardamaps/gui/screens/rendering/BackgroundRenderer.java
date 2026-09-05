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
 * Renders the two-page book GUI background from left and right nine-sliced sprites.
 * Each page's scaling geometry is declared in its PNG mcmeta sidecar.
 */
public class BackgroundRenderer {

    /** Aspect ratio of the entire book GUI (including margins) to maintain consistency across screen sizes. */
    private static final float GUI_RATIO = 16f / 9f;

    /** Margin between book area and screen edges */
    private static final int UI_MARGIN = 25;

    /** Padding from page horizontal edges to usable content area */
    private static final int CONTENT_PADDING_X = 42;

    /** Padding from page vertical edges to usable content area */
    private static final int CONTENT_PADDING_Y = 16;

    /** Total horizontal padding from left edge of left page to right edge of right page */
    private static final int INNER_PADDING_X = 2 * CONTENT_PADDING_X;

    /** Total vertical padding from top edge to bottom edge of page (including inner gutter) */
    private static final int INNER_PADDING_Y = 2 * CONTENT_PADDING_Y + 8;

    /** Cached screen height from last render, used to detect when to recalculate layout. */
    private int width = -1;

    /** Cached screen height from last render, used to detect when to recalculate layout. */
    private int height = -1;

    /** Top-left corner X of the entire book GUI (including margins) */
    private int guiTopLeftX;

    /** Top-left corner Y of the entire book GUI (including margins) */
    private int guiTopLeftY;

    /** Width of each page texture (including corners) - scaled and cached */
    private int pageWidth;

    /** Height of the book texture (including corners) - scaled and cached */
    private int pageHeight;

    /**
     * Renders the book GUI background, recalculating layout if screen dimensions have changed since last render.
     *
     * @param context      the DrawContext to render with, provided by the caller's render method
     * @param screenWidth  current screen width in pixels, used to detect when to recalculate layout
     * @param screenHeight current screen height in pixels, used to detect when to recalculate layout
     */
    public void render(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        invalidate(screenWidth, screenHeight);
        drawBookGui(context);
    }

    /**
     * Recalculates the layout of the GUI if the screen dimensions have changed since the last render.
     * Computes the largest 16:9 book area (including UI margins) that fits on screen, and caches the positions and sizes of each 9-slice piece for rendering.
     *
     * @param width  current screen width in pixels
     * @param height current screen height in pixels
     */
    public void invalidate(int width, int height) {

        // Exit early if dimensions haven't changed since last calculation
        if (width == this.width && height == this.height) return;

        this.width = width;
        this.height = height;

        // Compute the largest 16:9 book area (including UI margins) that fits on screen
        int bookW, bookH;
        if ((float) width / height >= GUI_RATIO) {
            // Height-constrained
            bookH = height;
            bookW = Math.round(height * GUI_RATIO);
        } else {
            // Width-constrained
            bookW = width;
            bookH = Math.round(width / GUI_RATIO);
        }

        pageHeight = bookH - 2 * UI_MARGIN;
        pageWidth = bookW / 2 - UI_MARGIN;

        // Centre the book on screen
        guiTopLeftX = (width - bookW) / 2 + UI_MARGIN;
        guiTopLeftY = (height - bookH) / 2 + UI_MARGIN;
    }

    /**
     * Renders the left and right book page sprites.
     *
     * @param context the DrawContext to render with, provided by the caller's render method
     */
    private void drawBookGui(GuiGraphicsExtractor context) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, ModConstants.BOOK_PAGE_LEFT_SPRITE,
                guiTopLeftX, guiTopLeftY, pageWidth, pageHeight);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, ModConstants.BOOK_PAGE_RIGHT_SPRITE,
                guiTopLeftX + pageWidth, guiTopLeftY, pageWidth, pageHeight);
    }

    /**
     * @return the usable content area of the GUI, excluding borders, as a GuiLayout record. Coordinates are in screen pixels.
     */
    public GuiLayout getGuiContentArea() {

        return getGuiContentArea(0);
    }

    /**
     * Returns the usable content area of the GUI, excluding borders, as a GuiLayout record. Coordinates are in screen pixels.
     *
     * @param extraPadding additional padding to apply on top of the default content padding
     * @return the usable content area of the GUI, excluding borders, as a GuiLayout record.
     */
    public GuiLayout getGuiContentArea(int extraPadding) {

        int contentX = guiTopLeftX + CONTENT_PADDING_X + extraPadding;
        int contentY = guiTopLeftY + CONTENT_PADDING_Y + extraPadding;
        int contentW = pageWidth * 2 - INNER_PADDING_X - extraPadding * 2;
        int contentH = pageHeight - INNER_PADDING_Y - extraPadding * 2;

        return new GuiLayout(contentX, contentY, contentW, contentH);
    }

    /**
     * Simple record class representing the usable content area of the GUI, excluding borders. Coordinates are in screen pixels.
     *
     * @param topLeftX  X coordinate of the top-left corner of the content area (inside the border)
     * @param topLeftY  Y coordinate of the top-left corner of the content area (inside the border)
     * @param guiWidth  Width of the content area spanning both pages (inside the border)
     * @param guiHeight Height of the content area (inside the border)
     */
    public record GuiLayout(int topLeftX, int topLeftY, int guiWidth, int guiHeight) {

    }
}
