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

package com.duom.ardamaps.core.data.map.markers;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.data.json.MarkerTypeTypeAdapter;
import com.duom.ardamaps.core.data.json.MarkersDefinitionTypeAdapter;
import com.duom.ardamaps.core.data.json.SpriteTypeAdapter;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.location.LocationServer;
import com.duom.ardamaps.gui.ModConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Defines the configuration for map markers, including visual properties and available marker types.
 * This record is loaded from the `markers.json` resource file.
 *
 * @param markerBackground        the identifier for the marker background texture
 * @param mapMarkerBackgroundSize the size of the marker background on the map in pixels
 * @param mapMarkerIconSize       the size of the marker icon in pixels
 * @param mapMarkerIconXOffset    the horizontal offset of the marker icon
 * @param mapMarkerIconYOffset    the vertical offset of the marker icon
 * @param defaultType             the name of the default marker type
 * @param types                   a map of marker type names to their corresponding {@link MarkerType} definitions
 */
public record MarkersDefinition(@SerializedName("marker_background") Identifier markerBackground,
                                @SerializedName("marker_background_visited") Identifier markerBackgroundVisited,
                                @SerializedName("map_marker_background_size") int mapMarkerBackgroundSize,
                                @SerializedName("map_marker_icon_size") int mapMarkerIconSize,
                                @SerializedName("map_marker_icon_x_offset") float mapMarkerIconXOffset,
                                @SerializedName("map_marker_icon_y_offset") float mapMarkerIconYOffset,
                                @SerializedName("unknown_type") MarkerType unknownType,
                                @SerializedName("default_type") MarkerType defaultType,
                                Map<String, MarkerType> types) {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(MarkersDefinition.class);

    /** Marker label for undiscovered locations */
    private static final Identifier MARKERS_JSON = new Identifier(ArdaMaps.MOD_ID, "markers.json");

    /**
     * Loads the markers definition from the `markers.json` resource file.
     *
     * @return the loaded {@link MarkersDefinition}
     * @throws RuntimeException if the resource is missing or cannot be parsed
     */
    public static @NotNull MarkersDefinition loadMarkersDefinition() {

        MarkersDefinition markersDefinition = createDefault();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Identifier.class, new SpriteTypeAdapter())
                .registerTypeAdapter(MarkerType.class, new MarkerTypeTypeAdapter())
                .registerTypeAdapter(MarkersDefinition.class, new MarkersDefinitionTypeAdapter())
                .create();

        ResourceManager manager = MinecraftClient.getInstance().getResourceManager();

        Optional<Resource> resource = manager.getResource(MARKERS_JSON);

        if (resource.isEmpty()) throw new RuntimeException("Missing resource: " + MARKERS_JSON);

        try (InputStreamReader reader = new InputStreamReader(
                resource.get().getInputStream(),
                StandardCharsets.UTF_8
        )) {

            markersDefinition = gson.fromJson(reader, MarkersDefinition.class);

        } catch (Exception e) {

            LOGGER.error("Error loading markers definition from {} - Mod may be unstable", MARKERS_JSON, e);
        }

        return markersDefinition;
    }

    /**
     * Creates a default markers definition with a single "Landmark" marker type.
     *
     * @return the default {@link MarkersDefinition}
     */
    public static MarkersDefinition createDefault() {

        var defaultMarker = new MarkerType("Landmark", ModConstants.LANDMARK_ICON, ModConstants.COLOR_BROWN, ModConstants.COLOR_LIGHT_BROWN);
        var unknown = new MarkerType("Unknown", ModConstants.UNKNOWN_ICON, ModConstants.COLOR_BROWN, ModConstants.COLOR_LIGHT_BROWN);
        return new MarkersDefinition(ModConstants.MAP_MARKER_ICON,
                ModConstants.MAP_MARKER_VISITED_ICON,
                35,
                30,
                7.5f,
                2.375f,
                unknown,
                defaultMarker,
                Map.of("LANDMARK", defaultMarker)
        );
    }

    /**
     * Binds marker icons and colours to the provided list of locations based on their types.
     * If a location's type is not found, the default marker type is used.
     *
     * @param locations the list of {@link LocationServer} objects to bind markers to
     */
    public void bindMarkers(List<LocationClient> locations) {

        for (LocationClient location : locations) {

            MarkerType markerType = getMarkerType(location.getTypes().isEmpty() ?
                    null :
                    location.getTypes().get(0).toUpperCase());

            location.setIcon(markerType.icon());
            location.setColor(markerType.color());
            location.setHighlightColor(markerType.highlightColor());
        }
    }

    /**
     * Gets the marker type by its name.
     * If the type is not found or the name is null/blank, returns the default marker type.
     *
     * @param type the name of the marker type
     * @return the matched {@link MarkerType} or the default if not found
     */
    public @NotNull MarkerType getMarkerType(String type) {

        if (type == null || type.isBlank()) return types.get("LANDMARK");

        return types.getOrDefault(type, types.get("LANDMARK"));
    }
}