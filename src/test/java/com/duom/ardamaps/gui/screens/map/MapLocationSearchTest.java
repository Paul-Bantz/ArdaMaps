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

package com.duom.ardamaps.gui.screens.map;

import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.location.LocationClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MapLocationSearch}.
 */
class MapLocationSearchTest {

    /**
     * Verifies that a null dimension id returns an empty result immediately.
     */
    @Test
    void search_nullDimension_returnsEmpty() {

        MapLocationSearch search = search(List.of(revealed("Minas Tirith", "city")));

        List<LocationClient> result = search.search(null, null, "minas");

        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that a query for a dimension with no locations returns an empty result.
     * The supplier mirrors how {@code ArdaMapsClient.CONFIG.getLocations} already filters by dimension.
     */
    @Test
    void search_noDimensionLocations_returnsEmpty() {

        // Supplier returns locations only for gondor; query for eriador gets nothing
        MapLocationSearch search = new MapLocationSearch(
                dimensionId -> "test:gondor".equals(dimensionId)
                        ? List.of(revealed("Minas Tirith", "city"))
                        : List.of(),
                types -> types == null || types.isEmpty() ? "UNKNOWN" : types.getFirst().toUpperCase(),
                loc -> loc.getExplorationState() != null
                        && loc.getExplorationState().ordinal() > ExplorationState.VISIBLE.ordinal(),
                LocationClient::rawName);

        List<LocationClient> result = search.search("test:eriador", null, "minas");

        assertTrue(result.isEmpty(), "Expected no results when the dimension has no matching locations");
    }

    /**
     * Verifies that name matching is case-insensitive.
     */
    @Test
    void search_caseInsensitiveNameMatch() {

        MapLocationSearch search = search(List.of(revealed("Minas Tirith", "city")));

        assertFalse(search.search("test:gondor", null, "MINAS").isEmpty());
        assertFalse(search.search("test:gondor", null, "minas tirith").isEmpty());
        assertTrue(search.search("test:gondor", null, "rohan").isEmpty());
    }

    /**
     * Verifies that unrevealed locations are excluded from results.
     */
    @Test
    void search_hiddenLocation_excluded() {

        LocationClient hidden = location("Minas Morgul", "city");
        hidden.updateExplorationState(ExplorationState.HIDDEN);

        MapLocationSearch search = search(List.of(hidden));

        assertTrue(search.search("test:gondor", null, "minas").isEmpty());
    }

    /**
     * Verifies that marker-type filtering excludes locations whose type is not enabled.
     */
    @Test
    void search_markerTypeFilter_excludesDisabledTypes() {

        LocationClient city = revealed("Minas Tirith", "city");
        LocationClient ruin = revealed("Weathertop", "ruin");

        MapLocationSearch search = search(List.of(city, ruin));

        // Keys are upper-cased by the injected resolver (mirrors MarkersDefinition.markerTypeKey)
        List<LocationClient> result = search.search("test:gondor", Set.of("RUIN"), "");

        assertFalse(result.stream().anyMatch(l -> l == city), "city type should be filtered out");
        assertTrue(result.stream().anyMatch(l -> l == ruin), "ruin type should be included");
    }

    /**
     * Verifies that null enabledMarkerTypes means all types pass the filter.
     */
    @Test
    void search_nullMarkerTypes_includesAll() {

        MapLocationSearch search = search(List.of(
                revealed("Minas Tirith", "city"),
                revealed("Weathertop", "ruin")));

        List<LocationClient> result = search.search("test:gondor", null, "");

        assertEquals(2, result.size());
    }

    /**
     * Creates a search backed by a fixed list of locations for the canonical test dimension.
     *
     * @param locations The locations the search will return.
     * @return A configured search instance.
     */
    private static MapLocationSearch search(List<LocationClient> locations) {

        // Type key resolver mirrors MarkersDefinition.markerTypeKey: first type uppercased, fallback "UNKNOWN"
        // Revealed predicate avoids calling ArdaMapsClient.CONFIG: checks exploration state ordinal directly.
        return new MapLocationSearch(
                _ -> locations,
                types -> types == null || types.isEmpty() ? "UNKNOWN" : types.getFirst().toUpperCase(),
                loc -> loc.getExplorationState() != null
                        && loc.getExplorationState().ordinal() > ExplorationState.VISIBLE.ordinal(),
                LocationClient::rawName);
    }

    /**
     * Creates a revealed location fixture in the canonical test dimension.
     *
     * @param name The location name.
     * @param type The marker type key.
     * @return A revealed location.
     */
    private static LocationClient revealed(String name, String type) {

        LocationClient loc = location(name, type);
        loc.updateExplorationState(ExplorationState.REVEALED);
        return loc;
    }

    /**
     * Creates a location fixture in the canonical test dimension with hidden exploration state.
     *
     * @param name The location name.
     * @param type The marker type key.
     * @return A location with default (hidden) exploration state.
     */
    private static LocationClient location(String name, String type) {

        LocationClient loc = new LocationClient();
        loc.setName(name);
        loc.setTypes(List.of(type));
        loc.setWorld("test:gondor");
        return loc;
    }
}
