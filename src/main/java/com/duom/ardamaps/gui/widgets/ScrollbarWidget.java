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

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * A reusable scrollbar widget that encapsulates scroll state and rendering.
 * <p>
 * Colours, dimensions and scroll speed are set via the constructor.
 * Callers update the maximum scroll offset via {@link #setMaxOffset(int)} whenever
 * content changes, and delegate mouse-scroll events to {@link #scroll(double)}.
 */
public class ScrollbarWidget {

    /** Visual width of the scrollbar (track + thumb) */
    private final int width;

    /** Minimum height of the draggable thumb */
    private final int minThumbHeight;

    /** ARGB colour of the scrollbar track */
    private final int trackColor;

    /** ARGB colour of the scrollbar thumb */
    private final int thumbColor;

    /**
     * Multiplier applied to the raw scroll delta.
     * Use {@code 1} for item-based scrolling, higher values (e.g. {@code 12}) for pixel-based scrolling.
     */
    private final int scrollSpeed;

    /** Current scroll offset (pixels or items, depending on how the caller interprets it) */
    @Getter
    private int scrollOffset = 0;

    /** Maximum reachable scroll offset; updated by {@link #setMaxOffset(int)} */
    private int maxOffset = 0;

    /**
     * Creates a new {@code ScrollbarWidget}.
     *
     * @param width          Visual width of the scrollbar in pixels
     * @param minThumbHeight Minimum height of the thumb in pixels
     * @param trackColor     ARGB colour of the track background
     * @param thumbColor     ARGB colour of the draggable thumb
     * @param scrollSpeed    Multiplier applied to the scroll delta (≥ 1)
     */
    public ScrollbarWidget(int width, int minThumbHeight, int trackColor, int thumbColor, int scrollSpeed) {

        this.width = width;
        this.minThumbHeight = minThumbHeight;
        this.trackColor = trackColor;
        this.thumbColor = thumbColor;
        this.scrollSpeed = Math.max(1, scrollSpeed);
    }

    /**
     * Updates the maximum scroll offset.
     * The current offset is clamped so it stays within the new bounds.
     *
     * @param maxOffset New maximum offset (>= 0)
     */
    public void setMaxOffset(int maxOffset) {
        this.maxOffset = Math.max(0, maxOffset);
        this.scrollOffset = MathHelper.clamp(scrollOffset, 0, this.maxOffset);
    }

    /**
     * Resets the scroll offset back to zero.
     * Call this whenever the underlying content is reset or refreshed.
     */
    public void resetOffset() {
        scrollOffset = 0;
    }

    /**
     * Scrolls by the given mouse-wheel delta, applying {@code scrollSpeed} as a multiplier.
     * <p>
     * A positive {@code delta} scrolls up (decreases offset); a negative one scrolls down.
     * The offset is always clamped to {@code [0, maxOffset]}.
     *
     * @param delta Raw mouse-wheel delta (positive = scroll up)
     * @return {@code true} if the scroll offset actually changed; {@code false} otherwise
     */
    public boolean scroll(double delta) {

        if (maxOffset <= 0) return false;

        int prev = scrollOffset;
        scrollOffset = MathHelper.clamp(scrollOffset - (int) (delta * scrollSpeed), 0, maxOffset);

        return scrollOffset != prev;
    }

    /**
     * Renders the scrollbar track and thumb.
     * <p>
     * The thumb height is proportional to {@code visibleCount / totalCount}.
     * Its position reflects the current scroll offset relative to {@link #maxOffset}.
     *
     * @param context      Drawing context
     * @param trackX       Left edge of the scrollbar track
     * @param trackY       Top edge of the scrollbar track
     * @param trackHeight  Full pixel height of the track area
     * @param visibleCount Number of visible units (items or pixels)
     * @param totalCount   Total number of units in the content
     */
    public void render(DrawContext context, int trackX, int trackY, int trackHeight,
                       int visibleCount, int totalCount) {

        // Track background
        context.fill(trackX, trackY, trackX + width, trackY + trackHeight, trackColor);

        // Thumb - proportional height, clamped to minThumbHeight
        int thumbHeight = Math.max(minThumbHeight,
                (int) ((float) visibleCount / totalCount * trackHeight));
        int scrollRange = trackHeight - thumbHeight;
        int thumbY = trackY + (maxOffset > 0
                ? (int) ((float) scrollOffset / maxOffset * scrollRange)
                : 0);

        context.fill(trackX, thumbY, trackX + width, thumbY + thumbHeight, thumbColor);
    }
}

