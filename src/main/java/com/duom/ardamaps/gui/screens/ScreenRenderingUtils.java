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

package com.duom.ardamaps.gui.screens;

import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.client.gui.DrawContext;

/**
 * Utility class for rendering operations in ArdaMaps screens.
 */
public class ScreenRenderingUtils {

    /**
     * Renders a separator line
     *
     * @param context the draw context
     * @param width   width of the separator
     * @param x       Starting horizontal position of the separator (left edge of the left end)
     * @param y       Vertical position of the separator
     * @return the separator height
     */
    public static int renderSeparator(DrawContext context, int width, int x, int y) {

        return renderSeparator(context, width, x, y, true);
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
    public static int renderSeparator(DrawContext context, int width, int x, int y, boolean displayCenterSeparator) {

        int separatorSize = 9;
        int xOffset = x;
        int lineWidth = (width / 2);

        if (displayCenterSeparator) {

            lineWidth -= separatorSize * 2;
        }

        // Left end
        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                xOffset, y,
                separatorSize, separatorSize,
                32, 224,
                18, 18,
                512, 512);

        // Left line
        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                xOffset += separatorSize, y,
                lineWidth, separatorSize,
                50, 224,
                1, 18,
                512, 512);

        if (displayCenterSeparator) {
            // Center
            context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                    xOffset += lineWidth, y,
                    separatorSize * 2, separatorSize,
                    142, 224,
                    36, 18,
                    512, 512);
        } else {
            xOffset += lineWidth - (separatorSize * 4);
        }

        // Right line
        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                xOffset += separatorSize * 2, y,
                lineWidth, separatorSize,
                50, 224,
                1, 18,
                512, 512);

        // Right end
        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                xOffset + lineWidth, y,
                separatorSize, separatorSize,
                270, 224,
                18, 18,
                512, 512);

        return separatorSize;
    }
}
