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

import com.duom.ardamaps.gui.screens.ScreenRenderingUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * A plain simple text field widget that renders an input box on a semi opaque black background.
 */
public class SimpleTextFieldWidget extends TextFieldWidget {

    /** Extra vertical offset to compensate for {@code drawsBackground=false} */
    private final int verticalOffset;

    /** Extra horizontal offset to compensate for {@code drawsBackground=false} */
    private final int horizontalOffset;

    /**
     * Initializes a simple text field widget
     *
     * @param textRenderer the text renderer
     * @param x            the widget's x position
     * @param y            the widget's y position
     * @param width        the width
     * @param height       the height
     * @param text         the placeholder text
     */
    public SimpleTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {

        super(textRenderer, x, y, width, height, text);
        this.setDrawsBackground(false);
        this.verticalOffset = (height - textRenderer.fontHeight) / 2;
        this.horizontalOffset = 8;
    }

    /**
     * Renders an input box lined by two separators on top and bottom with an opaque background.
     *
     * @param context the draw context
     * @param mouseX  the mouse X position
     * @param mouseY  the mouse Y position
     * @param delta   the tick delta
     */
    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {

        var matrices = context.getMatrices();

        matrices.push();
        matrices.translate(horizontalOffset, verticalOffset, 0);
        super.renderButton(context, mouseX, mouseY, delta);
        matrices.pop();

        context.fill(getX() + 4, getY(), getX() + getWidth() - 4, getY() + getHeight(), 0x55000000);

        ScreenRenderingUtils.renderSeparator(context, this.width, getX(), getY() - 4, false);
        ScreenRenderingUtils.renderSeparator(context, this.width, getX(), getY() - 4 + getHeight(), false);
    }
}
