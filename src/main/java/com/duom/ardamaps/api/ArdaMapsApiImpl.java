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

package com.duom.ardamaps.api;

import com.duom.ardamaps.api.locations.ILocationsApi;
import com.duom.ardamaps.api.locations.LocationsApiImpl;
import com.duom.ardamaps.api.waypoints.IWaypointsApi;
import com.duom.ardamaps.api.waypoints.WaypointsApiImpl;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Public API for the ArdaMaps mod.
 *
 * <p>Other mods interact with ArdaMaps through the static methods on this class.</p>
 */
@SuppressWarnings("unused")
public final class ArdaMapsApiImpl implements ArdaMapsApi {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdaMapsApiImpl.class);

    /** API Instance */
    @Getter
    private static ArdaMapsApiImpl instance;

    /** Waypoints API Handle */
    private final IWaypointsApi waypointsApi = new WaypointsApiImpl();

    /** Locations API Handle */
    private final ILocationsApi locationsApi = new LocationsApiImpl();

    private ArdaMapsApiImpl() { /* Not instantiable */ }

    /**
     * Initializes the API
     */
    public static void initialize() {

        if (instance == null)
            instance = new ArdaMapsApiImpl();
    }

    /**
     * @return an instance of the Waypoints API
     */
    @Override
    public IWaypointsApi getWaypointsApi() {

        return waypointsApi;
    }

    /**
     * @return an instance of the Locations API
     */
    @Override
    public ILocationsApi getLocationsApi() {

        return locationsApi;
    }
}


