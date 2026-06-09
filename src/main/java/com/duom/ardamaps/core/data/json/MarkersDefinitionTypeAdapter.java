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

package com.duom.ardamaps.core.data.json;

import com.duom.ardamaps.core.data.map.markers.MarkerType;
import com.duom.ardamaps.core.data.map.markers.MarkersDefinition;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Custom TypeAdapter for deserializing MarkersDefinition from JSON.
 */
public class MarkersDefinitionTypeAdapter implements JsonDeserializer<MarkersDefinition> {

    /**
     * Deserialize JSON data to MarkersDefinition object
     *
     * @param json    The Json data being deserialized
     * @param typeOfT The type of the Object to deserialize to
     * @param context The context for deserialization
     * @return The deserialized MarkersDefinition object
     * @throws JsonParseException if there is an error during deserialization
     */
    @Override
    public MarkersDefinition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        Identifier markerBackground = context.deserialize(obj.get("marker_background"), Identifier.class);
        Identifier markerVisitedBackground = context.deserialize(obj.get("marker_background_visited"), Identifier.class);
        int mapMarkerBackgroundSize = obj.get("map_marker_background_size").getAsInt();
        int mapMarkerIconSize = obj.get("map_marker_icon_size").getAsInt();
        float mapMarkerIconXOffset = obj.get("map_marker_icon_x_offset").getAsFloat();
        float mapMarkerIconYOffset = obj.get("map_marker_icon_y_offset").getAsFloat();

        MarkerType defaultType = context.deserialize(obj.get("default_type"), MarkerType.class);
        MarkerType unknownType = context.deserialize(obj.get("unknown_type"), MarkerType.class);
        Map<String, MarkerType> types = context.deserialize(obj.get("types"), new TypeToken<Map<String, MarkerType>>() {
        }.getType());

        // Add default type to the types map if it has a name
        if (defaultType != null) {
            types.put(defaultType.name().toUpperCase(), defaultType);
        }

        return new MarkersDefinition(markerBackground, markerVisitedBackground, mapMarkerBackgroundSize, mapMarkerIconSize,
                mapMarkerIconXOffset, mapMarkerIconYOffset, unknownType, defaultType, types);
    }
}