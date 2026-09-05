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

package com.duom.ardamaps.gui.screens.map;

import com.duom.ardamaps.core.data.location.LocationClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link LocationNavigationHistory}.
 */
class LocationNavigationHistoryTest {

    /**
     * Verifies that back and forward navigation walk the stored history in order and keep the current cursor coherent.
     * This protects the basic browser-style navigation behavior used by the map side panel.
     */
    @Test
    void pushBackForward_roundTrip() {

        var history = new LocationNavigationHistory();
        var first = location();
        var second = location();
        var third = location();

        history.push(first);
        history.push(second);
        history.push(third);

        assertSame(third, history.current());
        assertSame(second, history.back());
        assertSame(first, history.back());
        assertSame(second, history.forward());
        assertSame(third, history.forward());
    }

    /**
     * Creates a minimal mocked location entry for history navigation tests.
     *
     * @return A mocked {@link LocationClient} instance.
     */
    private static LocationClient location() {

        return mock(LocationClient.class);
    }

    /**
     * Verifies that pushing a new entry after navigating backward truncates obsolete forward history.
     * This matters because the side panel should behave like linear navigation, not a branching tree.
     */
    @Test
    void push_midHistory_truncatesForwardEntries() {

        var history = new LocationNavigationHistory();
        var first = location();
        var second = location();
        var third = location();
        var replacement = location();

        history.push(first);
        history.push(second);
        history.push(third);
        assertSame(second, history.back());

        history.push(replacement);

        assertSame(replacement, history.current());
        assertNull(history.forward());
        assertSame(second, history.back());
    }

    /**
     * Verifies that history retains only the newest ten entries and evicts the oldest once the cap is exceeded.
     * This protects the intended bounded-memory behavior and the documented ten-entry limit.
     */
    @Test
    void push_overTenEntries_evictsOldest() {

        var history = new LocationNavigationHistory();
        LocationClient first = null;
        LocationClient second = null;
        LocationClient last = null;

        for (int idx = 0; idx < 11; idx++) {
            var location = location();
            if (idx == 0) first = location;
            if (idx == 1) second = location;
            if (idx == 10) last = location;
            history.push(location);
        }

        assertSame(last, history.current());

        LocationClient cursor = null;
        for (int idx = 0; idx < 9; idx++) {
            cursor = history.back();
        }

        assertSame(second, cursor);
        assertNotSame(first, cursor);
        assertNull(history.back());
    }

    /**
     * Verifies that navigation boundaries return null when no further movement is possible.
     * This protects the screen-level contract that distinguishes "close panel" from "move to another entry".
     */
    @Test
    void backAtStartAndForwardAtEnd_returnNull() {

        var history = new LocationNavigationHistory();
        var only = location();

        history.push(only);

        assertNull(history.back());
        assertNull(history.forward());
    }
}
