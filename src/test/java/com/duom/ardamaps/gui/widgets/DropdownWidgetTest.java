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

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests for {@link DropdownWidget} dropdown layout and click selection behaviour.
 */
class DropdownWidgetTest {

    /**
     * Verifies that upward-expanding dropdowns render all visible options above the button and never on the button row.
     * This protects the upward layout math from overlapping the trigger control.
     */
    @Test
    void getDropdownItemY_upLeftWithScrolling_doesNotOverlapButton() {

        DropdownWidget<String, TextIdentifierPairItem> widget = widget(DropdownWidget.ExpandDirection.UP_LEFT);
        var items = widget.computeDropdownItems(widget.computeItemList());
        int visibleCount = widget.getVisibleDropdownItemCount(items);

        assertEquals(3, visibleCount);
        assertEquals(40, widget.getDropdownItemY(0, visibleCount));
        assertEquals(60, widget.getDropdownItemY(1, visibleCount));
        assertEquals(80, widget.getDropdownItemY(2, visibleCount));
        assertNotEquals(widget.getY(), widget.getDropdownItemY(2, visibleCount));
    }

    /**
     * Creates a dropdown fixture with deterministic options and layout.
     *
     * @param direction The expansion direction to test.
     * @return A configured dropdown widget fixture.
     */
    private static DropdownWidget<String, TextIdentifierPairItem> widget(DropdownWidget.ExpandDirection direction) {

        return new DropdownWidget<>(
                0,
                100,
                80,
                20,
                Component.empty(),
                Component.empty(),
                null,
                List.of("a", "b", "c", "d", "e"),
                value -> new TextIdentifierPairItem(value, null),
                "b",
                ignored -> {
                },
                false,
                false,
                false,
                true,
                false,
                false,
                direction,
                3);
    }

    /**
     * Verifies that downward-expanding dropdowns start immediately below the button.
     * This protects the basic placement contract used by the default downward menu direction.
     */
    @Test
    void getDropdownItemY_downRight_startsBelowButton() {

        DropdownWidget<String, TextIdentifierPairItem> widget = widget(DropdownWidget.ExpandDirection.DOWN_RIGHT);
        var items = widget.computeDropdownItems(widget.computeItemList());
        int visibleCount = widget.getVisibleDropdownItemCount(items);

        assertEquals(120, widget.getDropdownItemY(0, visibleCount));
    }

    /**
     * Verifies that click selection uses the same selected-excluded item list that rendering uses.
     * This prevents a mismatch where the user clicks one rendered row but a different option is selected.
     */
    @Test
    void onClick_expandedList_selectsRenderedItem() {

        DropdownWidget<String, TextIdentifierPairItem> widget = widget(DropdownWidget.ExpandDirection.UP_LEFT);

        widget.onClick(mouse(10, 100), false);
        widget.onClick(mouse(10, 65), false);

        assertEquals("c", widget.getSelected());
    }

    /**
     * Verifies that the widget's reported height stays at the button height while expanded.
     * This protects parent row layout from shifting the trigger when the option list opens.
     */
    @Test
    void onClick_expandsWithoutChangingReportedHeight() {

        DropdownWidget<String, TextIdentifierPairItem> widget = widget(DropdownWidget.ExpandDirection.DOWN_RIGHT);

        widget.onClick(mouse(10, 100), false);

        assertEquals(20, widget.getHeight());
    }

    /**
     * Creates a mouse button event at the supplied screen coordinates.
     *
     * @param x The screen X coordinate.
     * @param y The screen Y coordinate.
     * @return A mouse button event fixture.
     */
    @SuppressWarnings("SameParameterValue")
    private static MouseButtonEvent mouse(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
    }
}
