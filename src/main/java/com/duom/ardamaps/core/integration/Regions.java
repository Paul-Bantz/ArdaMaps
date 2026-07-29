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

package com.duom.ardamaps.core.integration;

import com.duom.ardamaps.core.data.map.RegionLookupTexture;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Facade for optional region providers.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Regions {

    /** The active region provider, or null when no region integration is available. */
    private static volatile @Nullable RegionProvider provider;

    /**
     * Registers a region provider.
     *
     * @param regionProvider the provider to route region lookup generation through
     */
    public static void register(RegionProvider regionProvider) {
        provider = regionProvider;
    }

    /**
     * @return true when a region provider has registered itself
     */
    public static boolean isAvailable() {
        return provider != null;
    }

    /**
     * Generates the region lookup texture if a provider is available.
     *
     * @param dimensionId the dimension id
     * @param callback    callback receiving the generated texture
     */
    public static void generateRegionLookup(String dimensionId, Consumer<RegionLookupTexture> callback) {
        RegionProvider regionProvider = provider;
        if (regionProvider != null) regionProvider.generateRegionLookup(dimensionId, callback);
    }
}
