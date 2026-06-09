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

import com.duom.ardamaps.gui.widgets.CheckboxWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Builder class for creating CheckboxWidget instances with a fluent interface.
 */
public class CheckboxBuilder {

    /** width of the checkbox */
    private int width;

    /** height of the checkbox */
    private int height;

    /** text label of the checkbox */
    private Text text = Text.empty();

    /** whether the checkbox is checked or not */
    private boolean checked = false;

    /** callback function to be called when the checkbox state changes */
    private Consumer<Boolean> onChange = null;

    /** Private constructor to prevent direct instantiation. Use the static create() method instead. */
    private CheckboxBuilder() {
    }

    /** Static factory method to create a new instance of CheckboxBuilder. */
    public static CheckboxBuilder create() {
        return new CheckboxBuilder();
    }

    /**
     * Sets the size of the checkbox.
     *
     * @param width  width of the checkbox
     * @param height height of the checkbox
     * @return the current instance of CheckboxBuilder for method chaining
     */
    public CheckboxBuilder setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Sets the text label of the checkbox.
     *
     * @param text text label of the checkbox
     * @return the current instance of CheckboxBuilder for method chaining
     */
    @SuppressWarnings("unused")
    public CheckboxBuilder setText(Text text) {
        this.text = text;
        return this;
    }

    /**
     * Sets whether the checkbox is checked or not.
     *
     * @param checked whether the checkbox is checked or not
     * @return the current instance of CheckboxBuilder for method chaining
     */
    public CheckboxBuilder setChecked(boolean checked) {
        this.checked = checked;
        return this;
    }

    /**
     * Sets the callback function to be called when the checkbox state changes.
     *
     * @param onChange callback function that takes a boolean parameter indicating the new state of the checkbox
     * @return the current instance of CheckboxBuilder for method chaining
     */
    public CheckboxBuilder setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    /**
     * Builds and returns a new instance of CheckboxWidget with the specified properties.
     *
     * @return a new instance of CheckboxWidget
     */
    public CheckboxWidget build() {

        return new CheckboxWidget(0, 0, width, height, text, checked, true, onChange);
    }
}