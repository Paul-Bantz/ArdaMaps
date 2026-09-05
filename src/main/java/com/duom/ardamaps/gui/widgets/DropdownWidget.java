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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

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
public class DropdownWidget<T, E extends TextIdentifierPairItem> extends AbstractWidget {

    /** Margin around text inside the dropdown items */
    protected static final int TEXT_MARGIN = 4;

    /** Original height of the Dropdown - ie the height of the button */
    protected final int originalHeight;

    /** Maximum number of visible options before scrolling */
    protected final int maxVisibleOptions;

    /** Text to display when no item is selected */
    protected final Component placeholderText;

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

    /** Scrollbar */
    private final ScrollbarWidget scrollbar = new ScrollbarWidget(2, 8, 0x445D4D35, 0xFF5D4D35, 1);

    /** Original width of the Dropdown - ie the width of the button */
    protected int originalWidth;

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
            Component title,
            Component nullValueText,
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

    /**
     * Sets the width of this widget
     *
     * @param width the width to set
     */
    @Override
    public void setWidth(int width) {

        super.setWidth(width);
        originalWidth = width;
    }

    /**
     * Renders the dropdown widget
     *
     * @param context the draw context
     * @param mouseX  the mouse x position
     * @param mouseY  the mouse y position
     * @param delta   the delta elapsed since the last tick
     */
    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        renderMainButton(context, mouseX, mouseY);

        renderTitle(context);
        List<T> allItems = computeItemList();

