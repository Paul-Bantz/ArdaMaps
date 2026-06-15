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
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * A simple context menu implementation for the map GUI.
 * It supports multiple entries, each with a label and an associated action.
 * The menu is rendered as a nine-sliced texture, and entries are highlighted on hover.
 */
public class ContextMenu {

    /**
     * Represents a single entry in the context menu, consisting of a label and an action to perform when clicked.
     * @param label The text label of the menu entry.
     * @param action The action to execute when the entry is clicked, represented as a Runnable.
     */
    public record Entry(Text label, Runnable action) {}

    /** Context menu item height */
    private static final int ITEM_HEIGHT = 16;

    /** The padding around the menu items, ensuring that the text is not flush against the edges of the menu. */
    private static final int PADDING = 6;

    /** The horizontal padding for the text within the menu, providing space on either side of the text for better readability. */
    private static final int H_PADDING = 12;

    /** The x-coordinate of the top-left corner of the context menu on the screen. */
    @Setter
    private int x;

    /** The y-coordinate of the top-left corner of the context menu on the screen. */
    @Setter
    private int y;

    /** The list of entries that will be displayed in the context menu, each containing a label and an associated action. */
    private final List<Entry> entries;

    /** The calculated width of the context menu, determined by the longest entry label plus horizontal padding. */
    private final int width;

    /** The calculated height of the context menu, determined by the number of entries multiplied by the item height plus vertical padding. */
    private final int height;

    /** The world X coordinate associated with this context menu, used for actions that require world position. */
    @Getter
    private final double worldX;

    /** The world Z coordinate associated with this context menu, used for actions that require world position. */
    @Getter
    private final double worldZ;

    /**
     * Constructs a new ContextMenu instance with the specified screen coordinates, world coordinates, and menu entries.
     * @param x The x-coordinate of the top-left corner of the context menu on the screen.
     * @param y The y-coordinate of the top-left corner of the context menu on the screen.
     * @param worldX The world X coordinate associated with this context menu, used for actions that require world position.
     * @param worldZ The world Z coordinate associated with this context menu, used for actions that require world position.
     * @param entries The list of entries that will be displayed in the context menu, each containing a label and an associated action.
     */
    public ContextMenu(int x, int y, double worldX, double worldZ, List<Entry> entries) {

        this.x = x;
        this.y = y;

        this.worldX = worldX;
        this.worldZ = worldZ;

        this.entries = entries;

        this.width = entries.stream()
                .mapToInt(e -> MinecraftClient.getInstance().textRenderer.getWidth(e.label()))
                .max()
                .orElse(40) + H_PADDING * 2;

        this.height = entries.size() * ITEM_HEIGHT + PADDING * 2;
    }

    /**
     * Renders the context menu on the screen, including the background and the individual entries.
     * Each entry is highlighted when hovered by the mouse cursor.
     * @param context The DrawContext used for rendering the menu.
     * @param mouseX The current x-coordinate of the mouse cursor, used for hover detection.
     * @param mouseY The current y-coordinate of the mouse cursor, used for hover detection.
     */
    public void render(DrawContext context, int mouseX, int mouseY) {

        var textRenderer = Client.mc().textRenderer;

        context.drawNineSlicedTexture(ModConstants.MAP_GUI_ELEMENTS,
                x, y,
                width, height,
                16,
                16,
                16,
                16,
                64,
                64,
                16, 176);

        for (int i = 0; i < entries.size(); i++) {
            int itemY = y + PADDING + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= x && mouseX <= x + width
                    && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;

            if (hovered) {

                context.drawNineSlicedTexture(ModConstants.MAP_GUI_ELEMENTS,
                        x, itemY,
                        width, ITEM_HEIGHT,
                        16,
                        1,
                        16,
                        1,
                        64,
                        64,
                        80, 176);
            }

            context.drawText(
                    Client.mc().textRenderer,
                    entries.get(i).label(),
                    x + H_PADDING,
                    itemY + textRenderer.fontHeight / 2,
                    ModConstants.COLOR_DARK_BROWN,
                    false
            );
        }
    }

    /**
     * Handles mouse click events on the context menu, determining if a click occurred on any of the menu entries and executing the associated action if so.
     * @param mouseX The x-coordinate of the mouse click event.
     * @param mouseY The y-coordinate of the mouse click event.
     * @param button The mouse button that was clicked, used to determine if it's a left-click.
     * @return true if a menu entry was clicked and its action executed, false otherwise.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        for (int i = 0; i < entries.size(); i++) {
            int itemY = y + PADDING + i * ITEM_HEIGHT;
            if (mouseX >= x && mouseX <= x + width
                    && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT) {

                entries.get(i).action().run();
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the given mouse coordinates are within the bounds of the context menu, used for hover detection and click handling.
     * @param mouseX The x-coordinate of the mouse cursor.
     * @param mouseY The y-coordinate of the mouse cursor.
     * @return true if the mouse coordinates are within the context menu bounds, false otherwise.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}