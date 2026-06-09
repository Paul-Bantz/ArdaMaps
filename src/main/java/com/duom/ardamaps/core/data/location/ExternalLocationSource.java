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

package com.duom.ardamaps.core.data.location;

import com.duom.ardamaps.api.ArdaMapsApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Bridge class between the ArdaMaps API and the ArdaMaps engine. Its responsibility is to query the Locations API,
 * check for a registered location source, and if one is present, fetch the location data and pass it to the engine.
 */
public class ExternalLocationSource {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalLocationSource.class);

    /**
     * Fetches a list of locations from an external source and returns a completable future
     * @return a CompletableFuture that will complete with a list of LocationServer objects, or complete exceptionally if no source is registered
     */
    public static CompletableFuture<List<LocationServer>> fetchLocations() {

        var apiInstance = ArdaMapsApi.getInstance();

        var locationSource = apiInstance.getLocationsApi().getLocationSource();

        if (locationSource.isEmpty()) {

            LOGGER.info("No LocationSource registered, skipping external location fetch");
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No Location Source registered"));
        }

        var locationSupplier = locationSource.get();

        return locationSupplier.get();
    }
}
