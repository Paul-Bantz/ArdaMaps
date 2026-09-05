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

import com.duom.ardamaps.gui.screens.map.MarkerTypeFilter;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for marker popup geometry and hit testing.
 */
class MarkersPopupContentTest {

    /**
     * Verifies that the popup is centred and sized from available marker type content.
     */
    @Test
    void constructor_largeContentArea_centresContentSizedPopup() {

        MarkersPopupContent content = content(new TestMarkerTypeFilter());

        assertEquals(246, content.getWidth());
        assertEquals(130, content.getHeight());
        assertEquals(97, content.getX());
        assertEquals(115, content.getY());
    }

    /**
     * Verifies that popup hit testing swallows only clicks inside the popup rectangle.
     */
    @Test
    void mouseClicked_returnsTrueOnlyInsidePopup() {

        MarkersPopupContent content = content(new TestMarkerTypeFilter());

        assertTrue(content.mouseClicked(content.getX(), content.getY(), 0));
        assertTrue(content.mouseClicked(content.getX() + content.getWidth() - 1, content.getY() + content.getHeight() - 1, 0));
        assertFalse(content.mouseClicked(content.getX() + content.getWidth(), content.getY() + content.getHeight(), 0));
        assertFalse(content.mouseClicked(content.getX() - 1, content.getY(), 0));
    }

    /**
     * Verifies that clicking a marker type row toggles the filter and invokes the callback.
     */
    @Test
    void mouseClicked_entryRow_togglesMarkerTypeAndRunsCallback() {

        TestMarkerTypeFilter filter = new TestMarkerTypeFilter();
        int[] callbackCount = {0};
        MarkersPopupContent content = new MarkersPopupContent(
                () -> new BackgroundRenderer.GuiLayout(20, 30, 400, 300),
                filter,
                () -> callbackCount[0]++,
                index -> List.of(30, 24, 36).get(index),
                120,
                9,
                20);

        assertTrue(content.mouseClicked(158, 170, 0));

        assertFalse(filter.isEnabled("CITY"));
        assertEquals(1, callbackCount[0]);
    }

    /**
     * Verifies that the close button invokes the close callback and handles the click.
     */
    @Test
    void mouseClicked_closeButton_runsCloseCallbackAndReturnsTrue() {

        MarkersPopupContent content = content(new TestMarkerTypeFilter());
        AtomicInteger closeCount = new AtomicInteger();
        content.setOnClose(closeCount::incrementAndGet);

        assertTrue(content.mouseClicked(
                PopupCloseButton.x(content.getX(), content.getWidth()),
                PopupCloseButton.y(content.getY()),
                0));

        assertEquals(1, closeCount.get());
    }

    /**
     * Verifies that clicks next to the close button do not invoke the close callback.
     */
    @Test
    void mouseClicked_nextToCloseButton_doesNotRunCloseCallback() {

        MarkersPopupContent content = content(new TestMarkerTypeFilter());
        AtomicInteger closeCount = new AtomicInteger();
        content.setOnClose(closeCount::incrementAndGet);

        assertTrue(content.mouseClicked(
                PopupCloseButton.x(content.getX(), content.getWidth()) - 1,
                PopupCloseButton.y(content.getY()),
                0));

        assertEquals(0, closeCount.get());
    }

    /**
     * Verifies that widened title spacing keeps the popup centred inside the content area.
     */
    @Test
    void constructor_titleCloseSpacing_centresPopupInsideContentArea() {

        MarkersPopupContent content = new MarkersPopupContent(
                () -> new BackgroundRenderer.GuiLayout(20, 30, 400, 300),
                new TestMarkerTypeFilter(),
                () -> {
                },
                _ -> 10,
                160,
                9,
                20);

        assertEquals(20 + (400 - content.getWidth()) / 2, content.getX());
    }

    /**
     * Verifies that a fresh popup marks the all button toggled when every marker type is enabled.
     */
    @Test
    void constructor_allMarkerTypesEnabled_togglesAllButtonOnly() {

        MarkersPopupContent content = content(new TestMarkerTypeFilter());

        assertTrue(content.isAllToggled());
        assertFalse(content.isNoneToggled());
    }

    /**
     * Verifies that clicking the none button moves the toggled state from all to none.
     */
    @Test
    void mouseClicked_noneButton_togglesNoneButtonOnly() {

        MarkersPopupContent content = content(new TestMarkerTypeFilter());

        assertTrue(content.mouseClicked(230, 230, 0));

        assertFalse(content.isAllToggled());
        assertTrue(content.isNoneToggled());
    }

    /**
     * Verifies that a partially enabled marker filter leaves both action buttons untoggled.
     */
    @Test
    void mouseClicked_entryRow_partiallyEnabledUntogglesActionButtons() {

        MarkersPopupContent content = content(new TestMarkerTypeFilter());

        assertTrue(content.mouseClicked(158, 170, 0));

        assertFalse(content.isAllToggled());
        assertFalse(content.isNoneToggled());
    }

    /**
     * Creates marker popup content using stable test text metrics.
     *
     * @param filter The marker filter fixture.
     * @return The popup content fixture.
     */
    private static MarkersPopupContent content(MarkerTypeFilter filter) {

        return new MarkersPopupContent(
                () -> new BackgroundRenderer.GuiLayout(20, 30, 400, 300),
                filter,
                () -> {
                },
                index -> List.of(30, 24, 36).get(index),
                120,
                9,
                20);
    }

    /**
     * Test marker filter with stable entries.
     */
    private static class TestMarkerTypeFilter extends MarkerTypeFilter {

        /** Available marker type entries. */
        private final List<Entry> entries = List.of(
                new Entry("CITY", "City", Identifier.parse("ardamaps:city")),
                new Entry("RUIN", "Ruin", Identifier.parse("ardamaps:ruin")),
                new Entry("TOWN", "Town", Identifier.parse("ardamaps:town")));

        /** Enabled marker type keys. */
        private final Set<String> enabled = new HashSet<>(List.of("CITY", "RUIN", "TOWN"));

        /**
         * Gets available marker type entries.
         *
         * @return Available marker type entries.
         */
        @Override
        public List<Entry> available() {

            return entries;
        }

        /**
         * Checks whether a key is enabled.
         *
         * @param key The marker type key.
         * @return True when enabled.
         */
        @Override
        public boolean isEnabled(String key) {

            return enabled.contains(key);
        }

        /**
         * Toggles a marker type key.
         *
         * @param key The marker type key to toggle.
         */
        @Override
        public void toggle(String key) {

            if (!enabled.remove(key)) enabled.add(key);
        }

        /**
         * Enables every test marker type.
         */
        @Override
        public void enableAll() {

            entries.stream().map(Entry::key).forEach(enabled::add);
        }

        /**
         * Disables every test marker type.
         */
        @Override
        public void disableAll() {

            enabled.clear();
        }

        /**
         * Checks whether every test marker type is enabled.
         *
         * @return True when every test marker type is enabled.
         */
        @Override
        public boolean isAllEnabled() {

            return enabled.size() == entries.size();
        }

        /**
         * Checks whether no test marker type is enabled.
         *
         * @return True when no test marker type is enabled.
         */
        @Override
        public boolean isNoneEnabled() {

            return enabled.isEmpty();
        }
    }
}
