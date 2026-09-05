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
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * A simple context menu implementation for the map GUI.
 * It supports multiple entries, each with a label and an associated action.
 * The menu is rendered as a connected stack of vanilla-style buttons.
 */
public class ContextMenu {

    /** Context menu row height in pixels. */
    static final int ITEM_HEIGHT = Button.DEFAULT_HEIGHT;

    /** Shared minimum width applied to menus opened from the map and from book labels. */
    public static final int DEFAULT_MIN_WIDTH = 90;

    /** The list of entries that will be displayed in the context menu, each containing a label and an associated action. */
    private final List<Entry> entries;

    /** The calculated width of the context menu, determined by the longest entry label plus horizontal padding. */
    @Getter
    private final int width;

    /** The calculated height of the context menu, determined by row count. */
    @Getter
    private final int height;

    /** Font line height used for stable construction-time menu geometry. */
    private final int lineHeight;

    /** Width of the screen containing this context menu. */
    private final int screenWidth;

    /** Height of the screen containing this context menu. */
    private final int screenHeight;

    /** The world X coordinate associated with this context menu, used for actions that require world position. */
    @Getter
    private final double worldX;

    /** The world Z coordinate associated with this context menu, used for actions that require world position. */
    @Getter
    private final double worldZ;

    /** Whether the menu opens above its anchor point. */
    @Getter
    private final boolean openUpward;

    /** The x-coordinate of the top-left corner of the context menu on the screen. */
    @Getter
    private int x;

    /** The y-coordinate of the top-left corner of the context menu on the screen. */
    @Getter
    private int y;

    /** Keyboard and scroll highlighted entry index, or -1 when none is highlighted. */
    private int highlightIndex = -1;

    /**
     * Constructs a new ContextMenu instance with automatic opening direction and minimum width.
     *
     * @param x            The anchor x-coordinate of the context menu on the screen.
     * @param y            The anchor y-coordinate of the context menu on the screen.
     * @param screenWidth  The width of the screen containing this menu.
     * @param screenHeight The height of the screen containing this menu.
     * @param worldX       The world X coordinate associated with this context menu.
     * @param worldZ       The world Z coordinate associated with this context menu.
     * @param entries      The list of entries that will be displayed in the context menu.
     * @param minWidth     The minimum menu width in pixels.
     */
    public ContextMenu(int x, int y, int screenWidth, int screenHeight,
                       double worldX, double worldZ, List<Entry> entries,
                       int minWidth) {

        this(x, y, screenWidth, screenHeight, worldX, worldZ, entries,
                label -> Minecraft.getInstance().font.width(label),
                () -> Minecraft.getInstance().font.lineHeight,
                minWidth,
                null);
    }

    /**
     * Constructs a context menu using injected text measurement dependencies.
     *
     * @param x            The anchor x-coordinate of the context menu on the screen.
     * @param y            The anchor y-coordinate of the context menu on the screen.
     * @param screenWidth  The width of the screen containing this menu.
     * @param screenHeight The height of the screen containing this menu.
     * @param worldX       The world X coordinate associated with this context menu.
     * @param worldZ       The world Z coordinate associated with this context menu.
     * @param entries      The list of entries that will be displayed in the context menu.
     * @param textWidth    The text width calculator for entry labels.
     * @param lineHeight   The font line height supplier for menu geometry.
     */
    ContextMenu(int x, int y, int screenWidth, int screenHeight,
                double worldX, double worldZ, List<Entry> entries,
                ToIntFunction<Component> textWidth, IntSupplier lineHeight) {

        this(x, y, screenWidth, screenHeight, worldX, worldZ, entries, textWidth, lineHeight, 0, null);
    }

    /**
     * Constructs a context menu using injected text measurement dependencies and a minimum width.
     *
     * @param x            The anchor x-coordinate of the context menu on the screen.
     * @param y            The anchor y-coordinate of the context menu on the screen.
     * @param screenWidth  The width of the screen containing this menu.
     * @param screenHeight The height of the screen containing this menu.
     * @param worldX       The world X coordinate associated with this context menu.
     * @param worldZ       The world Z coordinate associated with this context menu.
     * @param entries      The list of entries that will be displayed in the context menu.
     * @param textWidth    The text width calculator for entry labels.
     * @param lineHeight   The font line height supplier for menu geometry.
     * @param minWidth     The minimum menu width in pixels.
     */
    ContextMenu(int x, int y, int screenWidth, int screenHeight,
                double worldX, double worldZ, List<Entry> entries,
                ToIntFunction<Component> textWidth, IntSupplier lineHeight, int minWidth) {

        this(x, y, screenWidth, screenHeight, worldX, worldZ, entries, textWidth, lineHeight, minWidth, null);
    }

