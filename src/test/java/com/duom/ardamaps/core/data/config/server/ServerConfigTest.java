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

package com.duom.ardamaps.core.data.config.server;

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.DimensionRemap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for server-side remap resolution helpers.
 */
class ServerConfigTest {

    /**
     * Location worlds remapped to an owner are exposed with the owner dimension ID.
     */
    @Test
    void resolveDimensionId_remappedWorld_returnsOwnerDimension() {

        ServerConfig config = config();

        assertEquals("minecraft:overworld", config.resolveDimensionId("multiworld:freebuild"));
        assertEquals("minecraft:the_nether", config.resolveDimensionId("minecraft:the_nether"));
        assertEquals("unknown:world", config.resolveDimensionId("unknown:world"));
    }

    /**
     * Teleports inside regional remap bounds target the remapped world.
     */
    @Test
    void resolveTeleportWorld_insideRegion_returnsRemappedWorld() {

        ServerConfig config = config();

        assertEquals("multiworld:freebuild", config.resolveTeleportWorld("minecraft:overworld", 20, 30));
        assertEquals("minecraft:overworld", config.resolveTeleportWorld("minecraft:overworld", 9, 30));
        assertEquals("minecraft:overworld", config.resolveTeleportWorld("minecraft:overworld", 60, 60));
    }

    /**
     * Regionless remaps do not redirect teleports.
     */
    @Test
    void resolveTeleportWorld_regionlessRemap_keepsRequestedWorld() {

        ServerConfig config = config();

        assertEquals("minecraft:overworld", config.resolveTeleportWorld("minecraft:overworld", 60, 60));
    }

    /**
     * Creates a server config with regional and regionless remaps.
     *
     * @return The configured server config.
     */
    private static ServerConfig config() {

        ServerConfig config = new ServerConfig();
        config.setDimensions(List.of(
                new Dimension("Overworld", "minecraft:overworld", 1f, 0, 100, 0, 100, false, false,
                        List.of(
                                new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40),
                                new DimensionRemap("multiworld:regionless"))),
                new Dimension("Nether", "minecraft:the_nether", 1f, 0, 100, 0, 100, false)));
        return config;
    }
}
