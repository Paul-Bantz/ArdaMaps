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

import com.duom.ardamaps.core.data.json.MarkerTypeTypeAdapter;
import com.duom.ardamaps.core.data.json.MarkersDefinitionTypeAdapter;
import com.duom.ardamaps.core.data.json.SpriteTypeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.duom.ardamaps.gui.ModConstants.modId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for marker fallback resolution.
 */
class MarkersDefinitionTest {

    /**
     * Verifies that a missing marker type key falls back to the default marker type.
     */
    @Test
    void getMarkerType_missingLandmarkKey_usesDefaultType() {

        MarkerType defaultType = new MarkerType("Default", "ardamaps:default", 1, 2);
        MarkerType unknownType = new MarkerType("Unknown", "ardamaps:unknown", 3, 4);
        MarkersDefinition definition = new MarkersDefinition(
                modId("marker"),
                modId("marker_visited"),
                35,
                30,
                0,
                0,
                unknownType,
                defaultType,
                Map.of()
        );

        assertSame(defaultType, definition.getMarkerType("LANDMARK"));
        assertSame(defaultType, definition.getMarkerType(null));
    }

    /**
     * Verifies that bare icon paths in marker type JSON are qualified with the mod namespace.
     */
    @Test
    void fromJson_bareTypeIconPaths_areQualified() {

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Identifier.class, new SpriteTypeAdapter())
                .registerTypeAdapter(MarkerType.class, new MarkerTypeTypeAdapter())
                .registerTypeAdapter(MarkersDefinition.class, new MarkersDefinitionTypeAdapter())
                .create();

        String json = """
                {
                  "marker_background": "icons/map_marker",
                  "marker_background_visited": "icons/map_marker_visited",
                  "map_marker_background_size": 35,
                  "map_marker_icon_size": 30,
                  "map_marker_icon_x_offset": 0,
                  "map_marker_icon_y_offset": 0,
                  "default_type": {
                    "name": "Landmark",
                    "icon": "icons/icon_landmark",
                    "color": "#ffffff",
                    "highlight_color": "#000000"
                  },
                  "unknown_type": {
                    "name": "Unknown",
                    "icon": "icons/icon_unknown",
                    "color": "#ffffff",
                    "highlight_color": "#000000"
                  },
                  "types": {
                    "TOWN": {
                      "name": "Town",
                      "icon": "icons/icon_town",
                      "color": "#ffffff",
                      "highlight_color": "#000000"
                    }
                  }
                }
                """;

        MarkersDefinition definition = gson.fromJson(json, MarkersDefinition.class);

        assertEquals("ardamaps:icons/icon_town", definition.getMarkerType("TOWN").icon());
        assertEquals("ardamaps:icons/icon_unknown", definition.unknownType().icon());
        assertEquals("ardamaps:icons/icon_landmark", definition.defaultType().icon());
    }
}
