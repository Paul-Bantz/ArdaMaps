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

package com.duom.ardamaps.core.data.config.client;

import com.duom.ardamaps.core.data.config.Dimension;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests client configuration defaults.
 */
class ClientConfigTest {

    /**
     * Verifies stale persisted coarse pyramid bootstrap values are ignored and not written back.
     */
    @Test
    void coarsePyramidBootstrap_isNotPartOfClientConfigJson() {

        Gson gson = new Gson();

        ClientConfig config = assertDoesNotThrow(() -> gson.fromJson("{\"coarse_pyramid_bootstrap\":false}", ClientConfig.class));

        assertFalse(gson.toJson(config).contains("coarse_pyramid_bootstrap"));
    }

    /**
     * Verifies region overlays are enabled by default.
     */
    @Test
    void showRegionBorders_defaultsToTrue() {

        ClientConfig config = new ClientConfig();

        assertTrue(config.isShowRegionBorders());
    }

    /**
     * Stores and reads selected map layers independently for each dimension.
     */
    @Test
    void lastMapLayer_multipleDimensions_returnsEachLayer() {

        ClientConfig config = new ClientConfig();
        Dimension overworld = dimension("Overworld", "minecraft:overworld");
        Dimension nether = dimension("Nether", "minecraft:the_nether");

        config.setLastMapLayer(overworld, "terrain");
        config.setLastMapLayer(nether, "caves");

        assertEquals("terrain", config.getLastMapLayer(overworld));
        assertEquals("caves", config.getLastMapLayer(nether));
    }

    /**
     * Overwriting one dimension's selected layer leaves other dimensions untouched.
     */
    @Test
    void lastMapLayer_overwriteOneDimension_preservesOtherDimension() {

        ClientConfig config = new ClientConfig();
        Dimension overworld = dimension("Overworld", "minecraft:overworld");
        Dimension nether = dimension("Nether", "minecraft:the_nether");

        config.setLastMapLayer(overworld, "terrain");
        config.setLastMapLayer(nether, "caves");
        config.setLastMapLayer(overworld, "political");

        assertEquals("political", config.getLastMapLayer(overworld));
        assertEquals("caves", config.getLastMapLayer(nether));
    }

    /**
     * Missing map layer memory returns null for null, unknown, and omitted persisted values.
     */
    @Test
    void getLastMapLayer_missingMemory_returnsNull() {

        Gson gson = new Gson();
        ClientConfig config = gson.fromJson("{}", ClientConfig.class);

        assertNull(config.getLastMapLayer(null));
        assertNull(config.getLastMapLayer(dimension("Unknown", "minecraft:end")));
        assertNull(config.getLastMapLayer(dimension("Overworld", "minecraft:overworld")));
    }

    /**
     * Invalid map layer writes are ignored.
     */
    @Test
    void setLastMapLayer_invalidArguments_doesNothing() {

        ClientConfig config = new ClientConfig();
        Dimension overworld = dimension("Overworld", "minecraft:overworld");
        Dimension blankId = dimension("Blank", " ");

        config.setLastMapLayer(null, "terrain");
        config.setLastMapLayer(overworld, null);
        config.setLastMapLayer(overworld, " ");
        config.setLastMapLayer(blankId, "terrain");

        assertNull(config.getLastMapLayer(overworld));
        assertNull(config.getLastMapLayer(blankId));
    }

    /**
     * Creates a compact dimension fixture for config tests.
     *
     * @param name The dimension display name.
     * @param id   The dimension ID.
     * @return A dimension using neutral bounds and scale.
     */
    private Dimension dimension(String name, String id) {

        return new Dimension(name, id, 1f, 0, 100, 0, 100, false);
    }
}
