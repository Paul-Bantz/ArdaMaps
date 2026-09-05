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
import com.duom.ardamaps.core.data.map.markers.MarkersDefinition;
import com.duom.ardamaps.core.data.map.markers.MarkersManager;
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Session-scoped marker type filter state for the map screen.
 */
public class MarkerTypeFilter {

    /** Supplies locations for the currently selected dimension. */
    private final Function<String, List<LocationClient>> locationsByDimension;

    /** Supplies the current marker definition. */
    private final Supplier<MarkersDefinition> markersDefinitionSupplier;

    /** Marker types available in the selected dimension. */
    private List<Entry> available = List.of();

    /** Enabled marker type keys. */
    private Set<String> enabledKeys = new HashSet<>();

    /**
     * Creates a filter backed by the active client configuration.
     */
    public MarkerTypeFilter() {

        this(dimensionId -> ArdaMapsClient.CONFIG.getLocations(dimensionId, null), MarkersManager::get);
    }

    /**
     * Creates a filter with injected data suppliers for tests.
     *
     * @param locationsByDimension      Supplies locations for a dimension id.
     * @param markersDefinitionSupplier Supplies marker definitions.
     */
    MarkerTypeFilter(Function<String, List<LocationClient>> locationsByDimension,
                     Supplier<MarkersDefinition> markersDefinitionSupplier) {

        this.locationsByDimension = locationsByDimension;
        this.markersDefinitionSupplier = markersDefinitionSupplier;
    }

    /**
     * Rebuilds available marker types for the selected dimension.
     *
     * @param dimensionId The selected dimension id, or null when no dimension is selected.
     */
    public void refresh(@Nullable String dimensionId) {

        if (dimensionId == null) {

            available = List.of();
            enabledKeys = new HashSet<>();
            return;
        }

        MarkersDefinition definition = markersDefinitionSupplier.get();
        Set<String> previousEnabled = new HashSet<>(enabledKeys);
        Set<String> previousAvailable = new HashSet<>(available.stream().map(Entry::key).toList());
        Set<String> availableKeys = new HashSet<>();

        available = locationsByDimension.apply(dimensionId).stream()
                .map(location -> definition.markerTypeKey(location.getTypes()))
                .distinct()
                .filter(key -> definition.types().containsKey(key))
                .map(key -> {

                    var type = definition.types().get(key);
                    availableKeys.add(key);
                    return new Entry(key, type.name(), ModConstants.id(type.icon()));
                })
                .sorted(Comparator.comparing(Entry::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        enabledKeys = new HashSet<>();
        for (String key : availableKeys) {

            if (!previousAvailable.contains(key) || previousEnabled.contains(key)) enabledKeys.add(key);
        }
    }

    /**
     * Checks whether a marker type key is enabled.
     *
     * @param key The marker type key.
     * @return True when the key is currently enabled.
     */
    public boolean isEnabled(String key) {

        return enabledKeys.contains(key);
    }

    /**
     * Toggles a marker type key if it is available.
     *
     * @param key The marker type key to toggle.
     */
    public void toggle(String key) {

        if (available.stream().noneMatch(entry -> entry.key().equals(key))) return;

        if (!enabledKeys.remove(key)) enabledKeys.add(key);
    }

    /**
     * Enables every available marker type.
     */
    public void enableAll() {

        enabledKeys = new HashSet<>();
        available.stream().map(Entry::key).forEach(enabledKeys::add);
    }

    /**
     * Disables every available marker type.
     */
    public void disableAll() {

        enabledKeys = new HashSet<>();
    }

    /**
     * Gets the available marker type entries.
     *
     * @return The available marker type entries.
     */
    public List<Entry> available() {

        return available;
    }

    /**
     * Gets the enabled marker type keys.
     *
     * @return A defensive copy of the enabled marker type keys.
     */
    public Set<String> enabledKeys() {

        return Set.copyOf(enabledKeys);
    }

    /**
     * Checks whether all available marker types are enabled.
     *
     * @return True when all available marker types are enabled.
     */
    public boolean isAllEnabled() {

        return enabledKeys.size() == available.size();
    }

    /**
     * Checks whether no marker types are enabled.
     *
     * @return True when no marker type is enabled.
     */
    public boolean isNoneEnabled() {

        return enabledKeys.isEmpty();
    }

    /**
     * Marker type entry listed in the marker filter popup.
     *
     * @param key         The marker type key.
     * @param displayName The marker type display name.
     * @param icon        The marker type icon.
     */
    public record Entry(String key, String displayName, Identifier icon) {

    }
}
