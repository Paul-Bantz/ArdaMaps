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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Book-label behaviour that toggles an upward-opening context menu.
 */
public class BookLabelDropdown implements BookLabelBehaviour {

    /** Horizontal underlay margin between the dropdown and the expanded label edge. */
    private static final int DROPDOWN_UNDERLAY_MARGIN = 8;

    /** Delay before applying a scroll-selected direct entry. */
    private static final long SCROLL_APPLY_DELAY_MS = 150L;

    /** Supplies entries rendered inside the dropdown menu. */
    private final Supplier<List<ContextMenu.Entry>> entries;

    /** Supplies the current selected entry index, or -1 when unknown. */
    private final IntSupplier selectedIndex;

    /** Currently open menu, or null when closed. */
    private ContextMenu menu;

    /** Button currently anchoring this dropdown. */
    private BookLabelButtonWidget anchor;

    /** Pending direct-selection index awaiting debounce, or -1 when none is pending. */
    private int pendingIndex = -1;

    /** Last time a direct-selection scroll occurred. */
    private long lastScrollMs;

    /**
     * Creates a dropdown behaviour with dynamically supplied entries.
     *
     * @param entries Supplies entries to show when the label is opened.
     */
    public BookLabelDropdown(Supplier<List<ContextMenu.Entry>> entries) {

        this(entries, () -> -1);
    }

    /**
     * Creates a dropdown behaviour with dynamically supplied entries and selection.
     *
     * @param entries       Supplies entries to show when the label is opened.
     * @param selectedIndex Supplies the current selected entry index.
     */
    public BookLabelDropdown(Supplier<List<ContextMenu.Entry>> entries, IntSupplier selectedIndex) {

        this.entries = entries;
        this.selectedIndex = selectedIndex;
    }

    /**
     * Toggles the dropdown anchored to the button.
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
        boolean directSelection = button.isDirectSelection();
        int highlightIndex = directSelection ? selectedIndex.getAsInt() : 0;
        int anchorX = button.getX() + button.expandedWidth() - DROPDOWN_UNDERLAY_MARGIN;
        int anchorY = anchorYFor(button, directSelection ? highlightIndex : 0);

        menu = new ContextMenu(
                anchorX,
                anchorY,
                Client.mc().getWindow().getGuiScaledWidth(),
                Client.mc().getWindow().getGuiScaledHeight(),
                0,
                0,
                entries.get(),
                false,
                ContextMenu.DEFAULT_MIN_WIDTH);
        menu.setHighlightIndex(highlightIndex);
    }

    /**
     * Checks whether this dropdown should open on hover.
     *
     * @return True because dropdown menus are hover-opened.
     */
    @Override
    public boolean opensOnHover() {

        return true;
    }

    /**
     * Checks whether the menu is open.
     *
     * @return True if the menu is open.
     */
    @Override
    public boolean isOpen() {

        return menu != null;
    }

    /**
     * Checks whether the dropdown should tuck behind the label button.
     *
     * @return True because dropdowns render beneath their owning label.
     */
    @Override
    public boolean tucksBehindLabel() {

        return true;
    }

    /**
     * Closes the menu.
     */
    @Override
    public void close() {

        flushPendingSelection();
        menu = null;
        anchor = null;
        pendingIndex = -1;
    }

    /**
     * Renders the open menu.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     * @param delta   The frame delta.
     */
    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (menu == null) return;

        flushPendingSelectionIfReady();
        menu.render(context, mouseX, mouseY);
    }

    /**
     * Routes clicks into the open menu.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param button The clicked mouse button.
     * @return True if the click was handled by the menu.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (menu == null) return false;

        pendingIndex = -1;
        boolean handled = menu.mouseClicked(mouseX, mouseY, button);
        if (handled) close();
        return handled;
    }

    /**
     * Routes scroll input into the open menu highlight.
     *
     * @param mouseX           The mouse x position.
     * @param mouseY           The mouse y position.
     * @param horizontalAmount The horizontal scroll amount.
     * @param verticalAmount   The vertical scroll amount.
     * @return True if the scroll was handled by the menu.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        if (menu == null || !menu.scroll(verticalAmount)) return false;

        afterHighlightMoved();

        return true;
    }

    /**
     * Activates the highlighted menu entry on ENTER.
     *
     * @param key The pressed key code.
     * @return True if the key was handled.
     */
    @Override
    public boolean keyPressed(int key) {

        if (menu == null) return false;

        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {

            if (!menu.moveHighlight(key == GLFW.GLFW_KEY_DOWN ? 1 : -1)) return false;

            afterHighlightMoved();
            return true;
        }

        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) return false;

        return commitHighlighted();
    }

    /**
     * Activates the highlighted menu entry.
     *
     * @return True if the highlighted entry was activated.
     */
    @Override
    public boolean commitHighlighted() {

        if (menu == null) return false;

        pendingIndex = -1;
        boolean handled = menu.activateHighlighted();
        if (handled) close();
        return handled;
    }

    /**
     * Checks whether the mouse is over the open menu.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True when the mouse is over the menu.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {

        return menu != null && menu.isMouseOver(mouseX, mouseY);
    }

    /**
     * Returns the bounds of the open dropdown menu.
     *
     * @return The menu bounds, or null when closed.
     */
    @Override
    public ContentBounds openContentBounds() {

        return menu == null ? null : new ContentBounds(menu.getX(), menu.getY(), menu.getWidth(), menu.getHeight());
    }

    /**
     * Computes the dropdown anchor y for the selected row.
     *
     * @param button The owning button.
     * @param index  The selected row index.
     * @return The dropdown anchor y.
     */
    private static int anchorYFor(BookLabelButtonWidget button, int index) {

        int selected = Math.max(0, index);
        return button.getY() + (button.getHeight() - ContextMenu.ITEM_HEIGHT) / 2 - selected * ContextMenu.ITEM_HEIGHT;
    }

    /**
     * Performs direct-selection follow-up after the menu highlight changes.
     */
    private void afterHighlightMoved() {

        if (anchor != null && anchor.isDirectSelection()) {

            menu.setPosition(anchor.getX() + anchor.expandedWidth() - DROPDOWN_UNDERLAY_MARGIN,
                    anchorYFor(anchor, menu.highlightIndex()));
            pendingIndex = menu.highlightIndex();
            lastScrollMs = System.currentTimeMillis();
        }
    }

    /**
     * Applies a pending scroll selection after the debounce delay.
     */
    private void flushPendingSelectionIfReady() {

        if (pendingIndex >= 0 && System.currentTimeMillis() - lastScrollMs >= SCROLL_APPLY_DELAY_MS) {
            flushPendingSelection();
        }
    }

    /**
     * Applies and clears any pending scroll selection.
     */
    private void flushPendingSelection() {

        if (menu == null || pendingIndex < 0) return;

        int index = pendingIndex;
        pendingIndex = -1;
        menu.setHighlightIndex(index);
        menu.activateHighlighted();
    }
}
