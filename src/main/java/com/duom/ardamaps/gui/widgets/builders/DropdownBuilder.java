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

package com.duom.ardamaps.gui.widgets.builders;

import com.duom.ardamaps.gui.widgets.DropdownWidget;
import com.duom.ardamaps.gui.widgets.TextIdentifierPairItem;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builder class for constructing {@link DropdownWidget} instances with a fluent API.
 * Provides convenient methods to configure all aspects of a dropdown widget including
 * position, size, options, display settings, and behaviour.
 *
 * @param <T> The type of items stored in the dropdown
 * @param <E> The type of display pair (must extend {@link TextIdentifierPairItem})
 */
@SuppressWarnings("unused")
public class DropdownBuilder<T, E extends TextIdentifierPairItem> {

    /** x coordinate of the dropdown widget */
    protected int x;

    /** y coordinate of the dropdown widget */
    protected int y;

    /** width of the dropdown widget */
    protected int width;

    /** height of each item in the dropdown widget */
    protected int height;

    /** title text displayed above the dropdown widget */
    protected Text title;

    /** list of options available in the dropdown widget */
    protected List<T> options = List.of();

    /** function that maps items to their display representation (text and optional icon) */
    protected Function<T, E> optionDisplay;

    /** currently selected item in the dropdown widget (can be null if no selection) */
    protected T selected = null;

    /** callback function invoked when an item is selected from the dropdown widget */
    protected Consumer<T> onSelect = null;

    /** whether null (no selection) is allowed as a valid option in the dropdown widget */
    protected boolean allowNull = false;

    /** text displayed when no item is selected in the dropdown widget */
    protected Text placeholderText = Text.literal("None");

    /** icon displayed when no item is selected in the dropdown widget */
    protected Identifier placeholderIcon = null;

    /** whether the dropdown widget should start in an expanded state when created */
    protected final boolean expanded = false;

    /** maximum number of options visible in the dropdown widget before scrolling is enabled */
    protected int maxVisibleOptions = 8;

    /** direction in which the dropdown widget expands when opened */
    protected DropdownWidget.ExpandDirection expandDirection = DropdownWidget.ExpandDirection.DOWN_RIGHT;

    /** whether to display icons for each option in the dropdown widget (if available) */
    protected boolean displayIcons = false;

    /** whether to display text labels for each option in the dropdown widget */
    protected boolean displayLabels = true;

    /** whether to display expand/collapse arrows for the dropdown widget */
    protected boolean displayArrows = true;

    protected boolean displayAsSprite = false;

    /** Private constructor to prevent direct instantiation. Use the static create() method instead. */
    protected DropdownBuilder() {}

    /**
     * Creates a new instance of the builder.
     *
     * @param <T> The type of items in the dropdown
     * @param <E> The type of display pair
     * @return A new DropdownBuilder instance
     */
    public static <T, E extends TextIdentifierPairItem> DropdownBuilder<T, E> create() {
        return new DropdownBuilder<>();
    }

    /**
     * Sets the size of the dropdown widget.
     *
     * @param width  The width of the widget
     * @param height The height of each item in the widget
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Sets the list of options available in the dropdown.
     *
     * @param options The list of options
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setOptions(List<T> options) {
        this.options = options;
        return this;
    }

    /**
     * Sets the function that converts items to their display representation.
     *
     * @param optionDisplay Function that maps items to display pairs
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setOptionDisplay(Function<T, E> optionDisplay) {
        this.optionDisplay = optionDisplay;
        return this;
    }

    /**
     * Sets whether icons should be displayed for each option.
     *
     * @param displayIcons true to display icons, false otherwise
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setDisplayIcons(boolean displayIcons) {
        this.displayIcons = displayIcons;
        return this;
    }

    /**
     * Sets whether text labels should be displayed for each option.
     *
     * @param displayLabels true to display labels, false otherwise
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setDisplayLabels(boolean displayLabels) {
        this.displayLabels = displayLabels;
        return this;
    }

    /**
     * Sets whether expand/collapse arrows should be displayed.
     *
     * @param displayArrows true to display arrows, false otherwise
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setDisplayArrows(boolean displayArrows) {
        this.displayArrows = displayArrows;
        return this;
    }

    /**
     * Sets the initially selected item in the dropdown.
     *
     * @param selected The item to select initially (can be null)
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setSelected(T selected) {
        this.selected = selected;
        return this;
    }

    /**
     * Sets the callback function invoked when an item is selected.
     *
     * @param onSelect Consumer that handles selection events
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setOnSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /**
     * Sets whether null (no selection) is allowed as a valid option.
     *
     * @param allowNull true to allow null selection, false otherwise
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setAllowNull(boolean allowNull) {
        this.allowNull = allowNull;
        return this;
    }

    /**
     * Sets the text displayed when no item is selected.
     *
     * @param placeholderText The placeholder text
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setPlaceholderText(Text placeholderText) {
        this.placeholderText = placeholderText;
        return this;
    }

    /**
     * Sets the icon displayed when no item is selected.
     *
     * @param placeholderIcon The placeholder icon
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setPlaceholderIcon(Identifier placeholderIcon) {
        this.placeholderIcon = placeholderIcon;
        return this;
    }

    /**
     * Sets whether the dropdown should display options as sprites (icons) instead of text.
     *
     * @param displayAsSprite true to display options as sprites, false to display as text
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setDisplayAsSprite(boolean displayAsSprite) {
        this.displayAsSprite = displayAsSprite;
        return this;
    }

    /**
     * Sets the maximum number of options visible before scrolling is enabled.
     *
     * @param maxVisibleOptions Maximum number of visible options
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setMaxVisibleOptions(int maxVisibleOptions) {
        this.maxVisibleOptions = maxVisibleOptions;
        return this;
    }

    /**
     * Sets the direction in which the dropdown expands when opened.
     *
     * @param expandDirection The expansion direction
     * @return This builder for method chaining
     */
    public DropdownBuilder<T, E> setExpandDirection(DropdownWidget.ExpandDirection expandDirection) {
        this.expandDirection = expandDirection;
        return this;
    }

    /**
     * Builds and returns a configured {@link DropdownWidget} instance.
     *
     * @return A new DropdownWidget with the configured settings
     */
    public DropdownWidget<T, E> build() {
        return new DropdownWidget<>(
                x,
                y,
                width,
                height,
                title,
                placeholderText,
                placeholderIcon,
                options,
                optionDisplay,
                selected,
                onSelect,
                allowNull,
                expanded,
                displayAsSprite,
                displayLabels,
                displayIcons,
                displayArrows,
                expandDirection,
                maxVisibleOptions
        );
    }
}