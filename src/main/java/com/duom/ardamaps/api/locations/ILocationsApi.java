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

package com.duom.ardamaps.api.locations;

import com.duom.ardamaps.core.data.location.LocationServer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * API Contract for interacting with ArdaMaps locations.
 */
@SuppressWarnings("unused")
public interface ILocationsApi {

    /**
     * Registers a {@link CompletableFuture} that ArdaMaps will use to fetch location data.
     *
     * <p>Only one source is active at runtime. Calling this method a second time (e.g. from a
     * different mod) replaces the previous registration — last caller wins. A warning is logged
     * whenever an existing source is replaced so the conflict is visible in the server log.</p>
     *
     * @param source The location source to register. Must not be {@code null}.
     * @throws IllegalArgumentException if {@code source} is {@code null}.
     */
    void setLocationSource(Supplier<CompletableFuture<List<LocationServer>>> source);

    /**
     * @return an optional location source to fetch location data from, if one has been registered
     */
    Optional<Supplier<CompletableFuture<List<LocationServer>>>> getLocationSource();
}
