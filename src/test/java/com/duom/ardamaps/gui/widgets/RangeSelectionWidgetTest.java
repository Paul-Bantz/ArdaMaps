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

import com.duom.ardamaps.core.data.config.MapLayerRange;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RangeSelectionWidget} selection, scrolling, and hit-testing behaviour.
 */
class RangeSelectionWidgetTest {

    /**
     * Verifies that hit testing maps in-bounds coordinates to rendered item indices and rejects outside points.
     * This protects the basic pointer-to-range mapping used by click selection.
     */
    @Test
    void indexAt_insideAndOutsideBounds_returnsExpectedIndex() {

        RangeSelectionWidget widget = widget(ranges(5), new ArrayList<>());

        assertEquals(0, widget.indexAt(0, 0));
        assertEquals(2, widget.indexAt(59, 10));
        assertEquals(-1, widget.indexAt(-1, 10));
        assertEquals(-1, widget.indexAt(100, 10));
        assertEquals(-1, widget.indexAt(10, 20));
    }

    /**
     * Verifies that tooltip labels show the covered Y interval and normalize reversed authored bounds.
     * This protects the user-facing hover text from exposing confusing min/max ordering details.
     */
    @Test
    void tooltipLabel_formatsNormalizedYRange() {

        assertEquals("-500..-128", RangeSelectionWidget.tooltipLabel(new MapLayerRange(0, "0.pmtiles", -500, -128)));
        assertEquals("-500..-128", RangeSelectionWidget.tooltipLabel(new MapLayerRange(0, "0.pmtiles", -128, -500)));
    }

    /**
     * Verifies that the label-area gradient starts fully transparent and ends at the strip background colour.
     * This protects the softened left edge introduced for the fixed label area.
     */
    @Test
    void gradientColorAt_spansTransparentToBackground() {

        assertEquals(0x00000000, RangeSelectionWidget.gradientColorAt(0, 40));
        assertEquals(0x70000000, RangeSelectionWidget.gradientColorAt(39, 40));
    }

    /**
     * Verifies that tooltip positioning centers above its anchor and clamps cleanly at both screen edges.
     * This protects hover tooltips for partially clipped cells near the viewport boundaries.
     */
    @Test
    void tooltipHelpers_centerClampAndSitAboveAnchor() {

        assertEquals(40, RangeSelectionWidget.tooltipLeft(200, 60, 40));
        assertEquals(4, RangeSelectionWidget.tooltipLeft(200, 5, 40));
        assertEquals(156, RangeSelectionWidget.tooltipLeft(200, 195, 40));
        assertEquals(56, RangeSelectionWidget.tooltipTop(80, 24));
        assertEquals(4, RangeSelectionWidget.tooltipTop(10, 24));
    }

    /**
     * Creates a test widget with control-scroll disabled.
     *
     * @param ranges   the selectable map layer ranges
     * @param selected callback sink for selected ranges
     * @return a test widget using the supplied ranges and callback sink
     */
    private static TestRangeSelectionWidget widget(List<MapLayerRange> ranges,
                                                   List<MapLayerRange> selected) {

        return widget(ranges, selected, false);
    }

    /**
     * Builds consecutive map layer ranges with stable indices and file names.
     *
     * @param count the number of ranges to create
     * @return generated map layer ranges for test setup
     */
    private static List<MapLayerRange> ranges(int count) {

        List<MapLayerRange> ranges = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            ranges.add(new MapLayerRange(index, index + ".pmtiles", index * 10, index * 10 + 9));
        }

