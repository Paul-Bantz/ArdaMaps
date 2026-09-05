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

package com.duom.ardamaps.core.data.conversion;

import org.junit.jupiter.api.Test;

import static com.duom.ardamaps.core.data.conversion.ContentBlock.ImageBlock.Align;
import static com.duom.ardamaps.core.data.conversion.ContentBlock.TitleBlock.Type;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTML Content block parser tests
 */
class ContentBlockTest {

    /**
     * Image block alignment validation
     */
    @Test
    void align_fromString_null_returnsLeft() {
        // An HTML <img> with no align attribute passes null here.
        // Defaulting to LEFT keeps images left-aligned rather than throwing.
        assertEquals(Align.LEFT, Align.fromString(null));
    }

    /**
     * Image block alignment defaults to LEFT
     */
    @Test
    void align_fromString_empty_returnsLeft() {
        // An empty attribute value is equally legal from the HTML parser.
        assertEquals(Align.LEFT, Align.fromString(""));
    }

    /**
     * Image block alignment CENTRE validation
     */
    @Test
    void align_fromString_center_returnsCenter() {
        // The most common attribute value for centred images in the guide.
        assertEquals(Align.CENTER, Align.fromString("center"));
    }

    /**
     * Image block right alignment validation
     */
    @Test
    void align_fromString_right_returnsRight() {
        assertEquals(Align.RIGHT, Align.fromString("right"));
    }

    /**
     * Validate that alignment parsing is case-insensitive
     */
    @Test
    void align_fromString_isCaseInsensitive() {
        // HTML attributes are case-insensitive; "CENTER" must equal "centre".
        // Failing this causes correctly-authored guide HTML to render left-aligned.
        assertEquals(Align.CENTER, Align.fromString("CENTER"));
        assertEquals(Align.RIGHT, Align.fromString("RIGHT"));
    }

    /**
     * Validate typo error on image alignment
     */
    @Test
    void align_fromString_unknownValue_returnsLeft() {
        // Any typo (e.g. "middle") silently falls back to LEFT rather than throwing,
        // so a single bad attribute cannot break the whole guide page render.
        assertEquals(Align.LEFT, Align.fromString("middle"));
        assertEquals(Align.LEFT, Align.fromString("justify"));
    }

    /**
     * Validates default title to H1
     */
    @Test
    void titleType_fromString_null_returnsH1() {
        // null is a special case that returns H1 (largest title), NOT H3 (the switch default).
        // This documents that null is intentionally the primary heading fallback.
        assertEquals(Type.H1, Type.fromString(null));
    }

    /**
     * Validates unknown types defaults to H3
     */
    @Test
    void titleType_fromString_unknownTag_returnsH3() {
        // Any unrecognized heading tag (h4, h5, h6, …) safely downgrades to H3.
        // Without this test a regression could silently map h4 back to H1.
        assertEquals(Type.H3, Type.fromString("h4"));
        assertEquals(Type.H3, Type.fromString("h5"));
        assertEquals(Type.H3, Type.fromString("h6"));
        assertEquals(Type.H3, Type.fromString("span")); // non-heading tags also degrade
    }
}
