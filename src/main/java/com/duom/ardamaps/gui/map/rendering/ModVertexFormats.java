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

package com.duom.ardamaps.gui.map.rendering;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/**
 * Vertex formats used by ArdaMaps GUI render pipelines.
 */
public final class ModVertexFormats {

    /** Paper texture coordinates for the fog-of-war overlay. */
    public static final VertexFormatElement UV_PAPER =
            VertexFormatElement.register(freeId(), 0, VertexFormatElement.Type.FLOAT, false, 2);

    /** BlueMap tile parameters: sunlight, ambient light, LOD scale, texel-size X. */
    public static final VertexFormatElement TILE_PARAMS =
            VertexFormatElement.register(freeId(), 0, VertexFormatElement.Type.FLOAT, false, 4);

    /** Position + main UV + paper UV. */
    public static final VertexFormat POSITION_TEX_PAPER = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("UVPaper", UV_PAPER)
            .build();

    /** Position + main UV + BlueMap tile parameters. */
    public static final VertexFormat POSITION_TEX_PARAMS = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("TileParams", TILE_PARAMS)
            .build();

    private ModVertexFormats() {
    }

    private static int freeId() {

        for (int id = 0; id < VertexFormatElement.MAX_COUNT; id++)
            if (VertexFormatElement.byId(id) == null) return id;

        throw new IllegalStateException("No free vertex format element ids remain");
    }
}
