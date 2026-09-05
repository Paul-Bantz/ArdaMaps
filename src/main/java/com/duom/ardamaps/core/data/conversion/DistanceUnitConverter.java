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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Utility class for converting distances between in-game blocks and real-world units.
 */
public class DistanceUnitConverter {

    /** Conversion factor from kilometers to miles. */
    public static final float KM_TO_MILES = 0.621371f;

    /** Kilometer threshold below which metric distances are displayed as meters. */
    public static final double METRIC_UNIT_SWITCH_THRESHOLD = 1d;

    /** Mile threshold below which imperial distances are displayed as feet. */
    public static final double IMPERIAL_UNIT_SWITCH_THRESHOLD = 0.5d;

    /** Conversion factor from kilometers to meters. */
    public static final double KM_TO_METERS = 1000d;

    /** Conversion factor from miles to feet. */
    public static final double MILES_TO_FEET = 5280d;

    /**
     * Converts in-game blocks to a localized component in real-world units (kilometers or miles)
     * based on the configured unit system.
     *
     * @param dimension The dimension definition to use for the conversion.
     * @param nbBlocks  Distance in blocks.
     * @return A component representing the distance in the selected real-world units.
     */
    public static @NotNull Component asRealWorldUnits(Dimension dimension, double nbBlocks) {

        if (dimension == null) return Component.empty();

        double distance = blocksToRealWorldUnits(dimension, nbBlocks);
        UnitSystem unitSystem = ArdaMapsClient.CONFIG.getUnitSystem();
        String formattedDistance;
        String unitKey;

        if (unitSystem == UnitSystem.IMPERIAL) {

            if (distance < IMPERIAL_UNIT_SWITCH_THRESHOLD) {
                formattedDistance = formatDistance("%.1f", distance * MILES_TO_FEET);
                unitKey = unitSystem.getDisplayNameKey() + ".subunit";
            } else {
                formattedDistance = formatDistance("%.1f", distance);
                unitKey = unitSystem.getDisplayNameKey() + ".unit";
            }

        } else if (distance < METRIC_UNIT_SWITCH_THRESHOLD) {

            formattedDistance = formatDistance("%.0f", distance * KM_TO_METERS);
            unitKey = unitSystem.getDisplayNameKey() + ".subunit";

        } else {

            formattedDistance = formatDistance("%.0f", distance);
            unitKey = unitSystem.getDisplayNameKey() + ".unit";
        }

        return Component.literal(formattedDistance)
                .append(" ")
                .append(Component.translatable(unitKey));
    }

    /**
     * Formats a numeric distance using the current Minecraft language locale when available.
     *
     * @param pattern The {@link String#format} pattern.
     * @param value   The numeric value to format.
     * @return The formatted number.
     */
    private static String formatDistance(String pattern, double value) {

        return String.format(selectedLocale(), pattern, value);
    }

    /**
     * Resolves the current Minecraft language as a {@link Locale}, falling back for tests and early startup.
     *
     * @return The selected locale, or {@link Locale#ROOT} when unavailable.
     */
    private static Locale selectedLocale() {

        try {
            var minecraft = Minecraft.getInstance();
            String selected = minecraft.getLanguageManager().getSelected();
            if (!selected.isBlank())
                return Locale.forLanguageTag(selected.replace('_', '-'));
        } catch (LinkageError | RuntimeException ignored) {
            return Locale.ROOT;
        }

        return Locale.ROOT;
    }

    /**
     * Converts in-game blocks to kilometers based on Ardacraft scale.
     *
     * @param dimension The dimension definition to use for the conversion.
     * @param blocks    Distance in blocks.
     * @return Distance expressed in the currently selected real-world unit system.
     */
    public static double blocksToRealWorldUnits(Dimension dimension, double blocks) {

        if (dimension == null) return 0d;

        if (ArdaMapsClient.CONFIG.getUnitSystem() == UnitSystem.IMPERIAL)
            return (blocks / dimension.getScale() / 1000.0) * KM_TO_MILES;

        return blocks / dimension.getScale() / 1000.0;
    }

    /**
     * Converts a distance in miles to the corresponding distance in in-game blocks based on Ardacraft scale.
     *
     * @param dimension The dimension definition whose scale is used for conversion.
     * @param miles     Distance in miles.
     * @return Distance in blocks.
     */
    public static float milesToBlocks(Dimension dimension, float miles) {

        if (dimension == null) return 0f;

        return kmToBlocks(dimension, miles / KM_TO_MILES);
    }

    /**
     * Converts a distance in kilometers to the corresponding distance in in-game blocks based on Ardacraft scale.
     *
     * @param dimension The dimension definition to use for the conversion.
     * @param km        Distance in kilometers.
     * @return Distance in blocks.
     */
    public static float kmToBlocks(Dimension dimension, float km) {

        if (dimension == null) return 0f;

        return km * 1000.0f * dimension.getScale();
    }
}
