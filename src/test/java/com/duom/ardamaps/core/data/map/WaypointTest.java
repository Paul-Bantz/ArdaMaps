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

package com.duom.ardamaps.core.data.map;

import com.duom.ardamaps.gui.ModConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests waypoint JSON normalization.
 */
class WaypointTest {

    @Test
    void fromJson_bareIconPath_isQualifiedWithModNamespace() {

        var waypoint = Waypoint.fromJson(jsonWithIcon("icons/custom_waypoint"));

        assertTrue(waypoint.isPresent());
        assertEquals("ardamaps:icons/custom_waypoint", waypoint.get().icon());
    }

    @Test
    void fromJson_qualifiedIconPath_isUnchanged() {

        var waypoint = Waypoint.fromJson(jsonWithIcon("minecraft:target"));

        assertTrue(waypoint.isPresent());
        assertEquals("minecraft:target", waypoint.get().icon());
    }

    @Test
    void fromJson_missingIcon_usesDefaultWaypointIcon() {

        String json = """
                {
                  "x": 10,
                  "z": 20,
                  "text": "Target",
                  "r": 1.0,
                  "g": 0.5,
                  "b": 0.0,
                  "identifier": "test",
                  "dimension": "minecraft:overworld"
                }
                """;

        var waypoint = Waypoint.fromJson(json);

        assertTrue(waypoint.isPresent());
        assertEquals(ModConstants.ICON_WAYPOINT.toString(), waypoint.get().icon());
    }

    private String jsonWithIcon(String icon) {

        return """
                {
                  "x": 10,
                  "z": 20,
                  "text": "Target",
                  "r": 1.0,
                  "g": 0.5,
                  "b": 0.0,
                  "identifier": "test",
                  "dimension": "minecraft:overworld",
                  "icon": "%s"
                }
                """.formatted(icon);
    }
}
