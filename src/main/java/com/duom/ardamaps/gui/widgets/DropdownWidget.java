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
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import lombok.Getter;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A customizable dropdown widget for Minecraft GUI that supports generic types,
 * expandable options, scrolling, and configurable display settings.
 *
 * @param <T> The type of items stored in the dropdown
 * @param <E> The type of display pair (must extend {@link TextIdentifierPairItem})
 */
public class DropdownWidget<T, E extends TextIdentifierPairItem> extends ClickableWidget {

    /** Margin around text inside the dropdown items */
    protected static final int TEXT_MARGIN = 4;

    /** Scrollbar */
    private final ScrollbarWidget scrollbar = new ScrollbarWidget(2, 8, 0x445D4D35, 0xFF5D4D35, 1);

    /** Original height of the Dropdown - ie the height of the button */
    protected final int originalHeight;

    /** Original width of the Dropdown - ie the width of the button */
    protected int originalWidth;

    /** Maximum number of visible options before scrolling */
    protected final int maxVisibleOptions;

    /** Text to display when no item is selected */
    protected final Text placeholderText;

    /** Icon to display when no item is selected */
    protected final Identifier placeholderIcon;

    /** Direction in which the dropdown expands */
    protected final ExpandDirection expandDirection;

    /** List of options in the dropdown */
    protected final List<T> options;

    /** Function to convert items to display pairs */
    protected final Function<T, E> optionDisplay;

    /** Callback invoked when an item is selected */
    protected final Consumer<T> onSelect;

    /** Whether to display icons as sprites from the atlas or as regular textures */
    protected final boolean displayAsSprite;

    /** Whether to allow a null selection (i.e., no item selected) */
    protected final boolean allowNull;

    /** Whether to show expand/collapse arrows on the dropdown button */
    protected final boolean displayArrows;

    /** Whether to show text labels for each dropdown item */
    protected final boolean displayLabels;

    /** Whether to show icons for each dropdown item */
    protected final boolean displayIcons;

    /** Padding inside the dropdown button for text and icons */
    protected int buttonPadding;

    /** Currently selected item */
    protected T selected;

    /** Size of the icons to display in the dropdown items */
    protected int iconSize;

    /** True if the dropdown is currently expanded. */
    @Getter
    protected boolean expanded;


    /**
     * Creates a new dropdown widget with full customization options.
     *
     * @param x                 The x-coordinate of the widget
     * @param y                 The y-coordinate of the widget
     * @param width             The width of the widget
     * @param height            The height of each item in the widget
     * @param title             The title text displayed above the widget
     * @param nullValueText     The text to display when no item is selected
     * @param options           The list of options to display
     * @param optionDisplay     Function to convert items to display pairs
     * @param selected          The initially selected item (can be null)
     * @param onSelect          Callback invoked when an item is selected
     * @param allowNull         Whether null selection is allowed
     * @param expanded          Whether the dropdown starts expanded
     * @param displayAsSprite   Whether to display icons as sprites from the atlas
     * @param displayLabels     Whether to show text labels
     * @param displayIcons      Whether to show icons
     * @param displayArrows     Whether to show expand/collapse arrows
     * @param expandDirection   The direction in which the dropdown expands
     * @param maxVisibleOptions Maximum number of visible options before scrolling
     */
    public DropdownWidget(
            int x,
            int y,
            int width,
            int height,
            Text title,
            Text nullValueText,
            Identifier placeholderIcon,
            List<T> options,
            Function<T, E> optionDisplay,
            @Nullable T selected,
            Consumer<T> onSelect,
            boolean allowNull,
            boolean expanded,
            boolean displayAsSprite,
            boolean displayLabels,
            boolean displayIcons,
            boolean displayArrows,
            ExpandDirection expandDirection,
            int maxVisibleOptions
    ) {
        super(x, y, width, height, title);
        this.placeholderText = nullValueText;
        this.placeholderIcon = placeholderIcon;
        this.originalWidth = width;
        this.originalHeight = height;
        this.options = options;
        this.optionDisplay = optionDisplay;
        this.displayIcons = displayIcons;
        this.selected = selected;
        this.onSelect = onSelect;
        this.allowNull = allowNull;
        this.expanded = expanded;
        this.displayAsSprite = displayAsSprite;
        this.displayLabels = displayLabels;
        this.displayArrows = displayArrows;
        this.expandDirection = expandDirection;
        this.maxVisibleOptions = maxVisibleOptions;
        this.buttonPadding = 4;

        this.iconSize = (int) (originalHeight * .5f);
    }

