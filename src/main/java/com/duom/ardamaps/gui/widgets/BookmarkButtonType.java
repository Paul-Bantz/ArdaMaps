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

import net.minecraft.network.chat.Component;

/**
 * Enum representing different types of bookmark buttons in the GUI.
 * Each type corresponds to a specific action or functionality related to bookmarks.
 */
public enum BookmarkButtonType {

    /** Close button bookmark type. */
    BOOKMARK_CLOSE("ardamaps.client.map.screen.generic.close"),

    /** Configuration button bookmark type. */
    BOOKMARK_CONFIGURATION("ardamaps.client.map.screen.configuration"),

    /** Guide button bookmark type. */
    BOOKMARK_GUIDE("ardamaps.client.map.screen.guide"),

    /** Map button bookmark type. */
    BOOKMARK_MAP("ardamaps.client.map.screen.map");

    /** Translation key for this bookmark button type. */
    private final String translationKey;

    /**
     * Creates a new BookmarkButtonType with the specified translation key.
     *
     * @param translationKey the translation key for this bookmark button type
     */
    BookmarkButtonType(String translationKey) {
        this.translationKey = translationKey;
    }

    /**
     * Gets the translatable component for this bookmark button type.
     *
     * @return The translated component for this button type
     */
    public Component getTranslation() {

        return Component.translatable(translationKey);
    }
}
