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
import com.duom.ardamaps.core.data.config.DimensionRemap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DimensionTypeAdapter} remap parsing and serialization.
 */
class DimensionTypeAdapterTest {

    /** Gson configured with the dimension adapter under test. */
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Dimension.class, new DimensionTypeAdapter())
            .create();

    /**
     * Structured remap arrays parse regional and regionless entries.
     */
    @Test
    void deserialize_structuredRemaps_parsesRegions() {

        Dimension dimension = gson.fromJson("""
                {
                  "name": "Overworld",
                  "id": "minecraft:overworld",
                  "scale_factor": 1,
                  "x_min": 0,
                  "x_max": 100,
                  "z_min": 0,
                  "z_max": 100,
                  "remaps": [
                    {"id": "multiworld:freebuild", "region": {"x1": 30, "z1": 40, "x2": 10, "z2": 20}},
                    {"id": "multiworld:regionless"}
                  ]
                }
                """, Dimension.class);

        assertEquals(List.of("multiworld:freebuild", "multiworld:regionless"), dimension.getRemappedWorlds());
        assertEquals(new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40), dimension.getRemaps().getFirst());
        assertFalse(dimension.getRemaps().get(1).hasRegion());
    }

    /**
     * Serialization writes the documented structured array shape.
     */
    @Test
    void serialize_remaps_writesStructuredShape() {

        Dimension dimension = new Dimension("Overworld", "minecraft:overworld", 1f, 0, 100, 0, 100, false, false,
                List.of(new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40)));

        String json = gson.toJson(dimension, Dimension.class);

        assertTrue(json.contains("\"remaps\""));
        assertTrue(json.contains("\"region\""));
        assertEquals(dimension.getRemaps(), gson.fromJson(json, Dimension.class).getRemaps());
    }
}
