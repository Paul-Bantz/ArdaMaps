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

package com.duom.ardamaps.core.data.config;

import com.google.gson.annotations.SerializedName;

/**
 * A vertically bounded map image range for a map layer.
 *
 * @param index     Stable range index, used for exploration keying and as the label in the range strip
 * @param path      Path or URL to the map data for this Y band
 * @param rangeMinY Minimum world Y covered by this range, inclusive
 * @param rangeMaxY Maximum world Y covered by this range, inclusive
 */
public record MapLayerRange(@SerializedName("index") int index, @SerializedName("path") String path,
                            @SerializedName("range_min_y") int rangeMinY,
                            @SerializedName("range_max_y") int rangeMaxY) {

    /**
     * Checks whether the given world Y coordinate is inside this range.
     *
     * @param y World Y coordinate to test.
     * @return True when {@code y} is inside this range, treating min/max order as irrelevant.
     */
    public boolean containsY(double y) {

        return y >= Math.min(rangeMinY, rangeMaxY) && y <= Math.max(rangeMinY, rangeMaxY);
    }

    /**
     * Computes the shortest vertical distance from the given world Y coordinate to this range.
     *
     * @param y World Y coordinate to measure.
     * @return Absolute distance from {@code y} to this band, or 0 when inside it.
     */
    public double distanceTo(double y) {

        int min = Math.min(rangeMinY, rangeMaxY);
        int max = Math.max(rangeMinY, rangeMaxY);

        if (y < min) return min - y;
        if (y > max) return y - max;

        return 0;
    }
}
