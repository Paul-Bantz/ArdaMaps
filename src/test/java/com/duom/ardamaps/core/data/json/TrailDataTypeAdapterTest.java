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

import com.duom.ardamaps.core.data.trail.MovementMode;
import com.duom.ardamaps.core.data.trail.PlayerTrail;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for movement-trail JSON serialization.
 */
class TrailDataTypeAdapterTest {

    /** Runtime type for trail maps. */
    private static final Type TRAIL_MAP_TYPE = new TypeToken<Map<String, PlayerTrail>>() {
    }.getType();

    /** Gson instance using the movement-trail adapter. */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(TRAIL_MAP_TYPE, new TrailDataTypeAdapter())
            .create();

    /**
     * Verifies trail maps serialize as base64 strings and deserialize back to geometry.
     */
    @Test
    void serializeDeserialize_trailMap_roundTrips() {

        PlayerTrail trail = new PlayerTrail();
        trail.record(1, 2, MovementMode.WALK);
        trail.record(3, 4, MovementMode.WALK);
        trail.breakSegment();

        String json = GSON.toJson(Map.of("test:dimension", trail), TRAIL_MAP_TYPE);
        Map<String, PlayerTrail> decoded = GSON.fromJson(json, TRAIL_MAP_TYPE);

        assertTrue(json.startsWith("\""));
        assertEquals(2, decoded.get("test:dimension").pointCount());
    }

    /**
     * Verifies invalid base64 fails with a parse exception.
     */
    @Test
    void deserialize_invalidPayload_throws() {

        assertThrows(JsonParseException.class, () -> GSON.fromJson("\"not-base64\"", TRAIL_MAP_TYPE));
    }
}
