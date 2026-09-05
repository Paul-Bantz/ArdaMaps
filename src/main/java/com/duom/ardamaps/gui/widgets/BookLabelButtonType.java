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

import com.duom.ardamaps.gui.ModConstants;
import lombok.Getter;
import net.minecraft.resources.Identifier;

/**
 * Visual style for book-label buttons protruding from the book edge.
 */
public enum BookLabelButtonType {

    /** Blue book-label button type. */
    BLUE(ModConstants.SIDE_MAP_BUTTON_BLUE_TEXTURE, ModConstants.SIDE_MAP_BUTTON_BLUE_HIGHLIGHT_TEXTURE);

    /** Texture used when the button is idle. */
    @Getter
    private final Identifier texture;

    /** Texture used when the button is hovered or open. */
    @Getter
    private final Identifier highlightTexture;

    /**
     * Creates a new book-label button type.
     *
     * @param texture          Texture used when idle.
     * @param highlightTexture Texture used when hovered or open.
     */
    BookLabelButtonType(Identifier texture, Identifier highlightTexture) {

        this.texture = texture;
        this.highlightTexture = highlightTexture;
    }
}