    @Override
    public void setWidth(int width) {

        super.setWidth(width);
        originalWidth = width;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        super.render(context, mouseX, mouseY, delta);

        renderTitle(context);
        List<T> allItems = computeItemList();

        if (expanded) {

            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(0, 0, 200);

            renderExpandedDropdown(context, allItems, mouseX, mouseY);

            matrices.pop();
        }
    }

    /**
     * Renders the title text above the dropdown widget.
     *
     * @param context The drawing context
     */
    private void renderTitle(DrawContext context) {

        Text title = getMessage();

        if (title != null) {

            TextRenderer textRenderer = Client.mc().textRenderer;
            int titleY = getY() - (textRenderer.fontHeight / 2) - 8;
            context.drawTextWithShadow(textRenderer, title, getX(), titleY, ModConstants.COLOR_WHITE);
        }
    }

    /**
     * Builds the complete list of items including null option if allowed.
     *
     * @return List of all items to display
     */
    private List<T> computeItemList() {

        List<T> allItems = new ArrayList<>();

        if (allowNull) allItems.add(null);

        allItems.addAll(options);

        allItems.sort((a, b) -> {
            // selection always first
            if (Objects.equals(a, selected)) return -1;
            if (Objects.equals(b, selected)) return 1;

            // null handling
            if (a == null) return -1; // null before others
            if (b == null) return 1;

            E itemPairA = optionDisplay.apply(a);
            E itemPairB = optionDisplay.apply(a);
            String textA = itemPairA.text().getString();
            String textB = itemPairB.text().getString();

            // alphabetical by name()
            return textA.compareToIgnoreCase(textB);
        });

        return allItems;
    }

    /**
     * Renders the expanded dropdown list with all visible options.
     *
     * @param context The drawing context
     * @param items   The list of all items to display (including null option if allowed)
     * @param mouseX  Current mouse X position
     * @param mouseY  Current mouse Y position
     */
    protected void renderExpandedDropdown(DrawContext context, List<T> items, int mouseX, int mouseY) {

        var dropDownItems = new ArrayList<>(items);
        dropDownItems.remove(selected);

        int visibleCount = Math.min(dropDownItems.size(), maxVisibleOptions);

        // Dynamically adjust height based on visible items when expanded
        this.height = originalHeight + visibleCount * originalHeight;

        // Start at index 1 to skip rendering the main button area again, as it's already rendered in renderButton()
        for (int i = 0; i < visibleCount; i++) {

            // Calculate the actual index in the full item list based on scroll offset
            int actualIndex = scrollbar.getScrollOffset() + i;

            T dropdownItem = dropDownItems.get(actualIndex);

            int y = computeDropdownItemPositionY(i) + originalHeight;

            boolean isHovered = isMouseOverItem(mouseX, mouseY, y);
            boolean isSelected = isItemSelected(dropdownItem);

            renderDropdownItem(context, getX(), y, dropdownItem, isHovered, isSelected);

            // Stop rendering if we've reached the last item in the list to avoid overdrawing
            if (actualIndex + 1 >= dropDownItems.size()) break;
        }

        // Render scrollbar if needed
        if (items.size() > maxVisibleOptions) {
            int trackX = getX() + originalWidth - 2 - 4;
            int trackY = computeDropdownItemPositionY(1) + 12;
            int trackHeight = (visibleCount - 1) * originalHeight;
            int maxOffset = (items.size() - 1) - maxVisibleOptions;
            scrollbar.setMaxOffset(maxOffset);
            scrollbar.render(context, trackX, trackY, trackHeight, maxVisibleOptions - 1, items.size() - 1);
        }
    }

    /**
     * Computes the Y position of a dropdown item based on its index.
     *
     * @param index The index of the item in the visible list
     * @return Y coordinate for the item
     */
    protected int computeDropdownItemPositionY(int index) {
        int indexOffset = (index * originalHeight );
        boolean expandUpwards = expandDirection.equals(ExpandDirection.UP_LEFT) ||
                expandDirection.equals(ExpandDirection.UP_RIGHT);

        return expandUpwards
                ? getDropDownTopY(maxVisibleOptions) + indexOffset
                : getY() + indexOffset;
    }

    /**
     * Checks if the mouse is over a specific dropdown item.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param itemY  Item Y position
     * @return true if mouse is over the item
     */
    protected boolean isMouseOverItem(int mouseX, int mouseY, int itemY) {
        return mouseX >= getX() && mouseX <= getX() + originalWidth &&
                mouseY >= itemY && mouseY <= itemY + originalHeight;
    }

