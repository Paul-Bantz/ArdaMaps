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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Maintains side-panel location navigation history.
 */
public class LocationNavigationHistory {

    /** Maximum retained history entries. */
    private static final int MAX_HISTORY_SIZE = 10;

    /** Ordered history of locations displayed in the side panel. */
    private final List<LocationClient> locationHistory = new ArrayList<>();

    /** Index pointing at the currently displayed entry (-1 = no history). */
    private int historyIndex = -1;

    /**
     * Pushes a new location, truncating forward entries and evicting the oldest entry over the cap.
     */
    public void push(LocationClient location) {

        if (historyIndex < locationHistory.size() - 1)
            locationHistory.subList(historyIndex + 1, locationHistory.size()).clear();

        if (locationHistory.size() >= MAX_HISTORY_SIZE)
            locationHistory.remove(0);

        locationHistory.add(location);
        historyIndex = locationHistory.size() - 1;
    }

    /**
     * Moves back one history entry.
     *
     * @return The new current location, or null when already at the first entry.
     */
    public @Nullable LocationClient back() {

        if (historyIndex > 0) {
            historyIndex--;
            return locationHistory.get(historyIndex);
        }

        return null;
    }

    /**
     * Moves forward one history entry.
     *
     * @return The new current location, or null when already at the last entry.
     */
    public @Nullable LocationClient forward() {

        if (historyIndex < locationHistory.size() - 1) {
            historyIndex++;
            return locationHistory.get(historyIndex);
        }

        return null;
    }

    /**
     * Clears all history.
     */
    public void clear() {

        locationHistory.clear();
        historyIndex = -1;
    }

    /**
     * @return The current history entry, or null when history is empty.
     */
    public @Nullable LocationClient current() {

        if (historyIndex < 0 || historyIndex >= locationHistory.size()) return null;
        return locationHistory.get(historyIndex);
    }
}
