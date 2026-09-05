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

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Vanilla edit box with extra horizontal text padding inside the default border.
 */
public class PaddedEditBox extends EditBox {

    /** Extra inset beyond vanilla's border inset. */
    private static final int EXTRA_TEXT_PADDING = 4;

    /** Vanilla inset applied on each side of a bordered edit box. */
    private static final int VANILLA_BORDER_INSET = 4;

    /** Vanilla text field sprites recreated because EditBox keeps its copy private. */
    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("widget/text_field"),
            Identifier.withDefaultNamespace("widget/text_field_highlighted"));

    /** Whether the delegated render pass should skip its own border. */
    private boolean suppressBorder;

    /**
     * Creates a padded edit box.
     *
     * @param font    font used to render text
     * @param x       left x position
     * @param y       top y position
     * @param width   widget width
     * @param height  widget height
     * @param message narration message
     */
    public PaddedEditBox(Font font, int x, int y, int width, int height, Component message) {

        super(font, x, y, width, height, message);
    }

    /**
     * Returns whether the vanilla edit box border should be rendered by the delegated pass.
     *
     * @return true when the delegated pass should render as bordered
     */
    @Override
    public boolean isBordered() {

        return !suppressBorder && super.isBordered();
    }

    /**
     * Returns the available text width after both vanilla and extra padding.
     *
     * @return inner text width
     */
    @Override
    public int getInnerWidth() {

        return getWidth() - 2 * (VANILLA_BORDER_INSET + EXTRA_TEXT_PADDING);
    }

    /**
     * Converts a text index to the padded screen x position.
     *
     * @param index text index
     * @return padded screen x position
     */
    @Override
    public int getScreenX(int index) {

        return super.getScreenX(index) + EXTRA_TEXT_PADDING;
    }

    /**
     * Extracts render state with the border kept at the full widget bounds.
     *
     * @param context draw context
     * @param mouseX  mouse x position
     * @param mouseY  mouse y position
     * @param delta   frame delta time
     */
    @Override
    public void extractWidgetRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (isVisible() && super.isBordered()) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.get(isActive(), isFocused()),
                    getX(), getY(), getWidth(), getHeight());
        }

        suppressBorder = true;
        context.pose().pushMatrix();
        context.pose().translate(EXTRA_TEXT_PADDING, 0);
        try {
            super.extractWidgetRenderState(context, mouseX, mouseY, delta);
        } finally {
            context.pose().popMatrix();
            suppressBorder = false;
        }
    }

    /**
     * Handles clicks after mapping the visual padded x position back to vanilla text coordinates.
     *
     * @param event       mouse button event
     * @param doubleClick whether the click is a double click
     */
    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {

        super.onClick(padMouseEvent(event), doubleClick);
    }

    /**
     * Handles drags after mapping the visual padded x position back to vanilla text coordinates.
     *
     * @param event  mouse button event
     * @param dragX  x distance dragged
     * @param dragY  y distance dragged
     */
    @Override
    protected void onDrag(@NonNull MouseButtonEvent event, double dragX, double dragY) {

        super.onDrag(padMouseEvent(event), dragX, dragY);
    }

    /**
     * Returns an event shifted into vanilla text coordinates.
     *
     * @param event source mouse event
     * @return shifted mouse event
     */
    private static MouseButtonEvent padMouseEvent(MouseButtonEvent event) {

        return new MouseButtonEvent(event.x() - EXTRA_TEXT_PADDING, event.y(), event.buttonInfo());
    }

}
