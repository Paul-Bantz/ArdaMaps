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

import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.gui.ModConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * A styled button widget that displays text.
 * This button type is meant to be displayed on top of the GUI.
 */
public class StyledButtonWidget extends ClickableWidget {

    /** On select runnable */
    private final Runnable onSelect;

    /** Toggled state of the button, if true the button will be rendered in a toggled state */
    @Getter
    @Setter
    private boolean toggled = false;

    /** Button style */
    private final Style style;

    /**
     * Button style type
     */
    public enum Style {
        DEFAULT,
        EDGE
    }

    /**
     * Creates a new Button.
     *
     * @param x       x screen position
     * @param y       y screen position
     * @param width   width
     * @param height  height
     * @param style   button style
     * @param onClick on click runnable
     */
    public StyledButtonWidget(int x,
                              int y,
                              int width,
                              int height,
                              Runnable onClick,
                              Style style,
                              Text text) {

        super(x, y, width, height, Text.empty());

        this.onSelect = onClick;
        this.width = width;
        this.height = height;
        this.style = style;
        this.setMessage(text != null ? text : Text.empty());
    }

    /**
     * Renders the button with a nine-sliced texture and centered text.
     * The button's appearance changes based on its toggled state and whether the mouse is hovering over it.
     *
     * @param context the DrawContext to render with
     * @param mouseX  the current x position of the mouse cursor
     * @param mouseY  the current y position of the mouse cursor
     * @param delta   the time delta since the last frame, used for animations (not utilized in this implementation)
     */
    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {

        if (!visible) return;

        if (style == Style.DEFAULT)
            renderDefaultStyle(context, mouseX, mouseY);
        else if (style == Style.EDGE)
            renderEdgeStyle(context, mouseX, mouseY);
    }

    /**
     * Renders the button as default style
     * @param context the draw context
     * @param mouseX the mouse x position
     * @param mouseY the mouse y position
     */
    private void renderDefaultStyle(DrawContext context, int mouseX, int mouseY) {

        var u = 16;
        var v = 16;
        var x = getX();
        var y = getY();

        if (toggled) v += 64;
        else if (isMouseOver(mouseX, mouseY)) v += 32;

        if (!active) {
            RenderSystem.setShaderColor(0.85f, 0.85f, 0.85f, 1f);
        }

        context.drawNineSlicedTexture(ModConstants.MAP_GUI_ELEMENTS,
                x, y,
                width, height,
                12,
                9,
                12,
                9,
                128,
                32,
                u, v);

        var text = getMessage();

        if (!text.equals(Text.empty())) {

            var textRenderer = Client.mc().textRenderer;
            var offsetX = width / 2 - textRenderer.getWidth(text) / 2;
            var offsetY = height / 2 - textRenderer.fontHeight / 2;

            context.drawText(textRenderer, text, x + offsetX, y + offsetY, toggled ? ModConstants.COLOR_LIGHT_BROWN : ModConstants.COLOR_DARK_BROWN, false);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Renders the button as edge style
     * @param context the draw context
     * @param mouseX the mouse x position
     * @param mouseY the mouse y position
     */
    private void renderEdgeStyle(DrawContext context, int mouseX, int mouseY) {

        var x = getX();
        var y = getY();

        var text = getMessage();
        var mouseOver = isMouseOver(mouseX, mouseY);

        if (isMouseOver(mouseX, mouseY))
            RenderSystem.setShaderColor(1f, 1f, 1f, .5f);

        if (toggled || mouseOver) {

            var indicatorHeight = getHeight();
            var indicatorWidth = indicatorHeight * 14 / 64;
            context.fill(x, y, x + getWidth(), y + getHeight(), ModConstants.COLOR_LIGHT_BROWN);
            context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                    x + getWidth() - indicatorWidth, y,
                    indicatorWidth, indicatorHeight,
                    32, 242,
                    14, 64,
                    512, 512);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        if (!text.equals(Text.empty())) {

            var textRenderer = Client.mc().textRenderer;
            var offsetX = width / 2 - textRenderer.getWidth(text) / 2;
            var offsetY = height / 2 - textRenderer.fontHeight / 2;

            context.drawText(textRenderer, text, x + offsetX, y + offsetY, ModConstants.COLOR_DARK_BROWN, false);
        }
    }

    /**
     * Sets the pixel width of this button. Call before rendering to resize the hit-box
     * and visual in the same frame.
     *
     * @param width new width in pixels
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Sets the pixel height of this button. Call before rendering to resize the hit-box
     * and visual in the same frame.
     *
     * @param height new height in pixels
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Handles mouse click events on the button, executing the onSelect runnable and then calling the superclass's onClick method to handle any additional behaviour.
     *
     * @param mouseX the x position of the mouse cursor at the time of the click
     * @param mouseY the y position of the mouse cursor at the time of the click
     */
    @Override
    public void onClick(double mouseX, double mouseY) {

        onSelect.run();
        super.onClick(mouseX, mouseY);
    }

    /**
     * Appends narration messages for accessibility, using the default narration provided by the superclass.
     *
     * @param builder the NarrationMessageBuilder to append messages to
     */
    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
