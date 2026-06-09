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
import com.google.gson.*;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;

/**
 * Custom JsonDeserializer for MarkerType objects.
 * This allows us to deserialize JSON objects into MarkerType instances, handling the conversion of colour strings and icon identifiers.
 */
public class MarkerTypeTypeAdapter implements JsonDeserializer<MarkerType> {

    /**
     * Deserialize a JSON element into a MarkerType.
     *
     * @param json    The JSON element to deserialize
     * @param typeOfT The type of the target object (MarkerType)
     * @param context The JSON deserialization context
     * @return A MarkerType instance with the name, icon, and colour parsed from the JSON
     * @throws JsonParseException If the JSON is not in the expected format or if colour parsing fails
     */
    @Override
    public MarkerType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();

        String name = obj.get("name").getAsString();
        Identifier icon = context.deserialize(obj.get("icon"), Identifier.class);
        String color = obj.get("color").getAsString();
        String highlightColor = obj.get("highlight_color").getAsString();

        return new MarkerType(name, icon, deserializeHexColor(color), deserializeHexColor(highlightColor));
    }

    /**
     * Deserialize a hex colour string into an integer ARGB colour.
     *
     * @param value The hex color string (e.g., "#RRGGBB", "0xAARRGGBB", "RRGGBB")
     * @return The integer representation of the colour in ARGB format
     * @throws JsonParseException If the colour string is not in a valid format
     */
    private int deserializeHexColor(String value) {

        // Remove prefixes
        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }

        // If RGB, prepend alpha FF
        if (value.length() == 6) {
            value = "FF" + value;
        }

        if (value.length() != 8) {
            throw new JsonParseException("Invalid color format: " + value);
        }

        return (int) Long.parseLong(value, 16);
    }
}