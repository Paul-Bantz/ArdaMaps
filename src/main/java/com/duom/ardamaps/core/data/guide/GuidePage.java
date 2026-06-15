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
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * Represents a top-level page (category) in the guide.
 * Each page has a display title and a list of {@link GuideEntry} items
 * that appear as navigation buttons in the right column when this page is selected.
 */
public class GuidePage {

    /** Stable identifier used to build guide deep-links, e.g. {@code "start_here"}. Optional in JSON. */
    @Getter
    @SerializedName("id")
    private String id;

    /** Display title shown on the page toggle button. */
    @Setter
    @Getter
    private transient String title;

    /** Ordered list of entries belonging to this page. */
    @SerializedName("entries")
    private List<GuideEntry> entries;

    /** No-arg constructor required by Gson. */
    public GuidePage() {}

    /**
     * @return an unmodifiable view of the entries in this page; never {@code null}
     */
    public List<GuideEntry> getEntries() {
        return entries == null ? Collections.emptyList() : Collections.unmodifiableList(entries);
    }
}
