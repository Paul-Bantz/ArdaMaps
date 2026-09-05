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

import com.duom.ardamaps.core.data.map.markers.MarkerType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests marker icon normalization at the JSON boundary.
 */
class MarkerTypeTypeAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(MarkerType.class, new MarkerTypeTypeAdapter())
            .create();

    @Test
    void bareIconPath_isQualifiedWithModNamespace() {

        MarkerType markerType = gson.fromJson(markerJson("icons/icon_town"), MarkerType.class);

        assertEquals("ardamaps:icons/icon_town", markerType.icon());
    }

    @Test
    void alreadyQualifiedIconPath_isUnchanged() {

        MarkerType markerType = gson.fromJson(markerJson("ardamaps:icons/icon_town"), MarkerType.class);

        assertEquals("ardamaps:icons/icon_town", markerType.icon());
    }

    @Test
    void foreignNamespaceIconPath_isUnchanged() {

        MarkerType markerType = gson.fromJson(markerJson("minecraft:foo"), MarkerType.class);

        assertEquals("minecraft:foo", markerType.icon());
    }

    @Test
    void missingIconField_throwsJsonParseException() {

        String json = """
                {
                  "name": "Town",
                  "color": "#ffffff",
                  "highlight_color": "#000000"
                }
                """;

        assertThrows(JsonParseException.class, () -> gson.fromJson(json, MarkerType.class));
    }

    private String markerJson(String icon) {

        return """
                {
                  "name": "Town",
                  "icon": "%s",
                  "color": "#ffffff",
                  "highlight_color": "#000000"
                }
                """.formatted(icon);
    }
}
