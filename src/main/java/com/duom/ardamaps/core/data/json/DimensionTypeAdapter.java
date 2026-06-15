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

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Custom TypeAdapter for deserializing Dimensions from JSON.
 */
public class DimensionTypeAdapter implements JsonDeserializer<Dimension> {

    /**
     * Deserialize Json data to Dimension object
     *
     * @param json    The Json data being deserialized
     * @param typeOfT The type of the Object to deserialize to
     * @param context The context for deserialization
     * @return The deserialized Dimension object
     * @throws JsonParseException if there is an error during deserialization
     */
    @Override
    public Dimension deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();

        String name = obj.get("name").getAsString();
        String id = obj.get("id").getAsString();
        float scaleFactor = obj.get("scale_factor").getAsFloat();
        int xMin = obj.get("x_min").getAsInt();
        int xMax = obj.get("x_max").getAsInt();
        int zMin = obj.get("z_min").getAsInt();
        int zMax = obj.get("z_max").getAsInt();
        boolean ardaRegions = obj.has("arda-regions") && obj.get("arda-regions").getAsBoolean();

        var dimension = new Dimension(name, id, scaleFactor, xMin, xMax, zMin, zMax, ardaRegions);

        if (obj.has("map_layers")) {

            Gson gson = new Gson();

            var mapLayersJson = obj.get("map_layers").getAsJsonArray();
            for (JsonElement layerElement : mapLayersJson) {

                var layer = gson.fromJson(layerElement, MapLayerDefinition.class);
                dimension.getMapLayers().add(layer);
            }
        }

        return dimension;
    }
}
