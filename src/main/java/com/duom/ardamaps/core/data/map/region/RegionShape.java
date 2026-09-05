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

package com.duom.ardamaps.core.data.map.region;

import com.duom.ardamaps.core.data.Vec2d;

import java.io.Serializable;

/**
 * Quantized vector geometry for one ArdaRegions region.
 *
 * @param id          The stable region identifier.
 * @param name        The display name.
 * @param parentId    The parent region identifier, or null for roots.
 * @param depth       The depth within the region hierarchy.
 * @param rings       The region rings as interleaved x,z integer world coordinates.
 * @param minX        The minimum X coordinate covered by the shape.
 * @param minZ        The minimum Z coordinate covered by the shape.
 * @param maxX        The maximum X coordinate covered by the shape.
 * @param maxZ        The maximum Z coordinate covered by the shape.
 * @param labelAnchor The pole-of-inaccessibility anchor for future label rendering.
 * @param labelRadius The radius of the largest inscribed circle at {@code labelAnchor}.
 */
public record RegionShape(String id,
                          String name,
                          String parentId,
                          int depth,
                          int[][] rings,
                          int minX,
                          int minZ,
                          int maxX,
                          int maxZ,
                          Vec2d labelAnchor,
                          double labelRadius) implements Serializable {
}
