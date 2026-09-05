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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for region polygon rasterization into lookup texture coordinates.
 */
class RegionLookupBuilderTest {

    /**
     * Verifies that a simple square polygon is correctly rasterized into the lookup texture.
     */
    @Test
    void fillsSquarePolygon() {
        RegionLookupTexture texture = build(List.of(region("square")), List.of(List.of(square(10, 10, 40, 40))));

        assertEquals("square", texture.getRegionAt(dimension(), 20, 20));
        assertNull(texture.getRegionAt(dimension(), 80, 80));
    }

    /**
     * Builds a region lookup texture from the given regions and their polygon definitions.
     *
     * @param regions  the regions to include in the texture.
     * @param polygons the polygons defining each region's boundaries.
     * @return a rasterized region lookup texture.
     */
    private static RegionLookupTexture build(List<Region> regions, List<List<List<Vec2d>>> polygons) {
        return RegionLookupBuilder.build(dimension(), regions, polygons);
    }

    /**
     * Creates a region with the supplied name used as both its identifier and display name.
     *
     * @param name the region's name.
     * @return a test region fixture.
     */
    private static Region region(String name) {
        return new Region(name, name);
    }

    /**
     * Creates a square polygon from two opposite corners (axis-aligned).
     *
     * @param x1 the west coordinate.
     * @param z1 the north coordinate.
     * @param x2 the east coordinate.
     * @param z2 the south coordinate.
     * @return a list of vertices forming a square polygon.
     */
    private static List<Vec2d> square(double x1, double z1, double x2, double z2) {
        return List.of(
                new Vec2d(x1, z1),
                new Vec2d(x2, z1),
                new Vec2d(x2, z2),
                new Vec2d(x1, z2)
        );
    }

    /**
     * Creates a small test dimension spanning 101x101 blocks.
     *
     * @return a test dimension fixture.
     */
    private static Dimension dimension() {
        return new Dimension("Test", "test:dimension", 1f, 0, 101, 0, 101, true);
    }

    /**
     * Verifies that a concave polygon is rasterized correctly without incorrectly filling the interior notch.
     */
    @Test
    void fillsConcavePolygonWithoutFillingNotch() {
        List<Vec2d> concave = List.of(
                new Vec2d(10, 10),
                new Vec2d(50, 10),
                new Vec2d(50, 20),
                new Vec2d(25, 20),
                new Vec2d(25, 50),
                new Vec2d(10, 50)
        );

        RegionLookupTexture texture = build(List.of(region("concave")), List.of(List.of(concave)));

        assertEquals("concave", texture.getRegionAt(dimension(), 15, 15));
        assertNull(texture.getRegionAt(dimension(), 40, 40));
    }

    /**
     * Verifies that polygon vertices outside the dimension bounds are clamped safely.
     */
    @Test
    void clampsOutOfBoundsPolygon() {
        RegionLookupTexture texture = build(List.of(region("large")), List.of(List.of(square(-20, -20, 20, 20))));

        assertEquals("large", texture.getRegionAt(dimension(), 5, 5));
        assertNull(texture.getRegionAt(dimension(), 80, 80));
    }

    /**
     * Verifies that when two region polygons overlap, the last written region takes precedence.
     */
    @Test
    void overlapUsesLastWrittenRegion() {
        RegionLookupTexture texture = build(
                List.of(region("first"), region("second")),
                List.of(List.of(square(10, 10, 60, 60)), List.of(square(30, 30, 80, 80)))
        );

        assertEquals("second", texture.getRegionAt(dimension(), 40, 40));
        assertEquals("first", texture.getRegionAt(dimension(), 20, 20));
    }
}
