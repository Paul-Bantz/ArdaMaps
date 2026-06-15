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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/**
 * Custom JsonDeserializer for colour strings in hex format (e.g., "#RRGGBB", "0xAARRGGBB", "RRGGBB").
 * This allows us to deserialize JSON strings into integer ARGB colour values.
 */
@SuppressWarnings("unused")
public class ColorIntTypeAdapter implements JsonDeserializer<Integer> {

    /**
     * Deserialize a JSON element into an integer ARGB colour.
     *
     * @param json    The JSON element to deserialize
     * @param typeOfT The type of the target object (Integer)
     * @param context The JSON deserialization context
     * @return The integer representation of the colour in ARGB format
     * @throws JsonParseException If the colour string is not in a valid format
     */
    @Override
    public Integer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        String value = json.getAsString().trim();

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
            throw new JsonParseException("Invalid color format: " + json.getAsString());
        }

        return (int) Long.parseLong(value, 16);
    }
}