    /**
     * Checks if an item is currently selected.
     *
     * @param item The item to check
     * @return true if the item is selected
     */
    private boolean isItemSelected(T item) {

        return (item == null && selected == null) || (item != null && item.equals(selected));
    }

    /**
     * Renders a box with optional text and icon.
     *
     * @param context    The drawing context
     * @param x          X position
     * @param y          Y position
     * @param item       The item to render
     * @param isHovered  Whether the mouse is hovering over this item
     * @param isSelected whether this item is selected
     */
    private void renderDropdownItem(DrawContext context, int x, int y, T item, boolean isHovered, boolean isSelected) {

        TextRenderer textRenderer = Client.mc().textRenderer;

        E itemPair = optionDisplay.apply(item);

        drawListSlice(context, x, y, isHovered, isSelected);

        var hasIcon = false;

        if (displayIcons) {

            var icon = (item == null) ? placeholderIcon : itemPair.image();

            if (icon != null) {

                if (displayAsSprite)
                    context.drawSprite(x + buttonPadding, y + (originalHeight - iconSize) / 2, 0, iconSize, iconSize, IconSpriteAtlas.retrieveSprite(icon));
                else
                    context.drawTexture(icon, x + buttonPadding, y + (originalHeight - iconSize) / 2, 0, 0, iconSize, iconSize, iconSize, iconSize);

                hasIcon = true;
            }
        }

        if (displayLabels) {

            Text display = (item == null) ? placeholderText : itemPair.text();
            int textX = x + TEXT_MARGIN;

            if (hasIcon)
                textX += iconSize + TEXT_MARGIN;

            int textY = y + (originalHeight - textRenderer.fontHeight) / 2;
            context.drawText(textRenderer, display, textX, textY, getLabelColor(), false);
        }
    }


    /**
     * Gets the top Y coordinate of the dropdown list.
     *
     * @param totalItems Total number of items in the dropdown
     * @return Top Y coordinate
     */
    public int getDropDownTopY(int totalItems) {
        int visibleCount = Math.min(totalItems, maxVisibleOptions);

        return switch (expandDirection) {
            case UP_LEFT, UP_RIGHT -> getDropdownListOrigin() - Math.min(visibleCount, options.size()) * originalHeight;
            case DOWN_RIGHT -> getDropdownListOrigin();
        };
    }

    protected void drawListSlice(DrawContext context, int x, int y, boolean isHovered, boolean isSelected) {

        int v = 46;

        if (isHovered) v += 40;
        else if (isSelected) v += 20;

        context.drawNineSlicedTexture(WIDGETS_TEXTURE, x, y,
                originalWidth, originalHeight,
                20,
                4,
                200,
                20,
                0, v);
    }

    /**
     * Computes the origin point of the dropdown list based on expansion direction.
     *
     * @return Y coordinate of the dropdown list origin
     */
    private int getDropdownListOrigin() {
        return getY();
    }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {

        TextRenderer textRenderer = Client.mc().textRenderer;
        int x = getX();
        int y = getY();

        boolean isHovered =
                mouseX >= x && mouseX <= x + originalWidth &&
                        mouseY >= y && mouseY <= y + originalHeight;

        // Render the main button area as a dropdown item
        renderDropdownItem(context, x, y, selected, isHovered, false);

        if (displayArrows) {
            renderExpandArrow(context, textRenderer, x, y);
        }
    }

    /**
     * Renders the expand/collapse arrow indicator.
     *
     * @param context      The drawing context
     * @param textRenderer The text renderer
     * @param x            Button X position
     * @param y            Button Y position
     */
    private void renderExpandArrow(DrawContext context, TextRenderer textRenderer, int x, int y) {
        boolean isUpDirection = expandDirection == ExpandDirection.UP_LEFT ||
                expandDirection == ExpandDirection.UP_RIGHT;
        String arrow = expanded
                ? (isUpDirection ? "▼" : "▲")
                : (isUpDirection ? "▲" : "▼");

        int arrowX = x + originalWidth - textRenderer.getWidth(arrow) - 4;
        int arrowY = y + (originalHeight - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(textRenderer, Text.literal(arrow), arrowX, arrowY, ModConstants.COLOR_WHITE);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!expanded) {
            expand();
        } else {
            selectItemAtMousePosition(mouseX, mouseY);
            collapse();
        }
        super.onClick(mouseX, mouseY);
    }

