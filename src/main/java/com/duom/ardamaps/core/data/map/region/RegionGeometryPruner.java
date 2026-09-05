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

import com.duom.ardamaps.core.data.location.BasicLocation;

import java.util.*;

/**
 * Removes region shapes that duplicate non-region map locations.
 */
public final class RegionGeometryPruner {

    /** Location type that protects matching regions from pruning. */
    private static final String REGION_TYPE = "region";

    /**
     * Prevents utility class construction.
     */
    private RegionGeometryPruner() {

    }

    /**
     * Prunes duplicate-location regions and repairs the surviving hierarchy.
     *
     * @param geometry  The source geometry.
     * @param locations The locations to match against.
     * @return Pruned geometry, or the original instance when unchanged.
     */
    public static RegionGeometry prune(RegionGeometry geometry, Collection<? extends BasicLocation> locations) {

        if (geometry == null || geometry.regions() == null || geometry.regions().length == 0 || locations == null
                || locations.isEmpty()) {
            return geometry;
        }

        Map<String, List<BasicLocation>> locationsByKey = locationsByKey(locations);
        List<RegionShape> survivors = new ArrayList<>(geometry.regions().length);
        Set<String> survivorIds = new HashSet<>();
        Map<String, RegionShape> shapesById = new HashMap<>();
        boolean removed = false;

        for (RegionShape shape : geometry.regions()) {
            shapesById.put(shape.id(), shape);
            if (shouldPrune(shape, locationsByKey)) {
                removed = true;
            } else {
                survivors.add(shape);
                survivorIds.add(shape.id());
            }
        }

        if (!removed) return geometry;

        Map<String, String> repairedParents = new HashMap<>();
        Map<String, Integer> repairedDepths = new HashMap<>();
        RegionShape[] repaired = new RegionShape[survivors.size()];

        for (RegionShape shape : survivors) {
            String parentId = repairedParentId(shape, shapesById, survivorIds);
            repairedParents.put(shape.id(), parentId);
        }

        for (int index = 0; index < survivors.size(); index++) {
            RegionShape shape = survivors.get(index);
            int depth = repairedDepth(shape.id(), repairedParents, repairedDepths, new HashSet<>());
            repaired[index] = new RegionShape(shape.id(), shape.name(), repairedParents.get(shape.id()), depth,
                    shape.rings(), shape.minX(), shape.minZ(), shape.maxX(), shape.maxZ(), shape.labelAnchor(),
                    shape.labelRadius());
        }

        return new RegionGeometry(geometry.dimensionId(), repaired, geometry.lastUpdate());
    }

    /**
     * Indexes locations by normalized id and display name.
     *
     * @param locations The source locations.
     * @return The indexed locations.
     */
    private static Map<String, List<BasicLocation>> locationsByKey(Collection<? extends BasicLocation> locations) {

        Map<String, List<BasicLocation>> byKey = new HashMap<>();

        for (BasicLocation location : locations) {
            addLocationKey(byKey, RegionNameMatcher.normalize(location.getId()), location);
            addLocationKey(byKey, RegionNameMatcher.normalize(location.getName()), location);
        }

        return byKey;
    }

    /**
     * Adds one non-empty normalized location key.
     *
     * @param byKey    The mutable index.
     * @param key      The normalized key.
     * @param location The location to index.
     */
    private static void addLocationKey(Map<String, List<BasicLocation>> byKey, String key, BasicLocation location) {

        if (key.isEmpty()) return;
        byKey.computeIfAbsent(key, _ -> new ArrayList<>()).add(location);
    }

    /**
     * Checks whether a region should be pruned.
     *
     * @param shape          The region shape.
     * @param locationsByKey The normalized location index.
     * @return true when the shape duplicates a non-region location.
     */
    private static boolean shouldPrune(RegionShape shape, Map<String, List<BasicLocation>> locationsByKey) {

        if (shape.depth() == 0) return false;

        List<BasicLocation> matches = new ArrayList<>();
        collectMatches(matches, locationsByKey, RegionNameMatcher.normalize(shape.id()));
        collectMatches(matches, locationsByKey, RegionNameMatcher.normalize(shape.name()));

        if (matches.isEmpty()) return false;

        for (BasicLocation location : matches)
            if (isRegionLocation(location)) return false;

        return true;
    }

    /**
     * Adds matching locations for a normalized key.
     *
     * @param matches        The mutable match list.
     * @param locationsByKey The normalized location index.
     * @param key            The normalized key.
     */
    private static void collectMatches(List<BasicLocation> matches, Map<String, List<BasicLocation>> locationsByKey,
                                       String key) {

        if (key.isEmpty()) return;

        List<BasicLocation> locations = locationsByKey.get(key);
        if (locations != null) matches.addAll(locations);
    }

    /**
     * Checks whether a location is typed as a region.
     *
     * @param location The location to inspect.
     * @return true when the location has region type.
     */
    private static boolean isRegionLocation(BasicLocation location) {

        if (location.getTypes() == null) return false;

        for (String type : location.getTypes())
            if (REGION_TYPE.equals(type.toLowerCase(Locale.ROOT))) return true;

        return false;
    }

    /**
     * Finds the nearest surviving ancestor for a shape.
     *
     * @param shape       The source shape.
     * @param shapesById  All original shapes by id.
     * @param survivorIds Surviving shape ids.
     * @return The nearest surviving parent id, or null.
     */
    private static String repairedParentId(RegionShape shape, Map<String, RegionShape> shapesById, Set<String> survivorIds) {

        String parentId = shape.parentId();
        Set<String> visited = new HashSet<>();

        while (parentId != null && visited.add(parentId)) {
            if (survivorIds.contains(parentId)) return parentId;

            RegionShape parent = shapesById.get(parentId);
            parentId = parent == null ? null : parent.parentId();
        }

        return null;
    }

    /**
     * Computes repaired depth from repaired parent links.
     *
     * @param id             The shape id.
     * @param repairedParent Repaired parent ids by shape id.
     * @param memo           Computed depths by shape id.
     * @param visiting       The recursion guard.
     * @return The repaired depth.
     */
    private static int repairedDepth(String id, Map<String, String> repairedParent, Map<String, Integer> memo,
                                     Set<String> visiting) {

        Integer cached = memo.get(id);
        if (cached != null) return cached;

        String parentId = repairedParent.get(id);
        int depth;
        if (parentId == null || !visiting.add(id)) {
            depth = 0;
        } else {
            depth = repairedDepth(parentId, repairedParent, memo, visiting) + 1;
            visiting.remove(id);
        }

        memo.put(id, depth);
        return depth;
    }
}
