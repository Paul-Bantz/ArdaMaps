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

import com.duom.ardamaps.core.data.Vec3d;
import com.duom.ardamaps.core.data.config.ConfigManager;
import com.duom.ardamaps.core.data.config.Dimension;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for JSON adapter error hardening.
 */
class TypeAdapterHardeningTest {

    /**
     * Verifies that invalid base64 data throws a JSON parse exception.
     */
    @Test
    void byteArray_invalidBase64_throwsJsonParseException() {

        assertThrows(JsonParseException.class, () -> ConfigManager.gson().fromJson("\"not base64\"", byte[].class));
    }

    /**
     * Verifies that valid base64 data can be serialized and deserialized without loss.
     */
    @Test
    void byteArray_validBase64_roundTrips() {

        byte[] bytes = {1, 2, 3, 4};
        String json = ConfigManager.gson().toJson(bytes, byte[].class);

        assertArrayEquals(bytes, ConfigManager.gson().fromJson(json, byte[].class));
    }

    /**
     * Verifies that deserializing a Dimension with a missing required field throws a parse exception.
     */
    @Test
    void dimension_missingRequiredField_throwsJsonParseException() {

        String json = """
                {
                  "name": "Overworld",
                  "scale_factor": 1,
                  "x_min": 0,
                  "x_max": 10,
                  "z_min": 0,
                  "z_max": 10
                }
                """;

        assertThrows(JsonParseException.class, () -> ConfigManager.gson().fromJson(json, Dimension.class));
    }

    /**
     * Verifies that deserializing a Vec3d with a missing required field throws a parse exception.
     */
    @Test
    void vec3d_missingRequiredField_throwsJsonParseException() {

        assertThrows(JsonParseException.class, () -> ConfigManager.gson().fromJson("{\"x\":1,\"z\":2}", Vec3d.class));
    }
}