    /**
     * Constructs a context menu using injected dependencies and an optional explicit opening direction.
     *
     * @param x               The anchor x-coordinate of the context menu on the screen.
     * @param y               The anchor y-coordinate of the context menu on the screen.
     * @param screenWidth     The width of the screen containing this menu.
     * @param screenHeight    The height of the screen containing this menu.
     * @param worldX          The world X coordinate associated with this context menu.
     * @param worldZ          The world Z coordinate associated with this context menu.
     * @param entries         The list of entries that will be displayed in the context menu.
     * @param textWidth       The text width calculator for entry labels.
     * @param lineHeight      The font line height supplier for menu geometry.
     * @param openUpward      True to open above the anchor, false to open below, or null to derive it from available space.
     */
    ContextMenu(int x, int y, int screenWidth, int screenHeight,
                double worldX, double worldZ, List<Entry> entries,
                ToIntFunction<Component> textWidth, IntSupplier lineHeight, Boolean openUpward) {

        this(x, y, screenWidth, screenHeight, worldX, worldZ, entries, textWidth, lineHeight, 0, openUpward);
    }

    /**
     * Constructs a context menu using injected dependencies and an optional explicit opening direction.
     *
     * @param x               The anchor x-coordinate of the context menu on the screen.
     * @param y               The anchor y-coordinate of the context menu on the screen.
     * @param screenWidth     The width of the screen containing this menu.
     * @param screenHeight    The height of the screen containing this menu.
     * @param worldX          The world X coordinate associated with this context menu.
     * @param worldZ          The world Z coordinate associated with this context menu.
     * @param entries         The list of entries that will be displayed in the context menu.
     * @param textWidth       The text width calculator for entry labels.
     * @param lineHeight      The font line height supplier for menu geometry.
     * @param minWidth        The minimum menu width in pixels.
     * @param openUpward      True to open above the anchor, false to open below, or null to derive it from available space.
     */
    ContextMenu(int x, int y, int screenWidth, int screenHeight,
                double worldX, double worldZ, List<Entry> entries,
                ToIntFunction<Component> textWidth, IntSupplier lineHeight, int minWidth, Boolean openUpward) {

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.entries = entries;
        this.lineHeight = lineHeight.getAsInt();

        this.width = Math.max(minWidth, entries.stream()
                .mapToInt(e -> textWidth.applyAsInt(e.label()))
                .max()
                .orElse(0) + ConnectedButtonSurface.LABEL_PADDING * 2);

        this.height = entries.size() * ITEM_HEIGHT;
        this.openUpward = openUpward != null ? openUpward : y + height > screenHeight;

        setPosition(x, y);
    }

    /**
     * Sets the context menu anchor position while preserving the original opening direction.
     *
     * @param x The anchor x-coordinate.
     * @param y The anchor y-coordinate.
     */
    public void setPosition(int x, int y) {

        this.x = Math.clamp(x, 0, Math.max(0, screenWidth - width));

        int topY = openUpward ? y - height : y;
        this.y = Math.clamp(topY, 0, Math.max(0, screenHeight - height));
    }

    /**
     * Constructs a new ContextMenu instance with an explicit opening direction and minimum width.
     *
     * @param x            The anchor x-coordinate of the context menu on the screen.
     * @param y            The anchor y-coordinate of the context menu on the screen.
     * @param screenWidth  The width of the screen containing this menu.
     * @param screenHeight The height of the screen containing this menu.
     * @param worldX       The world X coordinate associated with this context menu.
     * @param worldZ       The world Z coordinate associated with this context menu.
     * @param entries      The list of entries that will be displayed in the context menu.
     * @param openUpward   True to open above the anchor point.
     * @param minWidth     The minimum menu width in pixels.
     */
    public ContextMenu(int x, int y, int screenWidth, int screenHeight,
                       double worldX, double worldZ, List<Entry> entries,
                       boolean openUpward, int minWidth) {

        this(x, y, screenWidth, screenHeight, worldX, worldZ, entries,
                label -> Minecraft.getInstance().font.width(label),
                () -> Minecraft.getInstance().font.lineHeight,
                minWidth,
                openUpward);
    }

