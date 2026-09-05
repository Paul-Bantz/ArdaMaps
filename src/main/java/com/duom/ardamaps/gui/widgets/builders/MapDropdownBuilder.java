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
import com.duom.ardamaps.gui.widgets.MapDropdownWidget;
import com.duom.ardamaps.gui.widgets.TextIdentifierPairItem;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builder class for constructing {@link MapDropdownWidget} instances with a fluent API.
 * Provides convenient methods to configure all aspects of a map dropdown widget including
 * position, size, options, display settings, and behaviour.
 *
 * @param <T> The type of items stored in the dropdown
 * @param <E> The type of display pair (must extend {@link TextIdentifierPairItem})
 */
public class MapDropdownBuilder<T, E extends TextIdentifierPairItem> extends DropdownBuilder<T,E> {

    /**
     * Creates a new instance of the builder.
     *
     * @param <T> The type of items in the dropdown
     * @param <E> The type of display pair
     * @return A new DropdownBuilder instance
     */
    public static <T, E extends TextIdentifierPairItem> MapDropdownBuilder<T, E> create() {
        return new MapDropdownBuilder<>();
    }

    /**
     * Sets the size of the dropdown widget.
     *
     * @param width  The width of the widget
     * @param height The height of each item in the widget
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setSize(int width, int height) {
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
    public MapDropdownBuilder<T, E> setOptions(List<T> options) {
        this.options = options;
        return this;
    }

    /**
     * Sets the function that converts items to their display representation.
     *
     * @param optionDisplay Function that maps items to display pairs
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setOptionDisplay(Function<T, E> optionDisplay) {
        this.optionDisplay = optionDisplay;
        return this;
    }

    /**
     * Sets whether icons should be displayed for each option.
     *
     * @param displayIcons true to display icons, false otherwise
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setDisplayIcons(boolean displayIcons) {
        this.displayIcons = displayIcons;
        return this;
    }

    /**
     * Sets whether text labels should be displayed for each option.
     *
     * @param displayLabels true to display labels, false otherwise
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setDisplayLabels(boolean displayLabels) {
        this.displayLabels = displayLabels;
        return this;
    }

    /**
     * Sets whether expand/collapse arrows should be displayed.
     *
     * @param displayArrows true to display arrows, false otherwise
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setDisplayArrows(boolean displayArrows) {
        this.displayArrows = displayArrows;
        return this;
    }

    /**
     * Sets the initially selected item in the dropdown.
     *
     * @param selected The item to select initially (can be null)
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setSelected(T selected) {
        this.selected = selected;
        return this;
    }

    /**
     * Sets the callback function invoked when an item is selected.
     *
     * @param onSelect Consumer that handles selection events
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setOnSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /**
     * Sets whether null (no selection) is allowed as a valid option.
     *
     * @param allowNull true to allow null selection, false otherwise
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setAllowNull(boolean allowNull) {
        this.allowNull = allowNull;
        return this;
    }

    /**
     * Sets the text displayed when no item is selected.
     *
     * @param placeholderText The placeholder text
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setPlaceholderText(Text placeholderText) {
        this.placeholderText = placeholderText;
        return this;
    }

    /**
     * Sets the icon displayed when no item is selected.
     *
     * @param placeholderIcon The placeholder icon
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setPlaceholderIcon(Identifier placeholderIcon) {
        this.placeholderIcon = placeholderIcon;
        return this;
    }

    /**
     * Sets whether the dropdown should display options as sprites (icons) instead of text.
     *
     * @param displayAsSprite true to display options as sprites, false to display as text
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setDisplayAsSprite(boolean displayAsSprite) {
        this.displayAsSprite = displayAsSprite;
        return this;
    }

    /**
     * Sets the maximum number of options visible before scrolling is enabled.
     *
     * @param maxVisibleOptions Maximum number of visible options
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setMaxVisibleOptions(int maxVisibleOptions) {
        this.maxVisibleOptions = maxVisibleOptions;
        return this;
    }

    /**
     * Sets the direction in which the dropdown expands when opened.
     *
     * @param expandDirection The expansion direction
     * @return This builder for method chaining
     */
    public MapDropdownBuilder<T, E> setExpandDirection(MapDropdownWidget.ExpandDirection expandDirection) {
        this.expandDirection = expandDirection;
        return this;
    }

    /**
     * Builds and returns a configured {@link DropdownWidget} instance.
     *
     * @return A new DropdownWidget with the configured settings
     */
    public MapDropdownWidget<T, E> build() {
        return new MapDropdownWidget<>(
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
