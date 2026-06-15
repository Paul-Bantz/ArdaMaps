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

package com.duom.ardamaps.core.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exploration State enum validation. This might seem redundant but any changes made to ExplorationState has direct
 * impact on past player progress and current player experience (ie: spoilers, broken rendering etc).
 * These tests ensure consistency across versions.
 */
class ExplorationStateTest {

    /**
     * Validate ordinal conversion from 0
     */
    @Test
    void fromValue_zero_returnsHidden() {
        // Byte 0 is the initial state of every fog cell.
        // A wrong mapping here corrupts the entire fog mask the moment saved data is loaded.
        assertEquals(ExplorationState.HIDDEN, ExplorationState.fromValue((byte) 0));
    }

    /**
     * Validate ordinal conversion from 1
     */
    @Test
    void fromValue_one_returnsVisible() {
        // VISIBLE is an intermediate rendering state used during the HUD overlay pass.
        assertEquals(ExplorationState.VISIBLE, ExplorationState.fromValue((byte) 1));
    }

    /**
     * Validate ordinal conversion from 2
     */
    @Test
    void fromValue_two_returnsRevealed() {
        // REVEALED is the state set by trackExploration() every game tick.
        // If this mapping is wrong, nothing the player visits is ever marked explored.
        assertEquals(ExplorationState.REVEALED, ExplorationState.fromValue((byte) 2));
    }

    /**
     * Validate unknown bytes resolve to hidden
     */
    @Test
    void fromValue_unknownByte_returnsHidden() {
        // A corrupt or future-format saved byte must not crash or display a phantom state.
        // Defaulting to HIDDEN means the cell is simply re-explored next visit - safe.
        assertEquals(ExplorationState.HIDDEN, ExplorationState.fromValue((byte) 99));
        assertEquals(ExplorationState.HIDDEN, ExplorationState.fromValue((byte) -1));
    }

    /**
     * Validate Enum ordering
     */
    @SuppressWarnings("ConstantValue")
    @Test
    void ordinalOrdering_hiddenLessThanRevealed() {
        // LocationProvider uses `state.ordinal() >= ExplorationState.REVEALED.ordinal()`
        // to decide whether a location is visible on the map.
        // If this ordering ever regresses, every location appears unexplored.
        assertTrue(ExplorationState.HIDDEN.ordinal() < ExplorationState.VISIBLE.ordinal());
        assertTrue(ExplorationState.VISIBLE.ordinal() < ExplorationState.REVEALED.ordinal());
    }
}
