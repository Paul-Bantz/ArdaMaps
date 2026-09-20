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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for travels sheet hit testing.
 */
class TravelsSheetButtonTest {

    /**
     * Verifies a point inside the rotated sheet and before the clip line is accepted.
     */
    @Test
    void hitTest_insideVisibleRotatedSheet_returnsTrue() {

        assertTrue(TravelsSheetButton.hitTest(132, 160, 122, 150, 60, 200, 150));
    }

    /**
     * Verifies the clip line hides the overshooting part of the rotated sheet.
     */
    @Test
    void hitTest_afterClipLine_returnsFalse() {

        assertFalse(TravelsSheetButton.hitTest(150, 160, 122, 150, 60, 200, 150));
    }

    /**
     * Verifies a point outside the rotated local rectangle is rejected.
     */
    @Test
    void hitTest_outsideRotatedSheet_returnsFalse() {

        assertFalse(TravelsSheetButton.hitTest(110, 160, 122, 150, 60, 200, 150));
    }
}
