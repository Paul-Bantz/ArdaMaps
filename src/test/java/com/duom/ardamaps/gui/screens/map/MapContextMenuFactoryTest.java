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

package com.duom.ardamaps.gui.screens.map;

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.gui.widgets.ContextMenu;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MapContextMenuFactory#buildEntries} entry composition logic.
 */
class MapContextMenuFactoryTest {

    /**
     * Verifies that the teleport entry is absent when the point is outside the explored area.
     */
    @Test
    void buildEntries_outsideExploredArea_omitsTeleportEntry() {

        MapCamera camera = mockCamera("test:eriador");
        ContextMenu.Entry add = entry("add");
        ContextMenu.Entry teleport = entry("teleport");

        List<ContextMenu.Entry> entries = MapContextMenuFactory.buildEntries(
                camera, add, teleport, null, true, () -> { }, _ -> false);

        assertFalse(entries.contains(teleport), "Teleport should be absent outside explored area");
        assertTrue(entries.contains(add), "Add-waypoint should be present");
    }

    /**
     * Verifies that the teleport entry is present when the point is explored.
     */
    @Test
    void buildEntries_insideExploredArea_includesTeleportEntry() {

        MapCamera camera = mockCamera("test:eriador");
        ContextMenu.Entry add = entry("add");
        ContextMenu.Entry teleport = entry("teleport");

        List<ContextMenu.Entry> entries = MapContextMenuFactory.buildEntries(
                camera, add, teleport, null, false, () -> { }, _ -> false);

        assertTrue(entries.contains(teleport), "Teleport should be present inside explored area");
    }

    /**
     * Verifies that share and remove entries replace the add-waypoint entry when a waypoint is hovered.
     */
    @Test
    void buildEntries_waypointHovered_showsShareAndRemoveEntries() {

        MapCamera camera = mockCamera("test:eriador");
        Waypoint waypoint = new Waypoint(100, 200, "Test Waypoint", 0f, 0f, 1f, "Player", "test:eriador");

        ContextMenu.Entry add = entry("add");
        ContextMenu.Entry teleport = entry("teleport");

        List<ContextMenu.Entry> entries = MapContextMenuFactory.buildEntries(
                camera, add, teleport, waypoint, false, () -> { }, _ -> false);

        assertFalse(entries.contains(add), "Add-waypoint entry should not appear when hovering a waypoint");
        assertEquals(2, entries.stream()
                .filter(e -> !e.equals(teleport))
                .count(), "Exactly share and remove entries expected besides teleport");
    }

    /**
     * Verifies that the clear-waypoint entry is included when a waypoint exists in the dimension.
     */
    @Test
    void buildEntries_noWaypointHovered_waypointExists_showsClearEntry() {

        MapCamera camera = mockCamera("test:eriador");
        ContextMenu.Entry add = entry("add");
        ContextMenu.Entry teleport = entry("teleport");

        // hasWaypoint returns true → clear entry should appear
        List<ContextMenu.Entry> entries = MapContextMenuFactory.buildEntries(
                camera, add, teleport, null, false, () -> { }, _ -> true);

        assertEquals(3, entries.size(), "Expected teleport + clear + add entries");
    }

    /**
     * Verifies that no clear-waypoint entry appears when no waypoint exists.
     */
    @Test
    void buildEntries_noWaypointHovered_noWaypointExists_omitsClearEntry() {

        MapCamera camera = mockCamera("test:eriador");
        ContextMenu.Entry add = entry("add");
        ContextMenu.Entry teleport = entry("teleport");

        List<ContextMenu.Entry> entries = MapContextMenuFactory.buildEntries(
                camera, add, teleport, null, false, () -> { }, _ -> false);

        assertEquals(2, entries.size(), "Expected teleport + add, no clear entry");
    }

    /**
     * Returns a mock camera whose dimension has the given id.
     *
     * @param dimensionId The dimension id for the mocked dimension.
     * @return A mock camera with the configured dimension.
     */
    @SuppressWarnings("SameParameterValue")
    private static MapCamera mockCamera(String dimensionId) {

        MapCamera camera = Mockito.mock(MapCamera.class);
        Dimension dim = Mockito.mock(Dimension.class);
        Mockito.when(dim.getId()).thenReturn(dimensionId);
        Mockito.when(camera.getDimension()).thenReturn(dim);
        return camera;
    }

    /**
     * Creates a minimal menu entry fixture with the given label.
     *
     * @param label The menu-entry display label.
     * @return A {@link ContextMenu.Entry} instance.
     */
    private static ContextMenu.Entry entry(String label) {

        return new ContextMenu.Entry(Component.literal(label), () -> { });
    }
}
