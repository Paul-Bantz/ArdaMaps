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

package com.duom.ardamaps.core.networking.handlers.server;

import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.config.MapLayerSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PlayerRangedTeleportHandler} teleport candidate search order.
 */
class PlayerRangedTeleportHandlerTest {

    /**
     * Verifies that scanUpward returns the first passable level encountered and respects interval boundaries.
     */
    @Test
    void scanUpward_returnsFirstPassableLevelAndIncludesEndpoints() {

        assertEquals(OptionalDouble.of(2.25D), PlayerRangedTeleportHandler.scanUpward(2, 5, y -> y == 2 || y == 5
                ? OptionalDouble.of(y + 0.25D)
                : OptionalDouble.empty()));
        assertEquals(OptionalDouble.of(5.25D), PlayerRangedTeleportHandler.scanUpward(2, 5, y -> y == 5
                ? OptionalDouble.of(y + 0.25D)
                : OptionalDouble.empty()));
    }

    /**
     * Verifies that scanDownward returns the first passable level encountered (high to low) and respects interval boundaries.
     */
    @Test
    void scanDownward_returnsFirstPassableLevelAndIncludesEndpoints() {

        assertEquals(OptionalDouble.of(5.25D), PlayerRangedTeleportHandler.scanDownward(2, 5, y -> y == 2 || y == 5
                ? OptionalDouble.of(y + 0.25D)
                : OptionalDouble.empty()));
        assertEquals(OptionalDouble.of(2.25D), PlayerRangedTeleportHandler.scanDownward(2, 5, y -> y == 2
                ? OptionalDouble.of(y + 0.25D)
                : OptionalDouble.empty()));
    }

    /**
     * Verifies that scanUpward and scanDownward return empty for invalid ranges (minY > maxY) and when no passable level exists.
     */
    @Test
    void helpersReturnEmptyForEmptyOrInvalidIntervals() {

        assertTrue(PlayerRangedTeleportHandler.scanUpward(4, 3, OptionalDouble::of).isEmpty());
        assertTrue(PlayerRangedTeleportHandler.scanDownward(4, 3, OptionalDouble::of).isEmpty());
        assertTrue(PlayerRangedTeleportHandler.scanUpward(4, 4, _ -> OptionalDouble.empty()).isEmpty());
        assertTrue(PlayerRangedTeleportHandler.scanDownward(4, 4, _ -> OptionalDouble.empty()).isEmpty());
    }

    /**
     * Verifies that findTeleportCandidate prioritizes positions within the selected band over those outside it.
     */
    @Test
    void selectedBandScanningWinsOverValidPositionsBelowOrAboveIt() {

        var bounds = new PlayerRangedTeleportHandler.VerticalBounds(0, 10);

        OptionalDouble candidate = PlayerRangedTeleportHandler.findTeleportCandidate(4, 6, bounds,
                resolvedAt(2, 5, 8));

        assertEquals(OptionalDouble.of(5.25D), candidate);
    }

    /**
     * Creates a resolver that returns a fractional standing Y for configured integer feet candidates.
     *
     * @param safeYs Integer feet Y candidates that should resolve successfully.
     * @return A resolver suitable for candidate-search tests.
     */
    private static IntFunction<OptionalDouble> resolvedAt(int... safeYs) {

        return y -> {
            for (int safeY : safeYs) {
                if (safeY == y) return OptionalDouble.of(y + 0.25D);
            }

            return OptionalDouble.empty();
        };
    }

    /**
     * Verifies that findTeleportCandidate prioritizes positions below the selected band over those above it.
     */
    @Test
    void belowBandScanningWinsOverAboveBandMatch() {

        var bounds = new PlayerRangedTeleportHandler.VerticalBounds(0, 10);

        OptionalDouble candidate = PlayerRangedTeleportHandler.findTeleportCandidate(4, 6, bounds,
                resolvedAt(2, 8));

        assertEquals(OptionalDouble.of(2.25D), candidate);
    }

    /**
     * Verifies that findTeleportCandidate only scans above the selected band when in-band and below-band searches fail.
     */
    @Test
    void aboveBandScanningIsUsedOnlyAfterFirstTwoPhasesFail() {

        var bounds = new PlayerRangedTeleportHandler.VerticalBounds(0, 10);

        OptionalDouble candidate = PlayerRangedTeleportHandler.findTeleportCandidate(4, 6, bounds,
                resolvedAt(8));

        assertEquals(OptionalDouble.of(8.25D), candidate);
    }

