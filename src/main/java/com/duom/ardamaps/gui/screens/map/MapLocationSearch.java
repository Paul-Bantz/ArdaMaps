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

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.markers.MarkersManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure search helper for the map screen location search overlay.
 * Filters locations by dimension, marker-type visibility and name substring.
 */
@Environment(EnvType.CLIENT)
public final class MapLocationSearch {

    /** Supplies the current list of locations for a given dimension id. */
    private final Function<String, List<LocationClient>> locationsByDimension;

    /** Converts a location's type list to the canonical marker-type key used in enabled-type sets. */
    private final Function<List<String>, String> typeKeyResolver;

    /** Decides whether a location is visible to the player (wraps {@link LocationClient#isRevealed}). */
    private final java.util.function.Predicate<LocationClient> revealedPredicate;

    /** Extracts the display name from a location for substring matching. */
    private final Function<LocationClient, String> nameExtractor;

    /**
     * Creates a search backed by the active client configuration.
     */
    public MapLocationSearch() {

        this(dimensionId -> ArdaMapsClient.CONFIG.getLocations(dimensionId, null),
                types -> MarkersManager.get().markerTypeKey(types),
                LocationClient::isRevealed,
                LocationClient::getName);
    }

    /**
     * Creates a search with injected suppliers for unit testing.
     *
     * @param locationsByDimension Supplier that returns locations for a given dimension id.
     * @param typeKeyResolver      Converts a location's type list to its canonical marker-type key.
     * @param revealedPredicate    Returns {@code true} when a location is visible to the player.
     * @param nameExtractor        Extracts the display name used for substring matching.
     */
    MapLocationSearch(Function<String, List<LocationClient>> locationsByDimension,
                      Function<List<String>, String> typeKeyResolver,
                      java.util.function.Predicate<LocationClient> revealedPredicate,
                      Function<LocationClient, String> nameExtractor) {

        this.locationsByDimension = locationsByDimension;
        this.typeKeyResolver = typeKeyResolver;
        this.revealedPredicate = revealedPredicate;
        this.nameExtractor = nameExtractor;
    }

    /**
     * Returns locations in the given dimension whose name contains {@code query} (case-insensitive),
     * are revealed, and belong to an enabled marker type.
     *
     * @param dimensionId        The dimension to search in, or {@code null} for an empty result.
     * @param enabledMarkerTypes The set of enabled marker-type keys, or {@code null} when all are enabled.
     * @param query              The case-insensitive name substring to match against.
     * @return The matching locations, in encounter order.
     */
    public List<LocationClient> search(@Nullable String dimensionId, @Nullable Set<String> enabledMarkerTypes, String query) {

        if (dimensionId == null) return List.of();

        String lowerQuery = query.toLowerCase();

        return locationsByDimension.apply(dimensionId).stream()
                .filter(loc -> enabledMarkerTypes == null
                        || enabledMarkerTypes.contains(typeKeyResolver.apply(loc.getTypes())))
                .filter(revealedPredicate)
                .filter(loc -> nameExtractor.apply(loc).toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }
}