    /**
     * Expands the dropdown to show all options.
     */
    private void expand() {

        List<T> allItems = computeItemList();

        if (allItems.isEmpty() || allItems.size() == 1) return;

        expanded = true;
        scrollbar.resetOffset();

        int visibleCount = Math.min(allItems.size(), maxVisibleOptions);
        this.height = originalHeight + visibleCount * originalHeight;
    }

    /**
     * Selects the item at the current mouse position if within dropdown bounds.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     */
    private void selectItemAtMousePosition(double mouseX, double mouseY) {
        List<T> allItems = computeItemList();

        if (isMouseOver(mouseX, mouseY)) {
            int clickedIndex = (int) ((mouseY - getDropDownTopY(allItems.size())) / originalHeight);
            int actualIndex = scrollbar.getScrollOffset() + clickedIndex;

            if (actualIndex < allItems.size()) {
                T item = allItems.get(actualIndex);
                selected = item;
                if (onSelect != null) {
                    onSelect.accept(item);
                }
            }
        }
    }

    /**
     * Collapses the dropdown to hide options.
     */
    private void collapse() {
        expanded = false;
        height = originalHeight;
    }

    /**
     * Checks if the mouse is over the dropdown widget or its expanded area.
     *
     * @param mouseX The x-coordinate of the mouse cursor
     * @param mouseY The y-coordinate of the mouse cursor
     * @return true if the mouse is over the dropdown or its expanded area, false otherwise
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {

        // Check if mouse is over the main button area
        boolean mouseOver = mouseX >= getX() && mouseY >= getY()
                && mouseX <= getX() + originalWidth
                && mouseY <= getY() + originalHeight;

        if (!mouseOver && expanded) {
            mouseOver = mouseX >= getX() && mouseX <= getX() + width;
            mouseOver = switch (expandDirection) {
                case UP_LEFT -> mouseOver
                        && mouseY <= getY() && mouseY >= getY() - height;
                case UP_RIGHT -> mouseX >= getX()
                        && mouseY <= getY() + height
                        && mouseX <= getX() + width
                        && mouseY < getY() + height;
                case DOWN_RIGHT -> mouseX >= getX()
                        && mouseY >= getY()
                        && mouseX <= getX() + width
                        && mouseY <= getY() + height;
            };
        }

        return mouseOver;
    }

    /**
     * Overrides the default click behaviour to toggle dropdown expansion and handle item selection.
     *
     * @param mouseX The x-coordinate of the mouse click event
     * @param mouseY The y-coordinate of the mouse click event
     * @return true if the click was handled, false otherwise
     */
    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        if (!this.active || !this.visible) {
            return false;
        }

        boolean mouseOver = isMouseOver(mouseX, mouseY);
        if (!mouseOver && expanded) {
            collapse();
        }

        return mouseOver;
    }

    /**
     * Appends narration messages for accessibility, including the default narrations for the dropdown widget.
     *
     * @param builder The narration message builder to which narration messages should be appended
     */
    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    /**
     * Overrides the default mouse scroll behaviour to allow scrolling through dropdown options when expanded.
     *
     * @param mouseX The x-coordinate of the mouse cursor
     * @param mouseY The y-coordinate of the mouse cursor
     * @param amount The amount of scroll (positive for scroll up, negative for scroll down)
     * @return true if the scroll event was handled (i.e., if the dropdown is expanded and has more items than visible), false otherwise
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (expanded) {
            List<T> allItems = computeItemList();

            if (allItems.size() > maxVisibleOptions) {
                scrollbar.setMaxOffset(allItems.size() - maxVisibleOptions);
                return scrollbar.scroll(amount);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    /**
     * Gets the currently selected item in the dropdown.
     *
     * @return The selected item, or null if no item is selected
     */
    @Nullable
    public T getSelected() {
        return selected;
    }

    /**
     * Sets the selected item. If the item is not in the options list, selection is cleared.
     *
     * @param selected The item to select
     */
    public void setSelected(@Nullable T selected) {
        this.selected = (selected == null || options.contains(selected)) ? selected : null;
    }

    /**
     * Defines the direction in which the dropdown menu expands.
     */
    public enum ExpandDirection {
        /**
         * Expands upward and to the left
         */
        UP_LEFT,
        /**
         * Expands upward and to the right
         */
        UP_RIGHT,
        /**
         * Expands downward and to the right
         */
        DOWN_RIGHT
    }

    /**
     * Gets the colour used for text labels in the dropdown items.
     *
     * @return The colour value for label text
     */
    protected int getLabelColor() {

        return ModConstants.COLOR_WHITE;
    }
}