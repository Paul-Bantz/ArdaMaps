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

package com.duom.ardamaps.gui.screens.rendering;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Background renderer validation
 * Tests that the screen ratio is correctly calculated and applied.
 */
class BackgroundRendererTest {

    /**
     * Validate 16:9 aspect ratio calculations for the background image.
     * 1920×1080 is exactly 16:9. The entire screen height is used as bookH, o both page halves must fit without letterboxing.
     * pageHeight = bookH - 2*UI_MARGIN = 1080 - 50 = 1030
     * pageWidth  = bookW/2 - UI_MARGIN = 1920/2 - 25 = 935
     * Wrong values here misalign every text column, button, and image in the GUI.
     *
     * @throws Exception on reflective value get error
     */
    @Test
    void invalidate_exactlySixteenByNine_pagesSizedCorrectly() throws Exception {

        BackgroundRenderer renderer = new BackgroundRenderer();
        renderer.invalidate(1920, 1080);

        assertEquals(1030, getInt(renderer, "pageHeight"),
                "1920×1080: pageHeight must be screen height minus two margins");
        assertEquals(935, getInt(renderer, "pageWidth"),
                "1920×1080: pageWidth must be half of bookW minus one margin");
    }

    /**
     * Reflective helper to read a private int field from the renderer.
     *
     * @param renderer  the renderer
     * @param fieldName the name of the private int field to read
     */
    private static int getInt(BackgroundRenderer renderer, String fieldName) throws Exception {
        Field field = BackgroundRenderer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (int) field.get(renderer);
    }

    /**
     * Validate 16:9 aspect ratio calculations for the background image.
     * 2560×1080 (ultra-wide, ~21:9). Extra width is letterboxed; the book is still
     * height-constrained at 1080 px.  Page dimensions must match the 1920×1080 case.
     *
     * @throws Exception on reflective value get error
     */
    @Test
    void invalidate_widerThanSixteenByNine_sameDimensionsAsExactAspect() throws Exception {

        BackgroundRenderer renderer = new BackgroundRenderer();
        renderer.invalidate(2560, 1080);

        assertEquals(1030, getInt(renderer, "pageHeight"),
                "Ultra-wide: letterboxed book must use the same page height as 1080p");
        assertEquals(935, getInt(renderer, "pageWidth"),
                "Ultra-wide: letterboxed book must use the same page width as 1080p");
    }

    /**
     * Validate 16:9 aspect ratio calculations for the background image.
     * 800×600 (4:3 monitor). Width is the limiting factor:
     * bookW = 800, bookH = round(800 / (16/9)) = round(450) = 450
     * pageHeight = 450 - 50 = 400
     * pageWidth  = 800/2 - 25 = 375
     *
     * @throws Exception on reflective value get error
     */
    @Test
    void invalidate_fourByThree_widthConstrained() throws Exception {

        BackgroundRenderer renderer = new BackgroundRenderer();
        renderer.invalidate(800, 600);

        assertEquals(400, getInt(renderer, "pageHeight"),
                "4:3 screen: book is width-constrained; pageHeight must reflect smaller bookH");
        assertEquals(375, getInt(renderer, "pageWidth"),
                "4:3 screen: book is width-constrained; pageWidth must be half of screen width minus margin");
    }

    /**
     * Cache validation
     * The early-exit guard prevents redundant layout recalculations every frame.
     * This test verifies that the cached fields are stable (same values) on a second call,
     * not that the calculation is skipped - we cannot directly observe the skip from outside.
     *
     * @throws Exception on reflective value get error
     */
    @Test
    void invalidate_calledTwiceWithSameDimensions_doesNotRecalculate() throws Exception {

        BackgroundRenderer renderer = new BackgroundRenderer();
        renderer.invalidate(1920, 1080);
        int ph1 = getInt(renderer, "pageHeight");
        int pw1 = getInt(renderer, "pageWidth");

        renderer.invalidate(1920, 1080); // same dimensions
        int ph2 = getInt(renderer, "pageHeight");
        int pw2 = getInt(renderer, "pageWidth");

        assertEquals(ph1, ph2, "pageHeight must be stable across repeated invalidate() calls with same dimensions");
        assertEquals(pw1, pw2, "pageWidth must be stable across repeated invalidate() calls with same dimensions");
    }
}
