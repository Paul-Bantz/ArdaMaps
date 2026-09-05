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
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.map.geometry.PoleOfInaccessibility;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Builds quantized region geometry from provider region descriptors.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RegionGeometryBuilder {

    /**
     * Builds region geometry for a dimension.
     *
     * @param dimension The dimension definition.
     * @param sources   The source regions.
     * @return The generated geometry.
     */
    public static RegionGeometry build(Dimension dimension, Collection<RegionSource> sources) {

        Map<String, RegionSource> byId = new HashMap<>();
        for (RegionSource source : sources) {
            byId.put(source.id(), source);
        }

        List<RegionShape> shapes = new ArrayList<>();
        for (RegionSource source : sources) {
            RegionShape shape = buildShape(source, byId);
            if (shape != null) shapes.add(shape);
        }

        return new RegionGeometry(dimension.getId(), shapes.toArray(new RegionShape[0]), new Date());
    }

    /**
     * Builds one shape from one source region.
     *
     * @param source The source region.
     * @param byId   The region lookup by identifier.
     * @return The generated shape, or null when no valid rings exist.
     */
    private static RegionShape buildShape(RegionSource source, Map<String, RegionSource> byId) {

        List<int[]> rings = new ArrayList<>();
        Bounds bounds = new Bounds();

        for (List<Vec2d> polygon : source.polygons()) {
            int[] ring = quantizeRing(polygon);
            if (ring.length < 6) continue;

            rings.add(ring);
            bounds.include(ring);
        }

        if (rings.isEmpty()) return null;

        int[][] ringArray = rings.toArray(new int[0][]);
        PoleOfInaccessibility.Result label = PoleOfInaccessibility.compute(ringArray,
                bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());

        return new RegionShape(source.id(), source.name(), source.parentId(), depth(source, byId),
                ringArray, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(),
                label.anchor(), label.radius());
    }

    /**
     * Quantizes a floating-point polygon to integer world coordinates.
     *
     * @param polygon The source polygon vertices.
     * @return Interleaved x,z integer coordinates.
     */
    private static int[] quantizeRing(List<Vec2d> polygon) {

        if (polygon == null || polygon.isEmpty()) return new int[0];

        List<Integer> coordinates = new ArrayList<>(polygon.size() * 2);
        int previousX = Integer.MIN_VALUE;
        int previousZ = Integer.MIN_VALUE;

        for (Vec2d vertex : polygon) {
            int x = (int) Math.round(vertex.x());
            int z = (int) Math.round(vertex.y());
            if (x == previousX && z == previousZ) continue;

            coordinates.add(x);
            coordinates.add(z);
            previousX = x;
            previousZ = z;
        }

        if (coordinates.size() >= 4) {
            int firstX = coordinates.getFirst();
            int firstZ = coordinates.get(1);
            int lastX = coordinates.get(coordinates.size() - 2);
            int lastZ = coordinates.getLast();
            if (firstX == lastX && firstZ == lastZ) {
                coordinates.removeLast();
                coordinates.removeLast();
            }
        }

        int[] ring = new int[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            ring[i] = coordinates.get(i);
        }
        return ring;
    }

    /**
     * Computes a region's hierarchy depth by walking parent identifiers.
     *
     * @param source The source region.
     * @param byId   The source lookup by identifier.
     * @return The hierarchy depth, with roots at zero.
     */
    private static int depth(RegionSource source, Map<String, RegionSource> byId) {

        int depth = 0;
        String parentId = source.parentId();
        while (parentId != null) {
            RegionSource parent = byId.get(parentId);
            if (parent == null) break;

            depth++;
            parentId = parent.parentId();
        }

        return depth;
    }

    /**
     * Region source descriptor used by provider integrations.
     *
     * @param id        The stable region identifier.
     * @param name      The display name.
     * @param parentId  The parent region identifier, or null.
     * @param polygons  The polygons in world coordinates.
     */
    public record RegionSource(String id, String name, String parentId, List<List<Vec2d>> polygons) {
    }

    /**
     * Mutable bounds accumulator for one region shape.
     */
    private static final class Bounds {

        /** Current minimum X coordinate. */
        private int minX = Integer.MAX_VALUE;

        /** Current minimum Z coordinate. */
        private int minZ = Integer.MAX_VALUE;

        /** Current maximum X coordinate. */
        private int maxX = Integer.MIN_VALUE;

        /** Current maximum Z coordinate. */
        private int maxZ = Integer.MIN_VALUE;

        /**
         * Expands these bounds to include a ring.
         *
         * @param ring The interleaved x,z ring.
         */
        private void include(int[] ring) {

            for (int i = 0; i + 1 < ring.length; i += 2) {
                minX = Math.min(minX, ring[i]);
                minZ = Math.min(minZ, ring[i + 1]);
                maxX = Math.max(maxX, ring[i]);
                maxZ = Math.max(maxZ, ring[i + 1]);
            }
        }

        /**
         * @return The current minimum X coordinate.
         */
        private int minX() {

            return minX;
        }

        /**
         * @return The current minimum Z coordinate.
         */
        private int minZ() {

            return minZ;
        }

        /**
         * @return The current maximum X coordinate.
         */
        private int maxX() {

            return maxX;
        }

        /**
         * @return The current maximum Z coordinate.
         */
        private int maxZ() {

            return maxZ;
        }
    }
}
