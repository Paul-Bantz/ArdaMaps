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

package com.duom.ardamaps.core.data.guide;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * Root data structure that mirrors the layout of {@code assets/ardamaps/guide/guide.json}.
 * Contains an ordered list of {@link GuidePage} objects which are used to drive
 * the two-column navigation in {@link com.duom.ardamaps.gui.screens.GuideScreen}.
 * HTML content is never stored here; it is loaded on demand via GuideLoader.
 */
public class GuideBook {

    /** Ordered list of top-level pages in the guide. */
    @SerializedName("pages")
    private List<GuidePage> pages;

    /** No-arg constructor required by Gson. */
    public GuideBook() {}

    /**
     * @return an unmodifiable view of all pages; never {@code null}
     */
    public List<GuidePage> getPages() {
        return pages == null ? Collections.emptyList() : Collections.unmodifiableList(pages);
    }
}