        return ranges;
    }

    /**
     * Creates a test widget with an injectable control-key state.
     *
     * @param ranges      the selectable map layer ranges
     * @param selected    callback sink for selected ranges
     * @param controlDown whether the widget should report the control key as pressed
     * @return a test widget using the supplied ranges, callback sink, and control-key state
     */
    private static TestRangeSelectionWidget widget(List<MapLayerRange> ranges,
                                                   List<MapLayerRange> selected,
                                                   boolean controlDown) {

        return new TestRangeSelectionWidget(
                0,
                0,
                100,
                20,
                Text.empty(),
                ranges,
                20,
                selected::add,
                controlDown);
    }

    /**
     * Verifies that hit testing accounts for the current horizontal scroll offset.
     * This prevents selection from drifting away from the items actually visible after scrolling.
     */
    @Test
    void indexAt_afterScroll_returnsScrolledIndex() {

        RangeSelectionWidget widget = widget(ranges(10), new ArrayList<>());

        widget.centerOn(5);

        assertEquals(5, widget.indexAt(50, 10));
    }

    /**
     * Verifies that scroll clamping pins offsets to both ends of over-wide content.
     * This protects the strip from overscrolling into blank space on either side.
     */
    @Test
    void clampScrollOffset_wideContent_clampsBothEnds() {

        RangeSelectionWidget widget = widget(ranges(10), new ArrayList<>());

        assertEquals(0, widget.clampScrollOffset(12));
        assertEquals(-128, widget.clampScrollOffset(-200));
        assertEquals(-40, widget.clampScrollOffset(-40));
    }

    /**
     * Verifies that scroll clamping pins offset to zero when content is narrower than the widget.
     * This protects narrow strips from acquiring meaningless negative scroll positions.
     */
    @Test
    void clampScrollOffset_narrowContent_pinsToZero() {

        RangeSelectionWidget widget = widget(ranges(3), new ArrayList<>());

        assertEquals(0, widget.minScrollOffset());
        assertEquals(0, widget.clampScrollOffset(-20));
        assertEquals(0, widget.clampScrollOffset(20));
    }

    /**
     * Verifies that narrow content is right-aligned inside the widget and leaves the left gutter inactive.
     * This protects the special-case hit-testing path for strips that do not overflow their viewport.
     */
    @Test
    void indexAt_narrowContent_hitsOnlyRightAlignedBand() {

        RangeSelectionWidget widget = widget(ranges(3), new ArrayList<>());

        assertEquals(-1, widget.indexAt(39, 10));
        assertEquals(0, widget.indexAt(40, 10));
        assertEquals(2, widget.indexAt(99, 10));
    }

    /**
     * Verifies that centering a middle item places its cell center at the widget center.
     * This protects the positioning math used after programmatic range changes.
     */
    @Test
    void centerOn_middleItem_centersItem() {

        RangeSelectionWidget widget = widget(ranges(10), new ArrayList<>());

        widget.centerOn(5);

        assertEquals(-74, widget.getScrollOffset());
    }

    /**
     * Verifies that centering near either end clamps to the available scroll range instead of overscrolling.
     * This protects the edge behavior when the selected range is near the start or end of the strip.
     */
    @Test
    void centerOn_edgeItems_clampsToEnds() {

        RangeSelectionWidget widget = widget(ranges(10), new ArrayList<>());

        widget.centerOn(0);
        assertEquals(0, widget.getScrollOffset());

        widget.centerOn(9);
        assertEquals(-128, widget.getScrollOffset());
    }

    /**
     * Verifies that a press and release without drag selects the clicked item and fires the callback once.
     * This protects the basic click-selection contract and callback semantics.
     */
    @Test
    void onRelease_withoutMovement_selectsAndFiresCallbackOnce() {

        List<MapLayerRange> selected = new ArrayList<>();
        RangeSelectionWidget widget = widget(ranges(5), selected);

        widget.onClick(45, 10);
        widget.onRelease(45, 10);

        assertEquals(2, widget.getSelectedIndex());

        assertNotNull(widget.getSelected());

        assertEquals(2, widget.getSelected().index());
        assertEquals(List.of(widget.getSelected()), selected);
    }

    /**
     * Verifies that a drag past the threshold scrolls the strip and does not fire the selection callback.
     * This prevents drag-to-pan gestures from being misinterpreted as a range selection.
     */
    @Test
    void onRelease_afterDrag_scrollsWithoutCallback() {

        List<MapLayerRange> selected = new ArrayList<>();
        RangeSelectionWidget widget = widget(ranges(10), selected);

        widget.onClick(70, 10);
        widget.onDrag(20, 10, -50, 0);
        widget.onRelease(20, 10);

        assertEquals(-50, widget.getScrollOffset());
        assertNull(widget.getSelected());
        assertTrue(selected.isEmpty());
    }

    /**
     * Verifies that programmatic selection updates the selected item and scroll position without invoking the callback.
     * This protects internal UI synchronization from accidentally behaving like user input.
     */
    @Test
    void setSelected_existingRange_selectsWithoutCallback() {

        List<MapLayerRange> selected = new ArrayList<>();
        List<MapLayerRange> ranges = ranges(10);
        RangeSelectionWidget widget = widget(ranges, selected);

        widget.setSelected(ranges.get(5));

        assertEquals(ranges.get(5), widget.getSelected());
        assertEquals(-74, widget.getScrollOffset());
        assertTrue(selected.isEmpty());
    }

    /**
     * Verifies that shrinking the widget after selecting the last range keeps the strip pinned to the right edge.
     * This protects resize handling so the selected tail item remains reachable and visible.
     */
    @Test
    void setWidth_afterSelectingLastRange_reclampsToMinimumOffset() {

        List<MapLayerRange> ranges = ranges(10);
        RangeSelectionWidget widget = widget(ranges, new ArrayList<>());

        widget.setWidth(200);
        widget.setSelected(ranges.get(9));
        widget.setWidth(100);

        assertEquals(widget.minScrollOffset(), widget.getScrollOffset());
    }

    /**
     * Verifies that reserved ellipsis gutters are not clickable range cells.
     * This protects the overflow affordance from being treated like a real selectable range.
     */
    @Test
    void indexAt_insideActiveEllipsisCap_returnsNoIndex() {

        RangeSelectionWidget widget = widget(ranges(10), new ArrayList<>());

        widget.centerOn(5);

        assertEquals(-1, widget.indexAt(5, 10));
        assertEquals(-1, widget.indexAt(95, 10));
    }

    /**
     * Verifies that control-scroll selects the next range, fires the callback once, and recenters the strip.
     * This protects the keyboard-modified scroll shortcut used for fast range stepping.
     */
    @Test
    void mouseScrolled_withControlUp_selectsNextRangeAndFiresCallbackOnce() {

        List<MapLayerRange> selected = new ArrayList<>();
        List<MapLayerRange> ranges = ranges(10);
        RangeSelectionWidget widget = widget(ranges, selected, true);

        widget.setSelected(ranges.get(4));

        assertTrue(widget.mouseScrolled(50, 10, 1));
        assertEquals(5, widget.getSelectedIndex());
        assertEquals(ranges.get(5), widget.getSelected());
        assertEquals(List.of(ranges.get(5)), selected);
        assertEquals(-74, widget.getScrollOffset());
    }

    /**
     * Verifies that control-scroll below the first range is clamped and does not refire the callback.
     * This protects the lower bound from duplicate callback noise when no selection change occurs.
     */
    @Test
    void mouseScrolled_withControlDownAtFirstRange_clampsWithoutCallback() {

        List<MapLayerRange> selected = new ArrayList<>();
        List<MapLayerRange> ranges = ranges(10);
        RangeSelectionWidget widget = widget(ranges, selected, true);

        widget.setSelected(ranges.get(0));

        assertTrue(widget.mouseScrolled(50, 10, -1));
        assertEquals(0, widget.getSelectedIndex());
        assertTrue(selected.isEmpty());
    }

    /**
     * Verifies that control-scroll with no current selection uses the first visible range as its base.
     * This documents the deterministic starting point for shortcut-based selection when nothing is selected yet.
     */
    @Test
    void mouseScrolled_withControlAndNoSelection_startsFromFirstVisibleIndex() {

        List<MapLayerRange> selected = new ArrayList<>();
        List<MapLayerRange> ranges = ranges(10);
        RangeSelectionWidget widget = widget(ranges, selected, true);

        widget.centerOn(5);

        assertEquals(3, widget.firstVisibleIndex());
        assertTrue(widget.mouseScrolled(50, 10, 1));
        assertEquals(4, widget.getSelectedIndex());
        assertEquals(List.of(ranges.get(4)), selected);
    }

    /**
     * Verifies that plain scroll pans by one item width and does not fire selection callbacks.
     * This protects ordinary wheel scrolling from unexpectedly changing the active range.
     */
    @Test
    void mouseScrolled_withoutControl_pansByItemWidthWithoutCallback() {

        List<MapLayerRange> selected = new ArrayList<>();
        RangeSelectionWidget widget = widget(ranges(10), selected);

        widget.centerOn(5);

        assertTrue(widget.mouseScrolled(50, 10, 1));
        assertEquals(-54, widget.getScrollOffset());
        assertNull(widget.getSelected());
        assertTrue(selected.isEmpty());
    }

    /**
     * Verifies that every viewport pixel maps to a range while the strip is scrolled between both ends.
     * This protects mid-strip hit-testing from leaving dead pixels inside the active viewport.
     */
    @Test
    void indexAt_scrolledMidStrip_hitsFirstAndLastViewportPixels() {

        RangeSelectionWidget widget = widget(ranges(10), new ArrayList<>());

        widget.centerOn(5);

        assertEquals(3, widget.indexAt(widget.stripX(), 10));
        assertEquals(7, widget.indexAt(widget.stripX() + widget.viewportWidth() - 1, 10));
    }

    /**
     * Verifies that the right-most viewport pixel remains selectable at the scroll end and during mid-strip scrolling.
     * This protects an easy off-by-one boundary where the last visible item can become unclickable.
     */
    @Test
    void onRelease_rightMostViewportPixel_selectsAtScrollEndAndMidStrip() {

        List<MapLayerRange> selected = new ArrayList<>();
        RangeSelectionWidget widget = widget(ranges(10), selected);

        widget.centerOn(9);
        widget.onClick(widget.stripX() + widget.viewportWidth() - 1, 10);
        widget.onRelease(widget.stripX() + widget.viewportWidth() - 1, 10);

        assertEquals(9, widget.getSelectedIndex());
        assertEquals(9, selected.get(0).index());

        widget.centerOn(4);
        widget.onClick(widget.stripX() + widget.viewportWidth() - 1, 10);
        widget.onRelease(widget.stripX() + widget.viewportWidth() - 1, 10);

        assertNotEquals(-1, widget.getSelectedIndex());
        assertEquals(2, selected.size());
    }

    /**
     * Verifies that overflowing strips reserve ellipsis gutters while fitting strips keep all horizontal space clickable.
     * This protects the mode switch between overflowed and fully fitting layouts.
     */
    @Test
    void indexAt_overflowingAndNonOverflowingContent_reservesGuttersOnlyWhenNeeded() {

        RangeSelectionWidget overflowingWidget = widget(ranges(10), new ArrayList<>());
        RangeSelectionWidget fittingWidget = widget(ranges(5), new ArrayList<>());

        assertEquals(-1, overflowingWidget.indexAt(13, 10));
        assertEquals(0, overflowingWidget.indexAt(14, 10));
        assertEquals(0, fittingWidget.indexAt(0, 10));
    }

    /**
     * Verifies that plain scroll clamps at both ends of the scrollable range.
     * This protects scroll handling from pushing the strip beyond either legal limit.
     */
    @Test
    void mouseScrolled_withoutControl_clampsAtScrollBounds() {

        RangeSelectionWidget widget = widget(ranges(10), new ArrayList<>());

        widget.centerOn(9);
        assertEquals(widget.minScrollOffset(), widget.getScrollOffset());
        assertTrue(widget.mouseScrolled(50, 10, -1));
        assertEquals(widget.minScrollOffset(), widget.getScrollOffset());

        widget.centerOn(0);
        assertEquals(0, widget.getScrollOffset());
        assertTrue(widget.mouseScrolled(50, 10, 1));
        assertEquals(0, widget.getScrollOffset());
    }

    /**
     * Verifies that scroll events outside the widget are ignored.
     * This protects surrounding UI from having its wheel input consumed by an unfocused range strip.
     */
    @Test
    void mouseScrolled_outsideWidgetBounds_returnsFalse() {

        RangeSelectionWidget widget = widget(ranges(10), new ArrayList<>());

        assertFalse(widget.mouseScrolled(100, 10, 1));
        assertFalse(widget.mouseScrolled(50, 20, 1));
    }

    /**
     * Test subclass that exposes a fixed control-key state to scroll handling.
     */
    private static class TestRangeSelectionWidget extends RangeSelectionWidget {

        /**
         * Whether {@link #isControlDown()} should report the control key as pressed.
         */
        private final boolean controlDown;

        /**
         * Creates a test widget with deterministic keyboard state.
         *
         * @param x           the widget x-coordinate
         * @param y           the widget y-coordinate
         * @param width       the widget width
         * @param height      the widget height
         * @param label       the widget narration label
         * @param ranges      the selectable map layer ranges
         * @param itemWidth   the rendered width of each range cell
         * @param onSelect    callback invoked when a range is selected
         * @param controlDown whether the control key should be reported as pressed
         */
        TestRangeSelectionWidget(int x, int y, int width, int height, Text label, List<MapLayerRange> ranges,
                                 int itemWidth, java.util.function.Consumer<MapLayerRange> onSelect,
                                 boolean controlDown) {

            super(x, y, width, height, label, ranges, itemWidth, onSelect);
            this.controlDown = controlDown;
        }

        /**
         * Returns the fixed control-key state supplied by the test.
         *
         * @return whether control-scroll behaviour should be active
         */
        @Override
        boolean isControlDown() {

            return controlDown;
        }
    }
}
