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
import com.duom.ardamaps.core.data.location.BasicLocation;
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.data.map.region.RegionGeometryPruner;
import com.duom.ardamaps.core.data.map.region.RegionNameMatcher;
import com.duom.ardamaps.core.data.map.region.RegionShape;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for duplicate-location region pruning.
 */
class RegionGeometryPrunerTest {

    /**
     * Prunes named non-region location duplicates while keeping region-typed matches.
     */
    @Test
    void prune_removesNamedNonRegionDuplicates() {

        RegionGeometry geometry = geometry(
                shape("shire", "The Shire", null, 0),
                shape("bag_end", "Bag End", "shire", 1),
                shape("frogmorton", "Frogmorton", "shire", 1),
                shape("bree", "Bree", "shire", 1),
                shape("eastfarthing", "The Eastfarthing", "shire", 1)
        );

        RegionGeometry pruned = RegionGeometryPruner.prune(geometry, List.of(
                location("bag-end", "Bag End", "house"),
                location("frogmorton", "Frogmorton", "village"),
                location("bree", "Bree", "village"),
                location("eastfarthing", "Eastfarthing", "REGION")
        ));

        assertEquals(2, pruned.regions().length);
        assertEquals("shire", pruned.regions()[0].id());
        assertEquals("eastfarthing", pruned.regions()[1].id());
    }

    /**
     * Keeps top-level matches even when their only location match is not region-typed.
     */
    @Test
    void prune_keepsDepthZeroDuplicates() {

        RegionGeometry geometry = geometry(shape("fangorn", "Fangorn", null, 0));

        RegionGeometry pruned = RegionGeometryPruner.prune(geometry, List.of(location("fangorn", "Fangorn", "forest")));

        assertSame(geometry, pruned);
    }

    /**
     * Normalizes accents, ash ligatures, parenthetical suffixes, punctuation, and leading articles.
     */
    @Test
    void normalize_handlesArdaNameVariants() {

        assertEquals("annuminas", RegionNameMatcher.normalize("Annúminas"));
        assertEquals("druadan", RegionNameMatcher.normalize("Drúadan"));
        assertEquals("grenewaet", RegionNameMatcher.normalize("Grenewæt"));
        assertEquals("elostirion", RegionNameMatcher.normalize("Elostirion (The White Towers)"));
        assertEquals("eastfarthing", RegionNameMatcher.normalize("The Eastfarthing"));
        assertEquals("bag end", RegionNameMatcher.normalize("bag_end"));
        assertEquals("bag end", RegionNameMatcher.normalize("bag-end"));
    }

    /**
     * Reparents survivors to the nearest surviving ancestor and recomputes depth.
     */
    @Test
    void prune_reparentsSurvivorsThroughRemovedAncestors() {

        RegionGeometry geometry = geometry(
                shape("root", "Root", null, 0),
                shape("town", "Town", "root", 1),
                shape("inn", "The Prancing Pony", "town", 2),
                shape("room", "Room", "inn", 3)
        );

        RegionGeometry pruned = RegionGeometryPruner.prune(geometry, List.of(location("town", "Town", "village")));

        assertEquals(3, pruned.regions().length);
        assertEquals("root", pruned.regions()[1].parentId());
        assertEquals(1, pruned.regions()[1].depth());
        assertEquals("inn", pruned.regions()[2].parentId());
        assertEquals(2, pruned.regions()[2].depth());
    }

    /**
     * Returns the same instance once no more duplicate regions remain.
     */
    @Test
    void prune_isIdempotent() {

        RegionGeometry geometry = geometry(shape("root", "Root", null, 0), shape("bree", "Bree", "root", 1));
        List<BasicLocation> locations = List.of(location("bree", "Bree", "village"));

        RegionGeometry pruned = RegionGeometryPruner.prune(geometry, locations);
        RegionGeometry second = RegionGeometryPruner.prune(pruned, locations);

        assertSame(pruned, second);
    }

    /**
     * Creates geometry from shapes.
     *
     * @param shapes The region shapes.
     * @return The geometry fixture.
     */
    private static RegionGeometry geometry(RegionShape... shapes) {

        return new RegionGeometry("test:dimension", shapes, new Date(1234L));
    }

    /**
     * Creates a shape fixture.
     *
     * @param id The shape id.
     * @param name The shape name.
     * @param parentId The parent id.
     * @param depth The hierarchy depth.
     * @return The shape fixture.
     */
    private static RegionShape shape(String id, String name, String parentId, int depth) {

        return new RegionShape(id, name, parentId, depth, new int[][]{{0, 0, 10, 0, 10, 10, 0, 10}},
                0, 0, 10, 10, new Vec2d(5, 5), 5);
    }

    /**
     * Creates a location fixture.
     *
     * @param id The location id.
     * @param name The location name.
     * @param type The location type.
     * @return The location fixture.
     */
    private static BasicLocation location(String id, String name, String type) {

        BasicLocation location = new BasicLocation();
        location.setId(id);
        location.setName(name);
        location.setTypes(List.of(type));
        return location;
    }
}