    /**
     * Verifies that reversed selected bounds and reversed configured range bounds are normalized correctly.
     */
    @Test
    void reversedSelectedBoundsAndConfiguredRangeBoundsAreNormalized() {

        Dimension dimension = dimension(List.of(
                new MapLayerRange(0, "lower.pmtiles", 4, -2),
                new MapLayerRange(1, "upper.pmtiles", 8, 6)
        ));

        PlayerRangedTeleportHandler.VerticalBounds bounds = PlayerRangedTeleportHandler.effectiveOverallBounds(dimension, -64, 320);
        OptionalDouble candidate = PlayerRangedTeleportHandler.findTeleportCandidate(6, 4, bounds,
                resolvedAt(4));

        assertEquals(new PlayerRangedTeleportHandler.VerticalBounds(-2, 8), bounds);
        assertEquals(OptionalDouble.of(4.25D), candidate);
    }

    /**
     * Helper method to create a test Dimension with the specified exploration ranges.
     *
     * @param ranges The MapLayerRange configurations for the dimension.
     * @return A new Dimension instance with the provided ranges.
     */
    private static Dimension dimension(List<MapLayerRange> ranges) {

        Dimension dimension = new Dimension("Test", "test:dimension", 1f, 0, 1000, 0, 1000, false);
        dimension.getMapLayers().add(new MapLayerDefinition("Ranged", MapLayerSource.PMTILES, true, 8, null, 1.0,
                1, 3, 1, 14, 256, 1.0, "fallback.pmtiles", "fallback.png", ranges));
        return dimension;
    }

    /**
     * Verifies that effectiveOverallBounds correctly derives bounds from multiple configured ranges and clamps to build height.
     */
    @Test
    void overallBoundsAreDerivedAcrossMultipleConfiguredRangesAndClampedToBuildHeight() {

        Dimension dimension = dimension(List.of(
                new MapLayerRange(0, "deep.pmtiles", -200, -100),
                new MapLayerRange(1, "high.pmtiles", 200, 400)
        ));

        PlayerRangedTeleportHandler.VerticalBounds bounds = PlayerRangedTeleportHandler.effectiveOverallBounds(dimension, -64, 320);

        assertEquals(new PlayerRangedTeleportHandler.VerticalBounds(-64, 318), bounds);
    }

    /**
     * Verifies that findTeleportCandidate evaluates boundary levels exactly once and covers all three search phases.
     */
    @Test
    void boundaryLevelsAreEvaluatedOnceIncludingSelectedEdgesAndWorldSafeUpperFeetLevel() {

        var bounds = new PlayerRangedTeleportHandler.VerticalBounds(0, 8);
        List<Integer> tested = new ArrayList<>();
        IntFunction<OptionalDouble> noMatch = y -> {
            tested.add(y);
            return OptionalDouble.empty();
        };

        OptionalDouble candidate = PlayerRangedTeleportHandler.findTeleportCandidate(4, 6, bounds, noMatch);

        assertTrue(candidate.isEmpty());
        assertEquals(List.of(4, 5, 6, 3, 2, 1, 0, 7, 8), tested);
        assertEquals(tested.size(), tested.stream().distinct().count());
    }

    /**
     * Verifies that findTeleportCandidate returns empty when: no positions pass the predicate, selection exceeds bounds, or bounds are empty.
     */
    @Test
    void noMatchBehaviorProducesNoTeleportCandidate() {

        var bounds = new PlayerRangedTeleportHandler.VerticalBounds(0, 10);

        assertTrue(PlayerRangedTeleportHandler.findTeleportCandidate(4, 6, bounds, _ -> OptionalDouble.empty()).isEmpty());
        assertTrue(PlayerRangedTeleportHandler.findTeleportCandidate(20, 30, bounds, OptionalDouble::of).isEmpty());
        assertTrue(PlayerRangedTeleportHandler.findTeleportCandidate(4, 6,
                new PlayerRangedTeleportHandler.VerticalBounds(10, 0), OptionalDouble::of).isEmpty());
    }
}
