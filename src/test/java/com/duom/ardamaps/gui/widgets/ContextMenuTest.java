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

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for context menu placement and click hit testing.
 */
class ContextMenuTest {

    /** Width returned by the test text measurement function. */
    private static final int TEXT_WIDTH = 20;

    /** Height of one context menu row in pixels. */
    private static final int ROW_HEIGHT = 20;

    /** Font line height returned by the test line-height supplier. */
    private static final int LINE_HEIGHT = 9;

    /**
     * Verifies that menus opened near the bottom edge open upward and anchor above the click.
     */
    @Test
    void constructor_nearBottom_revealsUp() {

        ContextMenu menu = menu(40, 95, entries(3));

        assertTrue(menu.isOpenUpward());
        assertEquals(35, menu.getY());
    }

    /**
     * Verifies that an explicit opening direction overrides automatic placement.
     */
    @Test
    void constructor_explicitDirection_revealsUp() {

        ContextMenu menu = new ContextMenu(40, 80, 100, 200, 0, 0, entries(1),
                ignored -> TEXT_WIDTH, () -> LINE_HEIGHT, true);

        assertTrue(menu.isOpenUpward());
        assertEquals(60, menu.getY());
    }

    /**
     * Verifies that short labels use the configured minimum width.
     */
    @Test
    void constructor_shortLabel_usesMinimumWidth() {

        ContextMenu menu = new ContextMenu(10, 10, 200, 100, 0, 0,
                List.of(new ContextMenu.Entry(Component.literal("A"), () -> {
                })),
                _ -> 5,
                () -> LINE_HEIGHT,
                80);

        assertEquals(80, menu.getWidth());
    }

    /**
     * Verifies that short labels can use the shared map/book-label minimum width.
     */
    @Test
    void constructor_shortLabel_usesDefaultMinimumWidth() {

        ContextMenu menu = new ContextMenu(10, 10, 200, 100, 0, 0,
                List.of(new ContextMenu.Entry(Component.literal("A"), () -> {
                })),
                _ -> 5,
                () -> LINE_HEIGHT,
                ContextMenu.DEFAULT_MIN_WIDTH);

        assertEquals(ContextMenu.DEFAULT_MIN_WIDTH, menu.getWidth());
    }

    /**
     * Verifies that long labels can exceed the configured minimum width.
     */
    @Test
    void constructor_longLabel_exceedsMinimumWidth() {

        ContextMenu menu = new ContextMenu(10, 10, 200, 100, 0, 0,
                List.of(new ContextMenu.Entry(Component.literal("Very long label"), () -> {
                })),
                _ -> 120,
                () -> LINE_HEIGHT,
                80);

        assertEquals(120 + ConnectedButtonSurface.LABEL_PADDING * 2, menu.getWidth());
    }

    /**
     * Verifies that an explicit downward direction anchors the top edge at the supplied y coordinate.
     */
    @Test
    void constructor_explicitDirection_revealsDown() {

        int selectedIndex = 2;
        int labelY = 40;
        int labelHeight = 24;
        int anchorY = labelY + (labelHeight - ContextMenu.ITEM_HEIGHT) / 2 - selectedIndex * ContextMenu.ITEM_HEIGHT;
        ContextMenu menu = new ContextMenu(40, anchorY, 100, 200, 0, 0, entries(4),
                ignored -> TEXT_WIDTH, () -> LINE_HEIGHT, false);

        assertFalse(menu.isOpenUpward());
        assertEquals(anchorY, menu.getY());
    }

    /**
     * Verifies that construction clamps menus at the left and top screen edges.
     */
    @Test
    void constructor_clampsLeftAndTopEdges() {

        ContextMenu menu = menu(-10, -10, entries(1));

        assertEquals(0, menu.getX());
        assertEquals(0, menu.getY());
    }

    /**
     * Verifies that construction clamps menus at the right and bottom screen edges.
     */
    @Test
    void constructor_clampsRightAndBottomEdges() {

        ContextMenu menu = menu(200, 1_000, entries(1));

        assertEquals(100 - menu.getWidth(), menu.getX());
        assertEquals(100 - menu.getHeight(), menu.getY());
    }

    /**
     * Verifies that setPosition clamps all screen edges using the original reveal direction.
     */
    @Test
    void setPosition_clampsEdges() {

        ContextMenu menu = menu(40, 95, entries(1));

        menu.setPosition(-20, -20);
        assertEquals(0, menu.getX());
        assertEquals(0, menu.getY());

        menu.setPosition(200, 1_000);
        assertEquals(100 - menu.getWidth(), menu.getX());
        assertEquals(100 - menu.getHeight(), menu.getY());
    }

