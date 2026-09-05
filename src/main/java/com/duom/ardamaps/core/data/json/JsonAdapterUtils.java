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

import com.duom.ardamaps.ArdaMaps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Utility methods for JSON deserialization and validation.
 */
public final class JsonAdapterUtils {

    private JsonAdapterUtils() {
    }

    /**
     * Asserts that a JSON element is an object and returns it.
     *
     * @param json The JSON element to validate.
     * @param type A descriptive name for error messages.
     * @return The JSON object.
     * @throws JsonParseException if the element is not a JSON object.
     */
    public static JsonObject object(JsonElement json, String type) {

        if (json == null || json.isJsonNull() || !json.isJsonObject())
            throw new JsonParseException(type + " must be a JSON object");

        return json.getAsJsonObject();
    }

    /**
     * Retrieves a required field from a JSON object.
     *
     * @param obj   The JSON object.
     * @param field The field name.
     * @return The JSON element at the specified field.
     * @throws JsonParseException if the field is missing or null.
     */
    public static JsonElement required(JsonObject obj, String field) {

        JsonElement value = obj.get(field);
        if (value == null || value.isJsonNull())
            throw new JsonParseException("Missing required field '" + field + "'");

        return value;
    }

    /**
     * Qualifies a bare resource path with the mod namespace; already-qualified values pass through.
     *
     * @param value Raw resource path.
     * @return A qualified resource identifier string.
     */
    public static String qualify(String value) {

        String trimmed = value.trim();
        return trimmed.indexOf(':') >= 0 ? trimmed : ArdaMaps.MOD_ID + ":" + trimmed;
    }
}
