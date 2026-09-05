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

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

/**
 * Interaction contract for book-label buttons.
 */
public interface BookLabelBehaviour {

    /**
     * Bounds of open content owned by a label behaviour.
     *
     * @param x      The content left edge.
     * @param y      The content top edge.
     * @param width  The content width.
     * @param height The content height.
     */
    record ContentBounds(int x, int y, int width, int height) {

    }

    /**
     * Handles activation of the owning button.
     *
     * @param button The button that was activated.
     */
    void onActivate(BookLabelButtonWidget button);

    /**
     * Renders any open content owned by this behaviour.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     * @param delta   The frame delta.
     */
    default void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    /**
     * Handles a mouse click inside this behaviour's open content.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param button The clicked mouse button.
     * @return True if the click was handled.
     */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handles a mouse scroll inside this behaviour's open content.
     *
     * @param mouseX           The mouse x position.
     * @param mouseY           The mouse y position.
     * @param horizontalAmount The horizontal scroll amount.
     * @param verticalAmount   The vertical scroll amount.
     * @return True if the scroll was handled.
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    /**
     * Handles a key press while this behaviour's content is open.
     *
     * @param key The pressed key code.
     * @return True if the key was handled.
     */
    default boolean keyPressed(int key) {
        return false;
    }

    /**
     * Commits the currently highlighted entry, when this behaviour supports highlight-driven selection.
     *
     * @return True if the highlighted entry was committed.
     */
    default boolean commitHighlighted() {

        return false;
    }

    /**
     * Checks whether the mouse is over this behaviour's open content.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True if the mouse is over the open content.
     */
    default boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    /**
     * Returns the bounds of any open content owned by this behaviour.
     *
     * @return The open content bounds, or null when unavailable.
     */
    @Nullable
    default ContentBounds openContentBounds() {

        return null;
    }

    /**
     * Checks whether this behaviour currently has open content.
     *
     * @return True if content is open.
     */
    default boolean isOpen() {
        return false;
    }

    /**
     * Checks whether this behaviour should open while the owning label is hovered.
     *
     * @return True if hover should open this behaviour.
     */
    default boolean opensOnHover() {
        return false;
    }

    /**
     * Checks whether open content tucks behind the label bar.
     *
     * @return True if the label should draw over open content.
     */
    default boolean tucksBehindLabel() {
        return false;
    }

    /**
     * Closes any content opened by this behaviour.
     */
    default void close() {
    }
}
