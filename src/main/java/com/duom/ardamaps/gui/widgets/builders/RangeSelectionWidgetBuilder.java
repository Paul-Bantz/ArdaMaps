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

package com.duom.ardamaps.gui.widgets.builders;

import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.gui.widgets.RangeSelectionWidget;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/**
 * Builder class for constructing {@link RangeSelectionWidget} instances with a fluent API.
 * Provides convenient methods to configure all aspects of a range selection widget including
 * size, label, ranges, item width, and behaviour.
 */
public class RangeSelectionWidgetBuilder {

    /** width of the range selection widget */
    private int width;

    /** height of the range selection widget */
    private int height;

    /** fixed label displayed before range cells */
    private Component label = Component.empty();

    /** ranges displayed by the range selection widget */
    private List<MapLayerRange> ranges = List.of();

    /** fixed range cell width, or zero to compute width automatically */
    private int itemWidth = 0;

    /** callback function invoked when a range is selected */
    private Consumer<MapLayerRange> onSelect;

    /** Private constructor to prevent direct instantiation. Use the static create() method instead. */
    private RangeSelectionWidgetBuilder() {
    }

    /** Static factory method to create a new instance of RangeSelectionWidgetBuilder. */
    public static RangeSelectionWidgetBuilder create() {
        return new RangeSelectionWidgetBuilder();
    }

    /**
     * Sets the size of the range selection widget.
     *
     * @param width  width of the range selection widget
     * @param height height of the range selection widget
     * @return the current instance of RangeSelectionWidgetBuilder for method chaining
     */
    public RangeSelectionWidgetBuilder setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Sets the fixed label displayed before range cells.
     *
     * @param label fixed label displayed before range cells
     * @return the current instance of RangeSelectionWidgetBuilder for method chaining
     */
    public RangeSelectionWidgetBuilder setLabel(Component label) {
        this.label = label;
        return this;
    }

    /**
     * Sets the ranges displayed by the range selection widget.
     *
     * @param ranges ranges displayed by the range selection widget
     * @return the current instance of RangeSelectionWidgetBuilder for method chaining
     */
    public RangeSelectionWidgetBuilder setRanges(List<MapLayerRange> ranges) {
        this.ranges = ranges;
        return this;
    }

    /**
     * Sets the fixed width of each range cell.
     *
     * @param itemWidth fixed range cell width, or zero to compute width automatically
     * @return the current instance of RangeSelectionWidgetBuilder for method chaining
     */
    public RangeSelectionWidgetBuilder setItemWidth(int itemWidth) {
        this.itemWidth = itemWidth;
        return this;
    }

    /**
     * Sets the callback function to be called when a range is selected.
     *
     * @param onSelect callback function to be called when a range is selected
     * @return the current instance of RangeSelectionWidgetBuilder for method chaining
     */
    public RangeSelectionWidgetBuilder setOnSelect(Consumer<MapLayerRange> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /**
     * Builds and returns a new instance of RangeSelectionWidget based on the configured properties of this builder.
     *
     * @return a new instance of RangeSelectionWidget with the configured properties
     */
    public RangeSelectionWidget build() {

        if (itemWidth <= 0) {
            return new RangeSelectionWidget(
                    0,
                    0,
                    width,
                    height,
                    label,
                    ranges,
                    onSelect
            );
        }

        return new RangeSelectionWidget(
                0,
                0,
                width,
                height,
                label,
                ranges,
                itemWidth,
                onSelect
        );
    }
}
