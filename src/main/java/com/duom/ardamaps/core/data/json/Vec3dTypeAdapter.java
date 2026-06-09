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
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Type;

/**
 * A Gson TypeAdapter for Vec3d.
 */
public class Vec3dTypeAdapter implements JsonSerializer<Vec3d>, JsonDeserializer<Vec3d> {

    /**
     * Serialize a Vec3d to JSON.
     *
     * @param src       The Vec3d to serialize
     * @param typeOfSrc The type of the source object
     * @param context   The JSON serialization context
     * @return The serialized JSON element
     * @throws JsonParseException If the object cannot be serialized
     */
    @Override
    public JsonElement serialize(Vec3d src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", src.x);
        obj.addProperty("y", src.y);
        obj.addProperty("z", src.z);
        return obj;
    }

    /**
     * Deserialize a Vec3d from JSON.
     *
     * @param json    The JSON element to deserialize
     * @param typeOfT The type of the target object
     * @param context The JSON deserialization context
     * @return The deserialized Vec3d
     * @throws JsonParseException If the JSON is not in the expected format
     */
    @Override
    public Vec3d deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        double x = obj.get("x").getAsDouble();
        double y = obj.get("y").getAsDouble();
        double z = obj.get("z").getAsDouble();
        return new Vec3d(x, y, z);
    }
}