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
import com.duom.ardamaps.core.data.config.DimensionRemap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for client dimension lookup.
 */
class ClientConfigTest {

    /**
     * Exact dimension IDs are preferred over remap ownership.
     */
    @Test
    void getDimension_exactMatchWins() {

        Dimension overworld = dimension("Overworld", "minecraft:overworld", "multiworld:freebuild");
        Dimension freebuild = dimension("Freebuild", "multiworld:freebuild", (String[]) null);
        ClientConfig config = config(overworld, freebuild);

        assertEquals(freebuild, config.getDimension("multiworld:freebuild"));
    }

    /**
     * Remapped world IDs return the owning dimension.
     */
    @Test
    void getDimension_remappedWorld_returnsOwningDimension() {

        Dimension overworld = dimension("Overworld", "minecraft:overworld", "multiworld:freebuild");
        ClientConfig config = config(overworld);

        assertEquals(overworld, config.getDimension("multiworld:freebuild"));
    }

    /**
     * Unknown world IDs return null.
     */
    @Test
    void getDimension_unknownWorld_returnsNull() {

        ClientConfig config = config(dimension("Overworld", "minecraft:overworld", "multiworld:freebuild"));

        assertNull(config.getDimension("multiworld:other"));
    }

    /**
     * Creates a client config from dimensions.
     *
     * @param dimensions The dimensions to configure.
     * @return The client config.
     */
    private static ClientConfig config(Dimension... dimensions) {

        ClientConfig config = new ClientConfig();
        config.setDimensions(List.of(dimensions));
        return config;
    }

    /**
     * Creates a test dimension.
     *
     * @param name   The dimension name.
     * @param id     The dimension ID.
     * @param remaps The regionless remap IDs.
     * @return The test dimension.
     */
    private static Dimension dimension(String name, String id, String... remaps) {

        List<DimensionRemap> dimensionRemaps = remaps == null
                ? null
                : Arrays.stream(remaps)
                .map(DimensionRemap::new)
                .toList();
        return new Dimension(name, id, 1f, 0, 10, 0, 10, false, false, dimensionRemaps);
    }
}
