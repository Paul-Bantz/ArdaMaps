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

package com.duom.ardamaps.core.data.map.markers;

import com.duom.ardamaps.core.data.json.ColorIntTypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import net.minecraft.util.Identifier;

/**
 * Definition of a marker type, including its name, icon, and colour.
 *
 * @param name           the name of the marker type
 * @param icon           the identifier for the marker icon texture
 * @param color          the colour of the marker in ARGB format (e.g., 0xFFFF0000 for red)
 * @param highlightColor the colour of the marker when highlighted, in ARGB format
 */
public record MarkerType(String name,
                         @SerializedName("icon") Identifier icon,
                         @SerializedName("color") @JsonAdapter(ColorIntTypeAdapter.class) int color,
                         @SerializedName("highlight_color") @JsonAdapter(ColorIntTypeAdapter.class) int highlightColor) {
}