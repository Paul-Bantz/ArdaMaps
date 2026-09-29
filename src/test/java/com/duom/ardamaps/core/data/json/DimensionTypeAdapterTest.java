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

import com.duom.ardamaps.core.data.config.ConfigManager;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.DimensionRemap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DimensionTypeAdapter} dimension config parsing.
 */
class DimensionTypeAdapterTest {

    /**
     * Regionless structured remaps are read from the server-only dimension field.
     */
    @Test
    void deserialize_withRegionlessStructuredRemaps_preservesRemapIds() {

        Dimension dimension = ConfigManager.gson().fromJson("""
                {
                  "name": "Overworld",
                  "id": "minecraft:overworld",
                  "scale_factor": 1,
                  "x_min": 0,
                  "x_max": 10,
                  "z_min": 0,
                  "z_max": 10,
                  "remaps": [
                    {
                      "id": "multiworld:freebuild"
                    },
                    {
                      "id": "arda:plot"
                    }
                  ]
                }
                """, Dimension.class);

        assertEquals(List.of("multiworld:freebuild", "arda:plot"), dimension.getRemappedWorlds());
        assertTrue(dimension.getRemaps().stream().noneMatch(DimensionRemap::hasRegion));
    }

    /**
     * Structured remap configuration is read from the server-only dimension field.
     */
    @Test
    void deserialize_withStructuredRemaps_preservesRegions() {

        Dimension dimension = ConfigManager.gson().fromJson("""
                {
                  "name": "Overworld",
                  "id": "minecraft:overworld",
                  "scale_factor": 1,
                  "x_min": 0,
                  "x_max": 10,
                  "z_min": 0,
                  "z_max": 10,
                  "remaps": [
                    {
                      "id": "multiworld:freebuild",
                      "region": {
                        "x1": 8397,
                        "z1": 2602,
                        "x2": 10824,
                        "z2": 4535
                      }
                    }
                  ]
                }
                """, Dimension.class);

        DimensionRemap remap = dimension.getRemaps().get(0);

        assertEquals(List.of("multiworld:freebuild"), dimension.getRemappedWorlds());
        assertEquals(new DimensionRemap("multiworld:freebuild", 8397, 2602, 10824, 4535), remap);
        assertTrue(remap.contains(9000, 3000));
    }

    /**
     * Structured remap corners can be supplied in any order.
     */
    @Test
    void deserialize_withReversedRemapCorners_normalizesRegion() {

        Dimension dimension = ConfigManager.gson().fromJson("""
                {
                  "name": "Overworld",
                  "id": "minecraft:overworld",
                  "scale_factor": 1,
                  "x_min": 0,
                  "x_max": 10,
                  "z_min": 0,
                  "z_max": 10,
                  "remaps": [
                    {
                      "id": "multiworld:freebuild",
                      "region": {
                        "x1": 10824,
                        "z1": 4535,
                        "x2": 8397,
                        "z2": 2602
                      }
                    }
                  ]
                }
                """, Dimension.class);

        assertEquals(new DimensionRemap("multiworld:freebuild", 8397, 2602, 10824, 4535),
                dimension.getRemaps().get(0));
    }

    /**
     * Dimensions without remap configuration remain compatible with existing JSON.
     */
    @Test
    void deserialize_withoutRemaps_usesEmptyRemapList() {

        Dimension dimension = ConfigManager.gson().fromJson("""
                {
                  "name": "Overworld",
                  "id": "minecraft:overworld",
                  "scale_factor": 1,
                  "x_min": 0,
                  "x_max": 10,
                  "z_min": 0,
                  "z_max": 10
                }
                """, Dimension.class);

        assertTrue(dimension.getRemappedWorlds().isEmpty());
    }

    /**
     * Structured remaps serialize to the documented nested region shape.
     */
    @Test
    void serialize_withStructuredRemaps_writesRegionObjects() {

        Dimension dimension = new Dimension("Overworld", "minecraft:overworld", 1f,
                0, 10, 0, 10, false, false,
                List.of(new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40)));

        String json = ConfigManager.gson().toJson(dimension, Dimension.class);

        Dimension parsed = ConfigManager.gson().fromJson(json, Dimension.class);

        assertTrue(json.contains("\"region\""));
        assertEquals(new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40),
                parsed.getRemaps().get(0));
    }
}
