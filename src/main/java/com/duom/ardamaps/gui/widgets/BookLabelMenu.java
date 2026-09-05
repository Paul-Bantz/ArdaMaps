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

import java.util.List;
import java.util.function.Supplier;

/**
 * Book-label behaviour that combines a hover dropdown with a centred popup.
 */
public class BookLabelMenu implements BookLabelBehaviour {

    /** Dropdown behaviour used while the menu list is active. */
    private final BookLabelDropdown dropdown;

    /** Popup behaviour used after a menu entry hands off to centred content. */
    private final BookLabelPopup popup;

    /** Button currently anchoring the active menu or popup. */
    private BookLabelButtonWidget anchor;

    /**
     * Creates a composite menu behaviour.
     *
     * @param entries         Supplies dropdown entries.
     * @param contentSupplier Supplies popup content.
     */
    public BookLabelMenu(Supplier<List<ContextMenu.Entry>> entries, Supplier<BookLabelPopupContent> contentSupplier) {

        dropdown = new BookLabelDropdown(entries);
        popup = new BookLabelPopup(contentSupplier);
    }

    /**
     * Opens the centred popup from a dropdown entry.
     */
    public void openPopup() {

        if (anchor == null) return;

        dropdown.close();
        popup.onActivate(anchor);
    }

    /**
     * Activates either the popup or dropdown state.
     *
     * @param button The button that was activated.
     */
    @Override
    public void onActivate(BookLabelButtonWidget button) {

        anchor = button;
        if (popup.isOpen()) {
            popup.close();
            return;
        }

        dropdown.onActivate(button);
    }

    /**
     * Renders the active dropdown or popup.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     * @param delta   The frame delta.
     */
    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        activeBehaviour().render(context, mouseX, mouseY, delta);
    }

    /**
     * Routes clicks to the active dropdown or popup.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param button The clicked mouse button.
     * @return True if the click was handled.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        return activeBehaviour().mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Routes scroll input to the active dropdown or popup.
     *
     * @param mouseX           The mouse x position.
     * @param mouseY           The mouse y position.
     * @param horizontalAmount The horizontal scroll amount.
     * @param verticalAmount   The vertical scroll amount.
     * @return True if the scroll was handled.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        return activeBehaviour().mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Routes key presses to the active dropdown or popup.
     *
     * @param key The pressed key code.
     * @return True if the key was handled.
     */
    @Override
    public boolean keyPressed(int key) {

        return activeBehaviour().keyPressed(key);
    }

    /**
     * Commits the active behaviour's highlighted entry.
     *
     * @return True if the active highlighted entry was committed.
     */
    @Override
    public boolean commitHighlighted() {

        return activeBehaviour().commitHighlighted();
    }

    /**
     * Checks whether the mouse is over the active content.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True if the mouse is over the active content.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {

        return activeBehaviour().isMouseOver(mouseX, mouseY);
    }

    /**
     * Returns the bounds of the active open content.
     *
     * @return The active content bounds, or null when unavailable.
     */
    @Override
    public ContentBounds openContentBounds() {

        return activeBehaviour().openContentBounds();
    }

    /**
     * Checks whether any menu content is open.
     *
     * @return True if the dropdown or popup is open.
     */
    @Override
    public boolean isOpen() {

        return dropdown.isOpen() || popup.isOpen();
    }

    /**
     * Checks whether this behaviour opens from hover.
     *
     * @return True while the dropdown is active, false while the popup is active.
     */
    @Override
    public boolean opensOnHover() {

        return !popup.isOpen();
    }

    /**
     * Checks whether active content tucks behind the label.
     *
     * @return True for the dropdown, false for the popup.
     */
    @Override
    public boolean tucksBehindLabel() {

        return activeBehaviour().tucksBehindLabel();
    }

    /**
     * Closes dropdown and popup content.
     */
    @Override
    public void close() {

        dropdown.close();
        popup.close();
        anchor = null;
    }

    /**
     * Gets the behaviour currently receiving delegated events.
     *
     * @return The popup when open, otherwise the dropdown.
     */
    private BookLabelBehaviour activeBehaviour() {

        return popup.isOpen() ? popup : dropdown;
    }
}
