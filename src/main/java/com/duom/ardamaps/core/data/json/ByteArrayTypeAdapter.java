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

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.Base64;

/**
 * A simple type adapter to serialize / deserialize byte arrays as B64 Strings
 */
public class ByteArrayTypeAdapter
        implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

    /**
     * Serializes a byte array to a base64-encoded string in JSON.
     * @param src the object that needs to be converted to JSON.
     * @param typeOfSrc the actual type (fully genericized version) of the source object.
     * @param context the serialization context
     * @return the resulting JSON element
     */
    @Override
    public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(Base64.getEncoder().encodeToString(src));
    }

    /**
     * Deserializes a base64-encoded string from JSON back into a byte array.
     * @param json The Json data being deserialized
     * @param typeOfT The type of the Object to deserialize to
     * @param context the serialization context
     * @return the byte array represented by the base64 string in the JSON
     * @throws JsonParseException when a parse error occurs
     */
    @Override
    public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        return Base64.getDecoder().decode(json.getAsString());
    }
}

