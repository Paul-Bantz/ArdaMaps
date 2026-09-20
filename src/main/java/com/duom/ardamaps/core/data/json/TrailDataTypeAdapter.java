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

import com.duom.ardamaps.core.data.trail.PlayerTrail;
import com.duom.ardamaps.core.data.trail.TrailCodec;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serializes movement trail data as compressed base64.
 */
public class TrailDataTypeAdapter
        implements JsonSerializer<Map<String, PlayerTrail>>, JsonDeserializer<Map<String, PlayerTrail>> {

    /**
     * Serializes movement trails to compressed base64.
     *
     * @param src       The trail data.
     * @param typeOfSrc The source type.
     * @param context   The serialization context.
     * @return The resulting JSON element.
     */
    @Override
    public JsonElement serialize(Map<String, PlayerTrail> src, Type typeOfSrc, JsonSerializationContext context) {

        return new JsonPrimitive(Base64.getEncoder().encodeToString(TrailCodec.encode(src)));
    }

    /**
     * Deserializes compressed base64 movement trails.
     *
     * @param json    The JSON data.
     * @param typeOfT The target type.
     * @param context The deserialization context.
     * @return The decoded trail data.
     * @throws JsonParseException when the payload is invalid.
     */
    @Override
    public Map<String, PlayerTrail> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        try {
            return new ConcurrentHashMap<>(TrailCodec.decode(Base64.getDecoder().decode(json.getAsString())));
        } catch (IllegalArgumentException | IOException e) {
            throw new JsonParseException("Invalid movement trail data", e);
        }
    }
}
