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
import com.duom.ardamaps.api.locations.ILocationsApi;
import com.duom.ardamaps.core.data.location.LocationServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Internal implementation of the locations API.
 */
@SuppressWarnings("unused")
public class LocationsApiImpl implements ILocationsApi {

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationsApiImpl.class);

    /** The source for the location data. */
    private @Nullable Supplier<CompletableFuture<List<ApiLocation>>> locationSource;

    @Override
    public void setLocationSource(Supplier<CompletableFuture<List<ApiLocation>>> source) {
        if (source == null) throw new IllegalArgumentException("LocationSource must not be null");

        if (locationSource != null) {
            LOGGER.warn(
                    "[ArdaMapsApi] LocationSource overridden: {} -> {}",
                    locationSource.getClass().getName(),
                    source.getClass().getName()
            );
        }

        LOGGER.info("[ArdaMapsApi] LocationSource registered: {}", source.getClass().getName());
        locationSource = source;
    }

    @Override
    public Optional<Supplier<CompletableFuture<List<ApiLocation>>>> getLocationSource() {
        return Optional.ofNullable(locationSource);
    }

    /**
     * Converts a public location DTO into the internal model.
     *
     * @param location the public location
     * @return the internal location
     */
    public static LocationServer toLocationServer(ApiLocation location) {
        LocationServer server = new LocationServer();
        server.setId(location.id());
        server.setName(location.name());
        server.setWorld(location.world());
        server.setTypes(location.types());
        server.setWarp(location.warp());
        server.setPosition(location.position());
        server.setPathfinder(location.pathfinder());
        server.setStatus(location.status());
        server.setRegions(location.regions());
        server.setCanon(location.canon());
        server.setDescription(location.description());
        server.setExternalUrl(location.externalUrl());
        return server;
    }
}
