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

import com.duom.ardamaps.gui.widgets.StyledButtonWidget;
import net.minecraft.text.Text;

/**
 * Builder class for constructing {@link StyledButtonWidget} instances with a fluent API.
 * Provides convenient methods to configure all aspects of a styled button widget including
 * position, size, text, style, and behaviour.
 */
public class StyledButtonBuilder {

    /** width of the button widget */
    private int width;

    /** height of the button widget */
    private int height;

    /** text displayed on the button widget */
    private Text text = Text.empty();

    /** Button style */
    private StyledButtonWidget.Style style = StyledButtonWidget.Style.DEFAULT;

    /** callback function invoked when the button widget is clicked */
    private Runnable onSelect;

    /** Private constructor to prevent direct instantiation. Use the static create() method instead. */
    private StyledButtonBuilder() { }

    /** Static factory method to create a new instance of StyledButtonBuilder. */
    public static StyledButtonBuilder create(){ return new StyledButtonBuilder(); }

    /**
     * Sets the size of the button widget.
     *
     * @param width  width of the button widget
     * @param height height of the button widget
     * @return the current instance of StyledButtonBuilder for method chaining
     */
    public StyledButtonBuilder setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Sets the callback function to be called when the button widget is clicked.
     *
     * @param onClick callback function to be called when the button widget is clicked
     * @return the current instance of StyledButtonBuilder for method chaining
     */
    public StyledButtonBuilder setOnClick(Runnable onClick) {
        this.onSelect = onClick;
        return this;
    }

    /**
     * Sets the text of the button widget.
     * @param text displayed on the button widget
     * @return the current instance of StyledButtonBuilder for method chaining
     */
    public StyledButtonBuilder setText(Text text) {
        this.text = text;
        return this;
    }

    /**
     * Sets the style of the button widget.
     * @param style display style
     * @return the current instance of StyledButtonBuilder for method chaining
     */
    public StyledButtonBuilder setStyle(StyledButtonWidget.Style style) {
        this.style = style;
        return this;
    }

    /**
     * Builds and returns a new instance of StyledButtonWidget based on the configured properties of this builder.
     * @return a new instance of StyledButtonWidget with the configured properties
     */
    public StyledButtonWidget build()
    {
        return new StyledButtonWidget(
                0,
                0,
                width,
                height,
                onSelect,
                style,
                text
        );
    }
}