    /**
     * Verifies that cyclic scroll wraps past both ends of the entry list.
     */
    @Test
    void scroll_wrapsHighlightCyclically() {

        ContextMenu menu = menu(10, 10, entries(3));

        assertTrue(menu.scroll(-1));
        assertEquals(0, menu.highlightIndex());

        assertTrue(menu.scroll(1));
        assertEquals(2, menu.highlightIndex());

        assertTrue(menu.scroll(-1));
        assertEquals(0, menu.highlightIndex());
    }

    /**
     * Verifies that direct highlight movement wraps down from no selection.
     */
    @Test
    void moveHighlight_downFromNoSelection_selectsFirstEntry() {

        ContextMenu menu = menu(10, 10, entries(3));

        assertTrue(menu.moveHighlight(1));
        assertEquals(0, menu.highlightIndex());
    }

    /**
     * Verifies that direct highlight movement wraps up from no selection.
     */
    @Test
    void moveHighlight_upFromNoSelection_selectsLastEntry() {

        ContextMenu menu = menu(10, 10, entries(3));

        assertTrue(menu.moveHighlight(-1));
        assertEquals(2, menu.highlightIndex());
    }

    /**
     * Verifies that zero scroll does not create a highlight.
     */
    @Test
    void scroll_zeroAmount_isIgnored() {

        ContextMenu menu = menu(10, 10, entries(3));

        assertFalse(menu.scroll(0));
        assertEquals(-1, menu.highlightIndex());
    }

    /**
     * Verifies that highlighted activation runs the highlighted row's action.
     */
    @Test
    void activateHighlighted_runsHighlightedEntry() {

        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        ContextMenu menu = menu(10, 10, List.of(
                new ContextMenu.Entry(Component.literal("first"), first::incrementAndGet),
                new ContextMenu.Entry(Component.literal("second"), second::incrementAndGet)
        ));

        menu.setHighlightIndex(1);

        assertTrue(menu.activateHighlighted());
        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }

    /**
     * Verifies that highlighted activation can run the first row's action.
     */
    @Test
    void activateHighlighted_fromFirstEntry_runsFirstEntry() {

        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        ContextMenu menu = menu(10, 10, List.of(
                new ContextMenu.Entry(Component.literal("first"), first::incrementAndGet),
                new ContextMenu.Entry(Component.literal("second"), second::incrementAndGet)
        ));

        menu.setHighlightIndex(0);

        assertTrue(menu.activateHighlighted());
        assertEquals(1, first.get());
        assertEquals(0, second.get());
    }

    /**
     * Verifies that activation without a highlighted row does nothing.
     */
    @Test
    void activateHighlighted_withoutHighlight_doesNothing() {

        AtomicInteger clicks = new AtomicInteger();
        ContextMenu menu = menu(10, 10, List.of(new ContextMenu.Entry(Component.literal("entry"), clicks::incrementAndGet)));

        assertFalse(menu.activateHighlighted());
        assertEquals(0, clicks.get());
    }

    /**
     * Verifies that menus default to no highlighted row until explicitly set.
     */
    @Test
    void constructor_withoutHighlightIndex_defaultsToNoHighlight() {

        ContextMenu menu = menu(10, 10, entries(1));

        assertEquals(-1, menu.highlightIndex());
    }

    /**
     * Verifies that menu size follows label width, row count, and button text margin.
     */
    @Test
    void constructor_sizesFromRowsAndTextMargin() {

        ContextMenu menu = menu(10, 10, entries(3));

        assertEquals(TEXT_WIDTH + ConnectedButtonSurface.LABEL_PADDING * 2, menu.getWidth());
        assertEquals(3 * ROW_HEIGHT, menu.getHeight());
    }

    /**
     * Verifies that adjacent row bands use half-open hit tests with no one-pixel overlap.
     */
    @Test
    void mouseClicked_rowBoundary_firesOnlySecondEntry() {

        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        ContextMenu menu = menu(10, 10, List.of(
                new ContextMenu.Entry(Component.literal("first"), first::incrementAndGet),
                new ContextMenu.Entry(Component.literal("second"), second::incrementAndGet)
        ));

        boolean handled = menu.mouseClicked(
                menu.getX() + ConnectedButtonSurface.LABEL_PADDING,
                menu.getY() + ROW_HEIGHT,
                GLFW.GLFW_MOUSE_BUTTON_LEFT);

        assertEquals(1, second.get());
        assertEquals(0, first.get());
        assertTrue(handled);
    }

