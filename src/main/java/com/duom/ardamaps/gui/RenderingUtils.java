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

package com.duom.ardamaps.gui;

import com.duom.ardamaps.core.Client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * Utility class for rendering operations in ArdaMaps.
 */
public final class RenderingUtils {

    /** Source texture height of the ornamented separator. */
    private static final int SEPARATOR_TEX_HEIGHT = 40;

    /** Minimum source texture width for a full-height separator without optional flourishes. */
    private static final int SEPARATOR_MIN_TEX_WIDTH = 396;

    /**
     * Utility class.
     */
    private RenderingUtils() {

    }

    /**
     * Snaps a GUI-space coordinate to the physical pixel grid. This approximates the true device mapping when the
     * window size is not divisible by GUI scale because Minecraft rounds scaled dimensions up.
     *
     * @param guiCoordinate The unrounded GUI-space coordinate.
     * @return The coordinate rounded to the nearest real screen pixel.
     */
    public static float toDevicePixel(float guiCoordinate) {

        int guiScale = Client.getGuiScale();
        return guiScale <= 1
                ? Math.round(guiCoordinate)
                : Math.round(guiCoordinate * guiScale) / (float) guiScale;
    }

    /**
     * Renders a separator line
     *
     * @param context the draw context
     * @param width   width of the separator
     * @param x       Starting horizontal position of the separator (left edge of the left end)
     * @param y       Vertical position of the separator
     * @return the separator height
     */
    public static int renderSeparator(GuiGraphicsExtractor context, int width, int x, int y) {

        int height = ModConstants.SEPARATOR_HEIGHT;
        int minWidth = Math.round(SEPARATOR_MIN_TEX_WIDTH * height / (float) SEPARATOR_TEX_HEIGHT);
        if (width < minWidth) {
            height = Math.max(1, Math.round(width * (float) SEPARATOR_TEX_HEIGHT / SEPARATOR_MIN_TEX_WIDTH));
        }

        GuiTextures.blitSeparator(context, x, y, width, height, ModConstants.COLOR_WHITE);

        return height;
    }

    /**
     * Renders a large screen heading.
     *
     * @param context The draw context.
     * @param font    The font renderer.
     * @param text    The text to render.
     * @param x       The text x coordinate.
     * @param y       The text y coordinate.
     * @param color   The text color.
     * @return The rendered heading height.
     */
    public static int renderH1(GuiGraphicsExtractor context, Font font, String text, int x, int y, int color) {

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(ModConstants.H1_TEXT_SCALE, ModConstants.H1_TEXT_SCALE);
        context.text(font, text, 0, 0, color, false);
        context.pose().popMatrix();

        return (int) (font.lineHeight * ModConstants.H1_TEXT_SCALE);
    }

    /**
     * Renders a large screen heading.
     *
     * @param context The draw context.
     * @param font    The font renderer.
     * @param text    The text to render.
     * @param x       The text x coordinate.
     * @param y       The text y coordinate.
     * @param color   The text color.
     * @return The rendered heading height.
     */
    public static int renderH1(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int color) {

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(ModConstants.H1_TEXT_SCALE, ModConstants.H1_TEXT_SCALE);
        context.text(font, text, 0, 0, color, false);
        context.pose().popMatrix();

        return (int) (font.lineHeight * ModConstants.H1_TEXT_SCALE);
    }

    /**
     * Renders a centred large screen heading.
     *
     * @param context   The draw context.
     * @param font      The font renderer.
     * @param title     The title text to render.
     * @param x         The left edge of the page.
     * @param y         The title y coordinate.
     * @param pageWidth The available page width.
     * @param color     The text color.
     * @return The rendered heading height.
     */
    public static int renderCenteredH1(GuiGraphicsExtractor context, Font font, Component title,
                                       int x, int y, int pageWidth, int color) {

        int xOffset = (int) (pageWidth / 2f - font.width(title) * ModConstants.H1_TEXT_SCALE / 2f);

        return renderH1(context, font, title, x + xOffset, y, color);
    }

    /**
     * Calculates the width of a rendered key-cap.
     *
     * @param font  The font renderer.
     * @param label The key label.
     * @return The key-cap width.
     */
    public static int keycapWidth(Font font, String label) {

        return Math.max(font.width(label), ModConstants.MIN_KEYBIND_SLOT_PX) + ModConstants.COMMAND_PADDING * 4;
    }

    /**
     * Renders a key-cap matching inline guide keybind runs.
     *
     * @param context The draw context.
     * @param font    The font renderer.
     * @param label   The key label.
     * @param x       The key-cap x coordinate.
     * @param y       The key-cap y coordinate.
     * @param tint    The tint to apply to the key-cap texture.
     */
    public static void renderKeycap(GuiGraphicsExtractor context, Font font, String label, int x, int y, int tint) {

        int faceWidth = keycapWidth(font, label);

        context.blitSprite(RenderPipelines.GUI_TEXTURED, ModConstants.KEYCAP_SPRITE,
                x, y, faceWidth, ModConstants.KEYCAP_HEIGHT, tint);

        int labelX = x + (faceWidth - font.width(label)) / 2;
        int labelY = y + font.lineHeight / 2;
        int labelColor = (tint & 0xFF000000) | (ModConstants.KEYBIND_LABEL_COLOR & 0x00FFFFFF);
        context.text(font, label, labelX, labelY, labelColor, false);
    }

}
