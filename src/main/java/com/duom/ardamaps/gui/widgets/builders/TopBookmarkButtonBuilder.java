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

import com.duom.ardamaps.gui.widgets.TopBookmarkButtonType;
import com.duom.ardamaps.gui.widgets.TopBookmarkButtonWidget;

/**
 * Builder for top bookmark button widgets.
 */
public class TopBookmarkButtonBuilder {

    /** The x-coordinate of the button's position. */
    private int x;

    /** The y-coordinate of the button's position. */
    private int y;

    /** The width of the button. */
    private int width;

    /** Source-texture icon size. */
    private int iconSize = TopBookmarkButtonWidget.DEFAULT_SRC_ICON_SIZE;

    /** The style of the button. */
    private TopBookmarkButtonType style = TopBookmarkButtonType.BOOKMARK_CLOSE;

    /** The Runnable that defines the action to be performed when the button is clicked. */
    private Runnable onSelect;

    /** Private constructor to enforce the use of the static create() method for instantiation. */
    private TopBookmarkButtonBuilder() {
    }

    /**
     * Static factory method to create a new instance of TopBookmarkButtonBuilder.
     *
     * @return A new top bookmark button builder.
     */
    public static TopBookmarkButtonBuilder create() {

        return new TopBookmarkButtonBuilder();
    }

    /**
     * Sets the position of the button using x and y coordinates.
     *
     * @param x The x-coordinate of the button's position.
     * @param y The y-coordinate of the button's position.
     * @return The current instance of TopBookmarkButtonBuilder for method chaining.
     */
    public TopBookmarkButtonBuilder setPosition(int x, int y) {

        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Sets the destination width of the button.
     *
     * @param width The width of the button.
     * @return The current instance of TopBookmarkButtonBuilder for method chaining.
     */
    public TopBookmarkButtonBuilder setWidth(int width) {

        this.width = width;
        return this;
    }

    /**
     * Sets the icon size in texture scale, relative to the 96 px source cap.
     *
     * @param sourceSize The source-space icon size.
     * @return The current instance of TopBookmarkButtonBuilder for method chaining.
     */
    public TopBookmarkButtonBuilder setIconSize(int sourceSize) {

        this.iconSize = sourceSize;
        return this;
    }

    /**
     * Sets the action to be performed when the button is clicked.
     *
     * @param onClick A Runnable that defines the click behaviour of the button.
     * @return The current instance of TopBookmarkButtonBuilder for method chaining.
     */
    public TopBookmarkButtonBuilder setOnClick(Runnable onClick) {

        this.onSelect = onClick;
        return this;
    }

    /**
     * Sets the style of the button using a top bookmark button type.
     *
     * @param style The button type that defines the visual style.
     * @return The current instance of TopBookmarkButtonBuilder for method chaining.
     */
    public TopBookmarkButtonBuilder setButtonStyle(TopBookmarkButtonType style) {

        this.style = style;
        return this;
    }

    /**
     * Builds and returns a new instance of TopBookmarkButtonWidget using the configured properties.
     *
     * @return A new TopBookmarkButtonWidget instance with the specified attributes.
     */
    public TopBookmarkButtonWidget build() {

        TopBookmarkButtonWidget widget = new TopBookmarkButtonWidget(x, y, width, style, onSelect);
        widget.setIconSize(iconSize);

        return widget;
    }
}