    /**
     * Verifies that every row owns exactly one item-height band.
     */
    @Test
    void mouseClicked_rowBandsHaveUniformHeight() {

        var clicks = IntStream.range(0, 3)
                .mapToObj(ignored -> new AtomicInteger())
                .toList();
        ContextMenu menu = menu(10, 10, IntStream.range(0, 3)
                .mapToObj(index -> new ContextMenu.Entry(Component.literal("entry" + index),
                        clicks.get(index)::incrementAndGet))
                .toList());

        for (int i = 0; i < clicks.size(); i++) {
            int rowTop = menu.getY() + i * ROW_HEIGHT;
            int previousClicks = clicks.get(i).get();

            assertTrue(click(menu, rowTop));
            assertEquals(previousClicks + 1, clicks.get(i).get());

            assertTrue(click(menu, rowTop + ROW_HEIGHT - 1));
            assertEquals(previousClicks + 2, clicks.get(i).get());

            click(menu, rowTop + ROW_HEIGHT);
            assertEquals(previousClicks + 2, clicks.get(i).get());
        }
    }

    /**
     * Verifies that the pixel above the first row does not belong to any entry.
     */
    @Test
    void mouseClicked_aboveFirstRow_firesNoEntry() {

        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        ContextMenu menu = menu(10, 10, List.of(
                new ContextMenu.Entry(Component.literal("first"), first::incrementAndGet),
                new ContextMenu.Entry(Component.literal("second"), second::incrementAndGet)
        ));

        boolean handled = menu.mouseClicked(
                menu.getX() + ConnectedButtonSurface.LABEL_PADDING,
                menu.getY() - 1,
                GLFW.GLFW_MOUSE_BUTTON_LEFT);

        assertEquals(0, first.get());
        assertEquals(0, second.get());
        assertFalse(handled);
    }

    /**
     * Verifies that the menu bottom edge is half-open.
     */
    @Test
    void mouseClicked_atBottomEdge_firesNoEntry() {

        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        ContextMenu menu = menu(10, 10, List.of(
                new ContextMenu.Entry(Component.literal("first"), first::incrementAndGet),
                new ContextMenu.Entry(Component.literal("second"), second::incrementAndGet)
        ));

        boolean handled = menu.mouseClicked(
                menu.getX() + ConnectedButtonSurface.LABEL_PADDING,
                menu.getY() + menu.getHeight(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT);

        assertEquals(0, first.get());
        assertEquals(0, second.get());
        assertFalse(handled);
    }

    /**
     * Verifies that row hit boxes span the full popup width.
     */
    @Test
    void mouseClicked_insideFormerBorder_firesEntry() {

        AtomicInteger clicks = new AtomicInteger();
        ContextMenu menu = menu(10, 10, List.of(new ContextMenu.Entry(Component.literal("entry"), clicks::incrementAndGet)));

        boolean handled = menu.mouseClicked(
                menu.getX() + 1,
                menu.getY(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT);

        assertEquals(1, clicks.get());
        assertTrue(handled);
    }

    /**
     * Verifies that the right edge remains half-open.
     */
    @Test
    void mouseClicked_atRightEdge_doesNotFireEntry() {

        AtomicInteger clicks = new AtomicInteger();
        ContextMenu menu = menu(10, 10, List.of(new ContextMenu.Entry(Component.literal("entry"), clicks::incrementAndGet)));

        boolean handled = menu.mouseClicked(
                menu.getX() + menu.getWidth(),
                menu.getY(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT);

        assertEquals(0, clicks.get());
        assertFalse(handled);
    }

    /**
     * Creates a context menu fixture.
     *
     * @param x       The menu anchor x coordinate.
     * @param y       The menu anchor y coordinate.
     * @param entries The menu entries.
     * @return A context menu fixture.
     */
    private static ContextMenu menu(int x, int y, List<ContextMenu.Entry> entries) {

        return new ContextMenu(x, y, 100, 100, 0, 0, entries, ignored -> TEXT_WIDTH, () -> LINE_HEIGHT);
    }

    /**
     * Clicks the test menu at the given y-coordinate.
     *
     * @param menu   The context menu fixture.
     * @param mouseY The y-coordinate to click.
     * @return True if the click was handled.
     */
    private static boolean click(ContextMenu menu, int mouseY) {

        return menu.mouseClicked(
                menu.getX() + ConnectedButtonSurface.LABEL_PADDING,
                mouseY,
                GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /**
     * Creates deterministic menu entries.
     *
     * @param count The number of entries to create.
     * @return A list of menu entries.
     */
    private static List<ContextMenu.Entry> entries(int count) {

        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new ContextMenu.Entry(Component.literal("entry" + index), () -> {
                }))
                .toList();
    }
}
