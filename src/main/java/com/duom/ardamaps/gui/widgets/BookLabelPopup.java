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

import com.duom.ardamaps.gui.widgets.popup.BookLabelPopupContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/**
 * Book-label behaviour that toggles supplier-created popup content.
 */
public class BookLabelPopup implements BookLabelBehaviour {

    /** Factory used to create popup content when opening. */
    private final Supplier<BookLabelPopupContent> contentSupplier;

    /** Button currently anchoring the popup. */
    private BookLabelButtonWidget anchor;

    /** Currently open popup content, or null when closed. */
    private BookLabelPopupContent content;

    /**
     * Creates a popup behaviour.
     *
     * @param contentSupplier Factory for content opened by this behaviour.
     */
    public BookLabelPopup(Supplier<BookLabelPopupContent> contentSupplier) {

        this.contentSupplier = contentSupplier;
    }

    /**
     * Toggles the popup content anchored to the button.
     *
     * @param button The button that was activated.
     */
    @Override
    public void onActivate(BookLabelButtonWidget button) {

        if (isOpen()) {
            close();
            return;
        }

        anchor = button;
        content = contentSupplier.get();
        content.setOnClose(this::close);
    }

    /**
     * Checks whether popup content is open.
     *
     * @return True if popup content is open.
     */
    @Override
    public boolean isOpen() {

        return content != null;
    }

    /**
     * Closes the popup content.
     */
    @Override
    public void close() {

        anchor = null;
        content = null;
    }

    /**
     * Renders the open popup content.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     * @param delta   The frame delta.
     */
    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (anchor != null && content != null)
            content.render(context, anchor.getX(), anchor.getY(), mouseX, mouseY);
    }

    /**
     * Routes clicks into the open popup content.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param button The clicked mouse button.
     * @return True if the popup handled the click.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        return content != null && content.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Checks whether the mouse is over the open popup content.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True if the mouse is over the popup.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {

        return content != null && content.isMouseOver(mouseX, mouseY);
    }
}
