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

package com.duom.ardamaps.core.api;

import com.duom.ardamaps.api.ArdaMapsApi;
import com.duom.ardamaps.api.locations.ILocationsApi;
import com.duom.ardamaps.api.waypoints.IWaypointsApi;
import com.duom.ardamaps.core.api.locations.LocationsApiImpl;
import com.duom.ardamaps.core.api.waypoints.WaypointsApiImpl;
import lombok.Getter;

/**
 * Internal implementation of the ArdaMaps public API.
 */
@SuppressWarnings("unused")
public final class ArdaMapsApiImpl implements ArdaMapsApi {

    /** API instance. */
    @Getter
    private static ArdaMapsApiImpl instance;

    /** Waypoints API handle. */
    private final IWaypointsApi waypointsApi = new WaypointsApiImpl();

    /** Locations API handle. */
    private final ILocationsApi locationsApi = new LocationsApiImpl();

    private ArdaMapsApiImpl() { /* Not instantiable */ }

    /**
     * Initializes the API.
     */
    public static void initialize() {
        if (instance == null)
            instance = new ArdaMapsApiImpl();
    }

    @Override
    public IWaypointsApi getWaypointsApi() {
        return waypointsApi;
    }

    @Override
    public ILocationsApi getLocationsApi() {
        return locationsApi;
    }
}
