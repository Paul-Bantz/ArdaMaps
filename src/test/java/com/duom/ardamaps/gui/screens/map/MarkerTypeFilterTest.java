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

import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.markers.MarkerType;
import com.duom.ardamaps.core.data.map.markers.MarkersDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.duom.ardamaps.gui.ModConstants.modId;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for session marker type filter state.
 */
class MarkerTypeFilterTest {

    /**
     * Verifies that refresh lists distinct present marker types sorted by display name.
     */
    @Test
    void refresh_listsDistinctPresentTypesSortedByDisplayName() {

        MarkerTypeFilter filter = filter(List.of(
                location("test:overworld", "ruin"),
                location("test:overworld", "city"),
                location("test:overworld", "ruin")
        ));

        filter.refresh("test:overworld");

        assertEquals(List.of("CITY", "RUIN"), filter.available().stream().map(MarkerTypeFilter.Entry::key).toList());
        assertTrue(filter.isAllEnabled());
    }

    /**
     * Verifies that toggles and bulk actions update enabled keys.
     */
    @Test
    void toggleAllNone_updateEnabledKeys() {

        MarkerTypeFilter filter = filter(List.of(location("test:overworld", "city"), location("test:overworld", "ruin")));
        filter.refresh("test:overworld");

        filter.toggle("CITY");

        assertFalse(filter.isEnabled("CITY"));
        assertEquals(1, filter.enabledKeys().size());

        filter.disableAll();

        assertTrue(filter.isNoneEnabled());

        filter.enableAll();

        assertTrue(filter.isAllEnabled());
    }

    /**
     * Verifies that refresh intersects existing enabled keys and enables newly appearing keys.
     */
    @Test
    void refresh_intersectsEnabledKeysAndEnablesNewKeys() {

        List<LocationClient> firstLocations = List.of(location("test:overworld", "city"), location("test:overworld", "ruin"));
        List<LocationClient> secondLocations = List.of(location("test:overworld", "city"), location("test:overworld", "town"));
        MarkerTypeFilter filter = new MarkerTypeFilter(
                new java.util.function.Function<>() {

                    /** Number of refresh calls. */
                    private int calls;

                    /**
                     * Gets locations for the current refresh pass.
                     *
                     * @param dimensionId The dimension id.
                     * @return The configured location list for the pass.
                     */
                    @Override
                    public List<LocationClient> apply(String dimensionId) {

                        calls++;
                        return calls == 1 ? firstLocations : secondLocations;
                    }
                },
                MarkerTypeFilterTest::definition);

        filter.refresh("test:overworld");
        filter.toggle("CITY");
        filter.refresh("test:overworld");

        assertFalse(filter.isEnabled("CITY"));
        assertTrue(filter.isEnabled("TOWN"));
        assertFalse(filter.isEnabled("RUIN"));
    }

    /**
     * Creates a marker type filter fixture.
     *
     * @param locations Locations supplied to the filter.
     * @return The filter fixture.
     */
    private static MarkerTypeFilter filter(List<LocationClient> locations) {

        return new MarkerTypeFilter(_ -> locations, MarkerTypeFilterTest::definition);
    }

    /**
     * Creates a location fixture.
     *
     * @param world The world id.
     * @param type  The location type.
     * @return The location fixture.
     */
    @SuppressWarnings("SameParameterValue")
    private static LocationClient location(String world, String type) {

        LocationClient location = new LocationClient();
        location.setWorld(world);
        location.setTypes(List.of(type));
        return location;
    }

    /**
     * Creates a marker definition fixture.
     *
     * @return The marker definition fixture.
     */
    private static MarkersDefinition definition() {

        MarkerType defaultType = new MarkerType("Landmark", "ardamaps:landmark", 1, 2);
        MarkerType unknownType = new MarkerType("Unknown", "ardamaps:unknown", 3, 4);
        MarkerType cityType = new MarkerType("City", "ardamaps:city", 5, 6);
        MarkerType ruinType = new MarkerType("Ruin", "ardamaps:ruin", 7, 8);
        MarkerType townType = new MarkerType("Town", "ardamaps:town", 9, 10);
        return new MarkersDefinition(
                modId("marker"),
                modId("marker_visited"),
                35,
                30,
                0,
                0,
                unknownType,
                defaultType,
                Map.of("CITY", cityType, "LANDMARK", defaultType, "RUIN", ruinType, "TOWN", townType)
        );
    }
}
