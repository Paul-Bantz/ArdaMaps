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

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A simple data class that holds a text and an identifier, used for displaying a list of items with associated icons in the map GUI.
 * This class is immutable and provides a convenient way to pair a text label with an image identifier for rendering in the GUI.
 */
public record TextIdentifierPairItem(Text text, Identifier image) {

    /**
     * Constructs a new TextIdentifierPairItem with the specified text and image identifier.
     * If the provided text is null, it defaults to an empty Text instance.
     *
     * @param text  the text label for this item, can be null (defaults to empty)
     * @param image the identifier for the associated image/icon
     */
    public TextIdentifierPairItem(String text, Identifier image) {

        this(text != null ? Text.literal(text) : Text.empty(), image);
    }
}