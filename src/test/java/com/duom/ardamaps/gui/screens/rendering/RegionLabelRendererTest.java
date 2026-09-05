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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for pure region label geometry helpers.
 */
class RegionLabelRendererTest {

    /**
     * Root labels render even when they do not fit inside the label radius.
     */
    @Test
    void labelFits_rootThatDoesNotFitReturnsTrue() {

        assertTrue(RegionLabelRenderer.labelFits(0, 10.0, 100));
    }

    /**
     * Child labels render when the label radius can contain the text.
     */
    @Test
    void labelFits_childThatFitsReturnsTrue() {

        assertTrue(RegionLabelRenderer.labelFits(1, 50.0, 100));
    }

    /**
     * Child labels do not render when the label radius cannot contain the text.
     */
    @Test
    void labelFits_childThatDoesNotFitReturnsFalse() {

        assertFalse(RegionLabelRenderer.labelFits(1, 49.0, 100));
    }
}
