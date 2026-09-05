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

/**
 * Enum representing different types of bookmark buttons in the GUI.
 * Each type corresponds to a specific action or functionality related to bookmarks.
 */
public enum BookmarkButtonType {

    BOOKMARK_CLOSE("ardamaps.client.map.screen.generic.close"),
    BOOKMARK_CONFIGURATION("ardamaps.client.map.screen.configuration"),
    BOOKMARK_GUIDE("ardamaps.client.map.screen.guide"),
    BOOKMARK_MAP("ardamaps.client.map.screen.map");

    private final String translationKey;

    BookmarkButtonType(String translationKey){
        this.translationKey = translationKey;
    }

    public Text getTranslation(){

        return Text.translatable(translationKey);
    }
}
