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
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;

/**
 * Custom JsonDeserializer for Minecraft Identifier objects.
 * This allows us to deserialize JSON strings into Sprite Identifier instances with the mod's namespace.
 */
public class SpriteTypeAdapter implements JsonDeserializer<Identifier> {

    /**
     * Deserialize a JSON element into an Identifier.
     *
     * @param json    The JSON element to deserialize
     * @param typeOfT The type of the target object (Identifier)
     * @param context The JSON deserialization context
     * @return An Identifier instance with the mod's namespace and the path from the JSON string
     * @throws JsonParseException If the JSON is not a valid string
     */
    @Override
    public Identifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        return new Identifier(ArdaMaps.MOD_ID, json.getAsString().trim());
    }

}