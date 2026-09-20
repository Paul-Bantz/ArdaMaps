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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for exploration-data JSON serialization.
 */
class ExplorationDataTypeAdapterTest {

    /** Gson instance under test. */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(byte[].class, new ExplorationDataTypeAdapter())
            .create();

    /**
     * Verifies compressed exploration data round-trips through Gson.
     */
    @Test
    void compressedData_roundTripsThroughGson() {

        byte[] states = new byte[2048];
        states[128] = 1;
        states[1024] = 2;

        String json = GSON.toJson(states, byte[].class);

        assertArrayEquals(states, GSON.fromJson(json, byte[].class));
    }

    /**
     * Verifies legacy raw base64 strings still deserialize.
     */
    @Test
    void legacyBase64_deserializes() {

        byte[] states = {0, 1, 2};
        String json = "\"" + Base64.getEncoder().encodeToString(states) + "\"";

        assertArrayEquals(states, GSON.fromJson(json, byte[].class));
    }

    /**
     * Verifies invalid base64 routes through JsonParseException.
     */
    @Test
    void invalidBase64_throwsJsonParseException() {

        assertThrows(JsonParseException.class, () -> GSON.fromJson("\"not base64\"", byte[].class));
    }
}
