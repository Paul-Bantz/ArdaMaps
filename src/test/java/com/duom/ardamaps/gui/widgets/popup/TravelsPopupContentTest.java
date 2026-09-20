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

package com.duom.ardamaps.gui.widgets.popup;

import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for travels popup geometry.
 */
class TravelsPopupContentTest {

    /**
     * Verifies that shared height computation matches constructed popup geometry.
     */
    @Test
    void computeHeight_matchesConstructedPopupHeight() {

        BackgroundRenderer.GuiLayout bookArea = new BackgroundRenderer.GuiLayout(20, 30, 400, 300);
        int lineHeight = 9;

        TravelsPopupContent content = new TravelsPopupContent(() -> bookArea, 120, 90, lineHeight);

        assertEquals(TravelsPopupContent.computeHeight(lineHeight, bookArea.guiHeight()), content.getHeight());
    }

    /**
     * Verifies that height computation clamps to the available book height.
     */
    @Test
    void computeHeight_smallBookHeight_clampsToBookHeight() {

        assertEquals(48, TravelsPopupContent.computeHeight(9, 48));
    }

    /**
     * Verifies that vertical padding keeps content clear of the popup shadow band.
     */
    @Test
    void verticalPadding_usesShadowedContentInset() {

        int lineHeight = 9;
        int rowHeight = TravelsPopupContent.rowHeight(lineHeight);

        assertEquals(ModConstants.POPUP_CONTENT_INSET - (rowHeight - lineHeight) / 2,
                TravelsPopupContent.verticalPadding(lineHeight));
    }
}
