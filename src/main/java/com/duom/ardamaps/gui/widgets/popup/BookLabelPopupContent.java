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

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Content rendered inside a book-label popup.
 */
public interface BookLabelPopupContent {

    /**
     * Renders the popup content.
     *
     * @param context The GUI draw context.
     * @param anchorX The x coordinate of the owning label button.
     * @param anchorY The y coordinate of the owning label button.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     */
    void render(GuiGraphicsExtractor context, int anchorX, int anchorY, int mouseX, int mouseY);

    /**
     * Supplies a callback that closes the owning popup.
     *
     * @param onClose Callback invoked to close the popup.
     */
    default void setOnClose(Runnable onClose) {

    }

    /**
     * Handles a click inside the popup.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param button The clicked mouse button.
     * @return True if the click was handled.
     */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    /**
     * Checks whether the mouse is over the popup.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True if the mouse is over the popup.
     */
    boolean isMouseOver(double mouseX, double mouseY);

    /**
     * Gets the popup width in pixels.
     *
     * @return The popup width.
     */
    int getWidth();

    /**
     * Gets the popup height in pixels.
     *
     * @return The popup height.
     */
    int getHeight();
}
