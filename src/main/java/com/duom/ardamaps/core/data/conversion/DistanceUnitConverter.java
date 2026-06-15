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

package com.duom.ardamaps.core.data.conversion;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.UnitSystem;
import com.duom.ardamaps.core.data.config.Dimension;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for converting distances between in-game blocks and real-world units.
 */
public class DistanceUnitConverter {

    public static final float KM_TO_MILES = 0.621371f;

    /**
     * Converts in-game blocks to a string representation in real-world units (kilometers or miles)
     * based on the configured unit system.
     *
     * @param dimension The dimension definition to use for the conversion.
     * @param nbBlocks Distance in blocks.
     * @return A string representing the distance in the selected real-world units.
     */
    public static @NotNull String asRealWorldUnits(Dimension dimension, double nbBlocks) {

        if(dimension == null) return "";

        return ArdaMapsClient.CONFIG.getUnitSystem() == UnitSystem.IMPERIAL ?
                String.format("%.1f miles", blocksToRealWorldUnits(dimension, nbBlocks)) : String.format("%.0f km", blocksToRealWorldUnits(dimension, nbBlocks));
    }

    /**
     * Converts in-game blocks to kilometers based on Ardacraft scale.
     *
     * @param dimension The dimension definition to use for the conversion.
     * @param blocks Distance in blocks.
     */
    public static double blocksToRealWorldUnits(Dimension dimension, double blocks) {

        if(dimension == null) return 0d;

        if (ArdaMapsClient.CONFIG.getUnitSystem() == UnitSystem.IMPERIAL)
            return (blocks / dimension.getScale() / 1000.0) * KM_TO_MILES;

        return blocks / dimension.getScale() / 1000.0;
    }

    /**
     * Converts a distance in kilometers to the corresponding distance in in-game blocks based on Ardacraft scale.
     *
     * @param dimension The dimension definition to use for the conversion.
     * @param km Distance in kilometers.
     * @return Distance in blocks.
     */
    public static float kmToBlocks(Dimension dimension, float km) {

        if (dimension == null) return 0f;

        return km * 1000.0f * dimension.getScale();
    }

    /**
     * Converts a distance in miles to the corresponding distance in in-game blocks based on Ardacraft scale.
     *
     * @param miles Distance in miles.
     * @return Distance in blocks.
     */
    public static float milesToBlocks(Dimension dimension, float miles) {

        if (dimension == null) return 0f;

        return kmToBlocks(dimension, miles / KM_TO_MILES);
    }
}