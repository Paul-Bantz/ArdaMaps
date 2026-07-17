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
import com.duom.ardamaps.core.data.config.client.ClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link DistanceUnitConverter}.
 */
class DistanceUnitConverterTest {

    private static final Dimension DIMENSION =
            new Dimension("Test", "test:distance", 1f, 0, 10000, 0, 10000, false);

    /** Holds the real {@link ClientConfig} so it can be restored after each test. */
    private ClientConfig previousConfig;

    /**
     * Saves the current {@link ArdaMapsClient#CONFIG} before each test so it can be restored.
     */
    @BeforeEach
    void setUp() {
        previousConfig = ArdaMapsClient.CONFIG;
    }

    /**
     * Restores {@link ArdaMapsClient#CONFIG} to its original value after each test.
     */
    @AfterEach
    void tearDown() {
        ArdaMapsClient.CONFIG = previousConfig;
    }

    /**
     * Metric distances below the kilometer threshold are displayed in meters.
     */
    @Test
    void asRealWorldUnits_metricBelowThreshold_returnsMeters() {
        setUnitSystem(UnitSystem.METRIC);

        assertEquals("2 km", DistanceUnitConverter.asRealWorldUnits(DIMENSION, 1839));
    }

    /**
     * Metric distances at the threshold stay in kilometers.
     */
    @Test
    void asRealWorldUnits_metricAtThreshold_returnsKilometers() {
        setUnitSystem(UnitSystem.METRIC);

        assertEquals("2 km", DistanceUnitConverter.asRealWorldUnits(DIMENSION, 2000));
    }

    /**
     * Imperial distances below the mile threshold are displayed in feet.
     */
    @Test
    void asRealWorldUnits_imperialBelowThreshold_returnsFeet() {
        setUnitSystem(UnitSystem.IMPERIAL);

        double blocks = DistanceUnitConverter.milesToBlocks(DIMENSION, 0.345f);

        assertEquals("1821.6 feet", DistanceUnitConverter.asRealWorldUnits(DIMENSION, blocks));
    }

    /**
     * Imperial distances at the threshold stay in miles.
     */
    @Test
    void asRealWorldUnits_imperialAtThreshold_returnsMiles() {
        setUnitSystem(UnitSystem.IMPERIAL);

        double blocks = DistanceUnitConverter.milesToBlocks(DIMENSION, 0.5f);

        assertEquals("0.5 miles", DistanceUnitConverter.asRealWorldUnits(DIMENSION, blocks));
    }

    /**
     * Null dimensions keep the existing empty-string behavior.
     */
    @Test
    void asRealWorldUnits_nullDimension_returnsEmptyString() {
        setUnitSystem(UnitSystem.METRIC);

        assertEquals("", DistanceUnitConverter.asRealWorldUnits(null, 1000));
    }

    private static void setUnitSystem(UnitSystem unitSystem) {
        var config = Mockito.mock(ClientConfig.class);

        Mockito.when(config.getUnitSystem()).thenReturn(unitSystem);

        ArdaMapsClient.CONFIG = config;
    }
}
