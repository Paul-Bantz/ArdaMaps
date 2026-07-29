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

package com.duom.ardamaps.core.api.locations;

import com.duom.ardamaps.api.locations.ApiLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationsApiImplTest {

    @Test
    void mapsApiLocationToServerLocation() {
        Vec3d position = new Vec3d(1, 2, 3);
        ApiLocation api = new ApiLocation("id", "Name", "minecraft:overworld", List.of("town"),
                "warp", position, "path:chapter", "built", List.of("Region"), true,
                "description", "https://example.test");

        var server = LocationsApiImpl.toLocationServer(api);

        assertEquals("id", server.getId());
        assertEquals("Name", server.getName());
        assertEquals("minecraft:overworld", server.getWorld());
        assertEquals(List.of("town"), server.getTypes());
        assertEquals("warp", server.getWarp());
        assertEquals(position, server.getPosition());
        assertEquals("path:chapter", server.getPathfinder());
        assertEquals("built", server.getStatus());
        assertEquals(List.of("Region"), server.getRegions());
        assertTrue(server.isCanon());
        assertEquals("description", server.getDescription());
        assertEquals("https://example.test", server.getExternalUrl());
    }

    @Test
    void storesApiLocationSource() {
        LocationsApiImpl api = new LocationsApiImpl();

        api.setLocationSource(() -> CompletableFuture.completedFuture(List.of()));

        assertTrue(api.getLocationSource().isPresent());
    }
}