        if (expanded) {
            renderExpandedDropdown(context, allItems, mouseX, mouseY);
        }
    }

    /**
     * Renders the title text above the dropdown widget.
     *
     * @param context The drawing context
     */
    @SuppressWarnings("ConstantValue")
    private void renderTitle(GuiGraphicsExtractor context) {

        Component title = getMessage();

        if (title != null) {

            Font textRenderer = Client.mc().font;
            int titleY = getY() - (textRenderer.lineHeight / 2) - 8;
            context.text(textRenderer, title, getX(), titleY, ModConstants.COLOR_WHITE);
        }
    }

    /**
     * Renders the expanded dropdown list with all visible options.
     *
     * @param context The drawing context
     * @param items   The list of all items to display (including null option if allowed)
     * @param mouseX  Current mouse X position
     * @param mouseY  Current mouse Y position
     */
    protected void renderExpandedDropdown(GuiGraphicsExtractor context, List<T> items, int mouseX, int mouseY) {

        var dropDownItems = computeDropdownItems(items);
        int visibleCount = getVisibleDropdownItemCount(dropDownItems);

        if (visibleCount <= 0) return;

        for (int i = 0; i < visibleCount; i++) {

            // Calculate the actual index in the full item list based on scroll offset
            int actualIndex = scrollbar.getScrollOffset() + i;

            if (actualIndex >= dropDownItems.size()) break;

            T dropdownItem = dropDownItems.get(actualIndex);

            int y = getDropdownItemY(i, visibleCount);

            boolean isHovered = isMouseOverItem(mouseX, mouseY, y);
            boolean isSelected = isItemSelected(dropdownItem);

            renderDropdownItem(context, getX(), y, dropdownItem, isHovered, isSelected);
        }

        // Render scrollbar if needed
        if (dropDownItems.size() > visibleCount) {
            int trackX = getX() + originalWidth - 2 - 4;
            int trackY = getDropdownListTopY(visibleCount) + 4;
            int trackHeight = Math.max(0, visibleCount * originalHeight - 8);
            int maxOffset = dropDownItems.size() - visibleCount;
            scrollbar.setMaxOffset(maxOffset);
            scrollbar.render(context, trackX, trackY, trackHeight, visibleCount, dropDownItems.size());
        }
    }

    /**
     * Gets the Y coordinate for a visible expanded-list item.
     *
     * @param visibleIndex Visible index in the expanded list.
     * @param visibleCount Number of visible expanded-list items.
     * @return Top Y coordinate for the visible item.
     */
    protected int getDropdownItemY(int visibleIndex, int visibleCount) {

        return getDropdownListTopY(visibleCount) + visibleIndex * originalHeight;
    }

    /**
     * Gets the top Y coordinate of the expanded list area.
     *
     * @param visibleCount Number of visible expanded-list items.
     * @return Top Y coordinate of the expanded list.
     */
    protected int getDropdownListTopY(int visibleCount) {

        return expandsUp()
                ? getY() - visibleCount * originalHeight
                : getY() + originalHeight;
    }

    /**
     * @return True if this dropdown expands upward.
     */
    protected boolean expandsUp() {

        return expandDirection.equals(ExpandDirection.UP_LEFT) ||
                expandDirection.equals(ExpandDirection.UP_RIGHT);
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
     * Renders the main dropdown button
     *
     * @param context the draw context
     * @param mouseX  the mouse x position
     * @param mouseY  the mouse y position
     */
    protected void renderMainButton(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        Font textRenderer = Client.mc().font;
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
     * Renders a box with optional text and icon.
     *
     * @param context    The drawing context
     * @param x          X position
     * @param y          Y position
     * @param item       The item to render
     * @param isHovered  Whether the mouse is hovering over this item
     * @param isSelected whether this item is selected
     */
    private void renderDropdownItem(GuiGraphicsExtractor context, int x, int y, T item, boolean isHovered, boolean isSelected) {

        Font textRenderer = Client.mc().font;

        E itemPair = optionDisplay.apply(item);

        drawListSlice(context, x, y, isHovered, isSelected);

        var hasIcon = false;

        if (displayIcons) {

            var icon = (item == null) ? placeholderIcon : itemPair.image();

            if (icon != null) {

                if (displayAsSprite)
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, IconSpriteAtlas.retrieveSprite(icon), x + buttonPadding, y + (originalHeight - iconSize) / 2, iconSize, iconSize);
                else
                    context.blit(RenderPipelines.GUI_TEXTURED, icon, x + buttonPadding, y + (originalHeight - iconSize) / 2, 0, 0, iconSize, iconSize, iconSize, iconSize);

                hasIcon = true;
            }
        }

        if (displayLabels) {

            Component display = (item == null) ? placeholderText : itemPair.text();
            int textX = x + TEXT_MARGIN;

            if (hasIcon)
                textX += iconSize + TEXT_MARGIN;

            int textY = y + (originalHeight - textRenderer.lineHeight) / 2;
            context.text(textRenderer, display, textX, textY, getLabelColor(), false);
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
    private void renderExpandArrow(GuiGraphicsExtractor context, Font textRenderer, int x, int y) {
        boolean isUpDirection = expandDirection == ExpandDirection.UP_LEFT ||
                expandDirection == ExpandDirection.UP_RIGHT;
        String arrow = expanded
                ? (isUpDirection ? "▼" : "▲")
                : (isUpDirection ? "▲" : "▼");

        int arrowX = x + originalWidth - textRenderer.width(arrow) - 4;
        int arrowY = y + (originalHeight - textRenderer.lineHeight) / 2;
        context.text(textRenderer, Component.literal(arrow), arrowX, arrowY, ModConstants.COLOR_WHITE);
    }

    /**
     * Draws the given dropdown list item
     *
     * @param context    the draw context
     * @param x          the button x coordinates
     * @param y          the button y coordinates
     * @param isHovered  whether the button is hovered
     * @param isSelected whether the button is selected
     */
    protected void drawListSlice(GuiGraphicsExtractor context, int x, int y, boolean isHovered, boolean isSelected) {

        Identifier sprite = (isHovered || isSelected)
                ? Identifier.withDefaultNamespace("widget/button_highlighted")
                : Identifier.withDefaultNamespace("widget/button");
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, originalWidth, originalHeight);
    }

    /**
     * Gets the colour used for text labels in the dropdown items.
     *
     * @return The colour value for label text
     */
    protected int getLabelColor() {

        return ModConstants.COLOR_WHITE;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (!expanded) {
            expand();
        } else {
            selectItemAtMousePosition(mouseX, mouseY);
            collapse();
        }
        super.onClick(event, doubleClick);
    }

    /**
     * Expands the dropdown to show all options.
     */
    private void expand() {

        List<T> allItems = computeItemList();
        List<T> dropdownItems = computeDropdownItems(allItems);

        if (dropdownItems.isEmpty()) return;

        expanded = true;
        scrollbar.resetOffset();
    }

    /**
     * Selects the item at the current mouse position if within dropdown bounds.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     */
    private void selectItemAtMousePosition(double mouseX, double mouseY) {
        List<T> allItems = computeItemList();
        List<T> dropdownItems = computeDropdownItems(allItems);
        int visibleCount = getVisibleDropdownItemCount(dropdownItems);

        if (visibleCount > 0 && isMouseOver(mouseX, mouseY)) {
            int clickedIndex = (int) ((mouseY - getDropdownListTopY(visibleCount)) / originalHeight);
            int actualIndex = scrollbar.getScrollOffset() + clickedIndex;

            if (clickedIndex >= 0 && clickedIndex < visibleCount && actualIndex < dropdownItems.size()) {
                T item = dropdownItems.get(actualIndex);
                selected = item;
                if (onSelect != null) {
                    onSelect.accept(item);
                }
            }
        }
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
            List<T> dropdownItems = computeDropdownItems(computeItemList());
            int visibleCount = getVisibleDropdownItemCount(dropdownItems);
            int listTop = getDropdownListTopY(visibleCount);
            int listBottom = listTop + visibleCount * originalHeight;

            mouseOver = visibleCount > 0
                    && mouseX >= getX()
                    && mouseX <= getX() + originalWidth
                    && mouseY >= listTop
                    && mouseY <= listBottom;
        }

        return mouseOver;
    }

    /**
     * Collapses the dropdown to hide options.
     */
    private void collapse() {
        expanded = false;
    }

    /**
     * Appends narration messages for accessibility, including the default narrations for the dropdown widget.
     *
     * @param builder The narration message builder to which narration messages should be appended
     */
    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }

    /**
     * Overrides the default mouse scroll behaviour to allow scrolling through dropdown options when expanded.
     *
     * @param mouseX           The x-coordinate of the mouse cursor
     * @param mouseY           The y-coordinate of the mouse cursor
     * @param horizontalAmount The amount of horizontal scroll (positive for scroll up, negative for scroll down)
     * @param verticalAmount   The amount of vertical scroll (positive for scroll up, negative for scroll down)
     * @return true if the scroll event was handled (i.e., if the dropdown is expanded and has more items than visible), false otherwise
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (expanded) {
            List<T> dropdownItems = computeDropdownItems(computeItemList());
            int visibleCount = getVisibleDropdownItemCount(dropdownItems);

            if (dropdownItems.size() > visibleCount) {
                scrollbar.setMaxOffset(dropdownItems.size() - visibleCount);
                return scrollbar.scroll(verticalAmount);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Builds the list of options actually rendered in the expanded dropdown.
     * The selected value is displayed on the button, so it is excluded from the list.
     *
     * @param items All logical dropdown items.
     * @return Items rendered in the expanded list.
     */
    protected List<T> computeDropdownItems(List<T> items) {

        var dropdownItems = new ArrayList<>(items);
        dropdownItems.remove(selected);

        return dropdownItems;
    }

    /**
     * Builds the complete list of items including null option if allowed.
     *
     * @return List of all items to display
     */
    protected List<T> computeItemList() {

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
            E itemPairB = optionDisplay.apply(b);
            String textA = itemPairA.text().getString();
            String textB = itemPairB.text().getString();

            // alphabetical by name()
            return textA.compareToIgnoreCase(textB);
        });

        return allItems;
    }

    /**
     * Computes how many expanded-list items are visible before scrolling is needed.
     *
     * @param dropdownItems Items rendered in the expanded list.
     * @return Visible expanded item count.
     */
    protected int getVisibleDropdownItemCount(List<T> dropdownItems) {

        return Math.min(dropdownItems.size(), Math.max(0, maxVisibleOptions));
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
}
