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

package com.duom.ardamaps.core.data.map;

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.data.map.region.RegionGeometryBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for region geometry generation.
 */
class RegionGeometryBuilderTest {

    /**
     * Verifies build quantizes rings, computes bounds, and assigns hierarchy depth.
     */
    @Test
    void build_quantizesRingsAndComputesDepth() {

        RegionGeometry geometry = RegionGeometryBuilder.build(dimension(), List.of(
                source("root", "Root", null, square(0, 0, 100, 100)),
                source("child", "Child", "root", square(10.2, 10.2, 20.4, 20.4))
        ));

        assertEquals("test:dimension", geometry.dimensionId());
        assertEquals(2, geometry.regions().length);
        assertEquals(0, geometry.regions()[0].depth());
        assertEquals(1, geometry.regions()[1].depth());
        assertEquals(10, geometry.regions()[1].minX());
        assertEquals(20, geometry.regions()[1].maxX());
        assertArrayEquals(new int[]{10, 10, 20, 10, 20, 20, 10, 20}, geometry.regions()[1].rings()[0]);
        assertNotNull(geometry.regions()[1].labelAnchor());
    }

    /**
     * Creates a region source fixture.
     *
     * @param id       The region identifier.
     * @param name     The display name.
     * @param parentId The parent identifier.
     * @param polygon  The polygon vertices.
     * @return The source fixture.
     */
    private static RegionGeometryBuilder.RegionSource source(String id, String name, String parentId, List<Vec2d> polygon) {

        return new RegionGeometryBuilder.RegionSource(id, name, parentId, List.of(polygon));
    }

    /**
     * Creates a square polygon fixture.
     *
     * @param x1 The west coordinate.
     * @param z1 The north coordinate.
     * @param x2 The east coordinate.
     * @param z2 The south coordinate.
     * @return The polygon vertices.
     */
    private static List<Vec2d> square(double x1, double z1, double x2, double z2) {

        return List.of(new Vec2d(x1, z1), new Vec2d(x2, z1), new Vec2d(x2, z2), new Vec2d(x1, z2));
    }

    /**
     * Creates a test dimension fixture.
     *
     * @return The test dimension.
     */
    private static Dimension dimension() {

        return new Dimension("Test", "test:dimension", 1f, 0, 100, 0, 100, true);
    }
}
