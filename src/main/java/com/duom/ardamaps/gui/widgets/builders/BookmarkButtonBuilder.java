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

import com.duom.ardamaps.gui.widgets.BookmarkButtonType;
import com.duom.ardamaps.gui.widgets.BookmarkButtonWidget;

/**
 * The BookmarkButtonBuilder class provides a builder pattern for creating instances of BookmarkButtonWidget
 * with customizable properties such as position, size, style, and click behaviour.
 * It allows for a fluent interface to set various attributes of the button before constructing the final widget instance.
 */
public class BookmarkButtonBuilder {

    /** The x-coordinate of the button's position. */
    private int x;

    /** The y-coordinate of the button's position. */
    private int y;

    /** The width of the button. */
    private int width;

    /** The height of the button. */
    private int height;

    /** The style of the button, defined by the ButtonType enum. */
    private BookmarkButtonType style = BookmarkButtonType.BOOKMARK_CLOSE;

    /** The Runnable that defines the action to be performed when the button is clicked. */
    private Runnable onSelect;

    /** Private constructor to enforce the use of the static create() method for instantiation. */
    private BookmarkButtonBuilder(){}

    /** Static factory method to create a new instance of BookmarkButtonBuilder. */
    public static BookmarkButtonBuilder create(){ return new BookmarkButtonBuilder(); }

    /**
     * Sets the position of the button using x and y coordinates.
     * @param x The x-coordinate of the button's position.
     * @param y The y-coordinate of the button's position.
     * @return The current instance of BookmarkButtonBuilder for method chaining.
     */
    public BookmarkButtonBuilder setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Sets the size of the button using width and height.
     * @param width The width of the button.
     * @param height The height of the button.
     * @return The current instance of BookmarkButtonBuilder for method chaining.
     */
    public BookmarkButtonBuilder setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Sets the action to be performed when the button is clicked.
     * @param onClick A Runnable that defines the click behaviour of the button.
     * @return The current instance of BookmarkButtonBuilder for method chaining.
     */
    public BookmarkButtonBuilder setOnClick(Runnable onClick) {
        this.onSelect = onClick;
        return this;
    }

    /**
     * Sets the style of the button using a ButtonType enum value.
     * @param style The ButtonType that defines the visual style of the button.
     * @return The current instance of BookmarkButtonBuilder for method chaining.
     */
    public BookmarkButtonBuilder setButtonStyle(BookmarkButtonType style) {
        this.style = style;
        return this;
    }

    /**
    * Builds and returns a new instance of BookmarkButtonWidget using the configured properties.
    * @return A new BookmarkButtonWidget instance with the specified attributes.
    */
    public BookmarkButtonWidget build()
    {
        return new BookmarkButtonWidget(
                x,
                y,
                width,
                height,
                style,
                onSelect
        );
    }
}