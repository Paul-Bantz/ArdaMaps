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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for server dimension resolution.
 */
class ServerConfigTest {

    /**
     * Exact dimension IDs are returned unchanged.
     */
    @Test
    void resolveDimensionId_exactMatchWins() {

        ServerConfig config = config(
                dimension("Overworld", "minecraft:overworld", "multiworld:freebuild"),
                dimension("Freebuild", "multiworld:freebuild", (String[]) null));

        assertEquals("multiworld:freebuild", config.resolveDimensionId("multiworld:freebuild"));
    }

    /**
     * Remapped worlds resolve to the owning dimension ID.
     */
    @Test
    void resolveDimensionId_remappedWorld_returnsOwningDimension() {

        ServerConfig config = config(dimension("Overworld", "minecraft:overworld", "multiworld:freebuild"));

        assertEquals("minecraft:overworld", config.resolveDimensionId("multiworld:freebuild"));
    }

    /**
     * Unknown worlds stay unchanged.
     */
    @Test
    void resolveDimensionId_unknownWorld_returnsOriginalWorld() {

        ServerConfig config = config(dimension("Overworld", "minecraft:overworld", "multiworld:freebuild"));

        assertEquals("multiworld:other", config.resolveDimensionId("multiworld:other"));
    }

    /**
     * Duplicate remaps resolve to the first configured owning dimension.
     */
    @Test
    void resolveDimensionId_duplicateRemap_returnsFirstConfiguredDimension() {

        ServerConfig config = config(
                dimension("Overworld", "minecraft:overworld", "multiworld:freebuild"),
                dimension("Nether", "minecraft:the_nether", "multiworld:freebuild"));

        assertEquals("minecraft:overworld", config.resolveDimensionId("multiworld:freebuild"));
    }

    /**
     * Teleports inside a regional remap resolve to the remapped world.
     */
    @Test
    void resolveTeleportWorld_insideRegionalRemap_returnsRemappedWorld() {

        ServerConfig config = config(dimension("Overworld", "minecraft:overworld",
                List.of(new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40))));

        assertEquals("multiworld:freebuild", config.resolveTeleportWorld("minecraft:overworld", 20, 30));
    }

    /**
     * Teleports outside regional remaps stay in the requested world.
     */
    @Test
    void resolveTeleportWorld_outsideRegionalRemap_returnsOriginalWorld() {

        ServerConfig config = config(dimension("Overworld", "minecraft:overworld",
                List.of(new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40))));

        assertEquals("minecraft:overworld", config.resolveTeleportWorld("minecraft:overworld", 9, 30));
    }

    /**
     * Dimensions without regional remaps keep teleporting to the requested world.
     */
    @Test
    void resolveTeleportWorld_withoutRemaps_returnsOriginalWorld() {

        ServerConfig config = config(new Dimension("Overworld", "minecraft:overworld", 1f,
                0, 10, 0, 10, false));

        assertEquals("minecraft:overworld", config.resolveTeleportWorld("minecraft:overworld", 20, 30));
    }

    /**
     * Unknown requested worlds are not rewritten.
     */
    @Test
    void resolveTeleportWorld_unknownWorld_returnsOriginalWorld() {

        ServerConfig config = config(dimension("Overworld", "minecraft:overworld",
                List.of(new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40))));

        assertEquals("multiworld:other", config.resolveTeleportWorld("multiworld:other", 20, 30));
    }

    /**
     * Teleports requested against a remapped world still use the owner dimension's regional configuration.
     */
    @Test
    void resolveTeleportWorld_remappedWorldRequest_usesOwningDimension() {

        ServerConfig config = config(dimension("Overworld", "minecraft:overworld",
                List.of(new DimensionRemap("multiworld:freebuild", 10, 20, 30, 40))));

        assertEquals("multiworld:freebuild", config.resolveTeleportWorld("multiworld:freebuild", 20, 30));
        assertEquals("multiworld:freebuild", config.resolveTeleportWorld("multiworld:freebuild", 9, 30));
    }

    /**
     * Creates a server config from dimensions.
     *
     * @param dimensions The dimensions to configure.
     * @return The server config.
     */
    private static ServerConfig config(Dimension... dimensions) {

        ServerConfig config = new ServerConfig();
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

    /**
     * Creates a test dimension.
     *
     * @param name    The dimension name.
     * @param id      The dimension ID.
     * @param remaps  The dimension remaps.
     * @return The test dimension.
     */
    @SuppressWarnings("SameParameterValue")
    private static Dimension dimension(String name, String id, List<DimensionRemap> remaps) {

        return new Dimension(name, id, 1f, 0, 10, 0, 10, false, false, remaps);
    }
}