    /**
     * Renders the context menu on the screen, including the background and the individual entries.
     * Each entry is highlighted when hovered by the mouse cursor.
     *
     * @param context The DrawContext used for rendering the menu.
     * @param mouseX  The current x-coordinate of the mouse cursor, used for hover detection.
     * @param mouseY  The current y-coordinate of the mouse cursor, used for hover detection.
     */
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        renderContents(context, mouseX, mouseY);
    }

    /**
     * Renders the full unclipped context menu contents.
     *
     * @param context The DrawContext used for rendering the menu.
     * @param mouseX  The current x-coordinate of the mouse cursor, used for hover detection.
     * @param mouseY  The current y-coordinate of the mouse cursor, used for hover detection.
     */
    private void renderContents(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        for (int i = 0; i < entries.size(); i++) {
            int itemY = rowTop(i);
            boolean mouseHovered = isMouseOverEntry(mouseX, mouseY, i);
            boolean hovered = mouseHovered || i == highlightIndex;

            if (mouseHovered) context.requestCursor(CursorTypes.POINTING_HAND);

            ConnectedButtonSurface.drawInList(context, x, itemY, width, ITEM_HEIGHT, i, entries.size(), hovered);
            context.text(
                    Client.mc().font,
                    entries.get(i).label(),
                    x + ConnectedButtonSurface.LABEL_PADDING,
                    textY(i),
                    ModConstants.COLOR_WHITE,
                    false
            );
        }
    }

    /**
     * Gets the top y-coordinate of an entry's highlighted and clickable band.
     *
     * @param index The entry row index.
     * @return The top y-coordinate of the row band.
     */
    private int rowTop(int index) {

        return y + index * ITEM_HEIGHT;
    }

    /**
     * Determines if the given mouse coordinates are within a menu entry row.
     *
     * @param mouseX The x-coordinate of the mouse cursor.
     * @param mouseY The y-coordinate of the mouse cursor.
     * @param index  The entry row index.
     * @return True if the coordinates are inside the row.
     */
    private boolean isMouseOverEntry(double mouseX, double mouseY, int index) {

        return mouseX >= x
                && mouseX < x + width
                && mouseY >= rowTop(index)
                && mouseY < rowTop(index) + ITEM_HEIGHT;
    }

    /**
     * Gets the top y-coordinate used to draw an entry label.
     *
     * @param index The entry row index.
     * @return The label top y-coordinate.
     */
    private int textY(int index) {

        return y + (ITEM_HEIGHT - lineHeight) / 2 + index * ITEM_HEIGHT;
    }

    /**
     * Handles mouse click events on the context menu, determining if a click occurred on any of the menu entries and executing the associated action if so.
     *
     * @param mouseX The x-coordinate of the mouse click event.
     * @param mouseY The y-coordinate of the mouse click event.
     * @param button The mouse button that was clicked, used to determine if it's a left-click.
     * @return true if a menu entry was clicked and its action executed, false otherwise.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        for (int i = 0; i < entries.size(); i++) {
            if (isMouseOverEntry(mouseX, mouseY, i)) {

                entries.get(i).action().run();
                return true;
            }
        }
        return false;
    }

    /**
     * Moves the highlighted entry cyclically in response to scroll input.
     *
     * @param amount The vertical scroll amount.
     * @return True if the scroll was consumed.
     */
    public boolean scroll(double amount) {

        if (entries.isEmpty() || amount == 0) return false;

        return moveHighlight(amount < 0 ? 1 : -1);
    }

    /**
     * Moves the highlighted entry cyclically by the given step.
     *
     * @param step The signed movement amount.
     * @return True if the highlight moved.
     */
    public boolean moveHighlight(int step) {

        if (entries.isEmpty() || step == 0) return false;

        int origin = highlightIndex < 0 && step < 0 ? 0 : highlightIndex;
        highlightIndex = Math.floorMod(origin + step, entries.size());
        return true;
    }

    /**
     * Returns the highlighted entry index.
     *
     * @return The highlighted index, or -1 when none is highlighted.
     */
    public int highlightIndex() {

        return highlightIndex;
    }

    /**
     * Sets the highlighted entry index.
     *
     * @param highlightIndex The highlighted index, or -1 for no highlight.
     */
    public void setHighlightIndex(int highlightIndex) {

        this.highlightIndex = highlightIndex >= 0 && highlightIndex < entries.size() ? highlightIndex : -1;
    }

    /**
     * Runs the highlighted entry action when one is selected.
     *
     * @return True if an action was run.
     */
    public boolean activateHighlighted() {

        if (highlightIndex < 0 || highlightIndex >= entries.size()) return false;

        entries.get(highlightIndex).action().run();
        return true;
    }

    /**
     * Determines if the given mouse coordinates are within the bounds of the context menu, used for hover detection and click handling.
     *
     * @param mouseX The x-coordinate of the mouse cursor.
     * @param mouseY The y-coordinate of the mouse cursor.
     * @return true if the mouse coordinates are within the context menu bounds, false otherwise.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * Represents a single entry in the context menu, consisting of a label and an action to perform when clicked.
     *
     * @param label  The text label of the menu entry.
     * @param action The action to execute when the entry is clicked, represented as a Runnable.
     */
    public record Entry(Component label, Runnable action) {

    }
}
