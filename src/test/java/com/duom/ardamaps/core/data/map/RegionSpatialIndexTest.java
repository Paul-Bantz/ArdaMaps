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
import com.duom.ardamaps.core.data.map.region.RegionShape;
import com.duom.ardamaps.core.data.map.region.RegionSpatialIndex;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for exact region spatial lookup.
 */
class RegionSpatialIndexTest {

    /**
     * Verifies deepest matching region wins over a containing parent.
     */
    @SuppressWarnings("DataFlowIssue")
    @Test
    void getRegionAt_prefersDeepestMatch() {

        RegionShape root = shape("root", "Root", null, 0, 0, 0, 100, 100);
        RegionShape child = shape("child", "Child", "root", 1, 25, 25, 75, 75);
        RegionShape leaf = shape("leaf", "Leaf", "child", 2, 40, 40, 60, 60);
        RegionSpatialIndex index = index(root, child, leaf);

        assertEquals("Leaf", index.getRegionAt(50, 50).name());
        assertEquals("Child", index.getRegionAt(30, 30).name());
        assertEquals("Root", index.getRegionAt(10, 10).name());
        assertNull(index.getRegionAt(150, 150));
    }

    /**
     * Verifies a deep leaf resolves to the top-most parent.
     */
    @Test
    void rootOf_returnsRootForDeepLeaf() {

        RegionShape root = shape("root", "Root", null, 0, 0, 0, 100, 100);
        RegionShape child = shape("child", "Child", "root", 1, 25, 25, 75, 75);
        RegionShape leaf = shape("leaf", "Leaf", "child", 2, 40, 40, 60, 60);
        RegionSpatialIndex index = index(root, child, leaf);

        assertSame(root, index.rootOf(leaf));
    }

    /**
     * Verifies a root shape resolves to itself.
     */
    @Test
    void rootOf_returnsShapeForRoot() {

        RegionShape root = shape("root", "Root", null, 0, 0, 0, 100, 100);
        RegionSpatialIndex index = index(root);

        assertSame(root, index.rootOf(root));
    }

    /**
     * Verifies a missing parent stops traversal at the current shape.
     */
    @Test
    void rootOf_survivesMissingParentId() {

        RegionShape child = shape("child", "Child", "missing", 1, 25, 25, 75, 75);
        RegionSpatialIndex index = index(child);

        assertSame(child, index.rootOf(child));
    }

    /**
     * Verifies a parent cycle cannot trap root traversal.
     */
    @Test
    void rootOf_survivesParentCycle() {

        RegionShape first = shape("first", "First", "second", 1, 0, 0, 100, 100);
        RegionShape second = shape("second", "Second", "first", 1, 25, 25, 75, 75);
        RegionSpatialIndex index = index(first, second);

        assertSame(first, index.rootOf(first));
        assertSame(second, index.rootOf(second));
    }

    /**
     * Builds a spatial index fixture.
     *
     * @param shapes The shapes to index.
     * @return The spatial index fixture.
     */
    private static RegionSpatialIndex index(RegionShape... shapes) {

        RegionGeometry geometry = new RegionGeometry("test:dimension", shapes, new Date());
        return new RegionSpatialIndex(geometry, dimension());
    }

    /**
     * Creates a rectangular shape fixture.
     *
     * @param id       The shape identifier.
     * @param name     The display name.
     * @param parentId The parent identifier.
     * @param depth    The hierarchy depth.
     * @param minX     The minimum X coordinate.
     * @param minZ     The minimum Z coordinate.
     * @param maxX     The maximum X coordinate.
     * @param maxZ     The maximum Z coordinate.
     * @return The shape fixture.
     */
    private static RegionShape shape(String id, String name, String parentId, int depth,
                                     int minX, int minZ, int maxX, int maxZ) {

        int[] ring = {minX, minZ, maxX, minZ, maxX, maxZ, minX, maxZ};
        return new RegionShape(id, name, parentId, depth, new int[][]{ring},
                minX, minZ, maxX, maxZ, new Vec2d((minX + maxX) / 2.0, (minZ + maxZ) / 2.0), 1.0);
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
