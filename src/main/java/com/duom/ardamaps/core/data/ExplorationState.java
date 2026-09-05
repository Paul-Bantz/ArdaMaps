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

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Enumeration representing the exploration state of a map area.
 * Each state has an associated byte value and a colour for rendering purposes.
 */
@Getter
public enum ExplorationState {

    /** Fully hidden state with transparent colour (alpha = 0). */
    HIDDEN(0, 0x00000000),

    /** Partially visible state with semi-transparent colour (alpha = 0.5). */
    VISIBLE(1, 0x33000000),

    /** Fully revealed state with opaque colour (alpha = 1.0). */
    REVEALED(2, 0xFF000000);

    /** the byte value of this state */
    private final byte value;

    /** The colour associated with this state in ARGB format */
    private final int color;

    /**
     * Constructs an ExplorationState with the specified byte value and colour.
     *
     * @param value the byte value representing the exploration state
     * @param color the colour associated with this state in ARGB format
     */
    ExplorationState(int value, int color) {
        this.value = (byte) value;
        this.color = color;
    }

    /**
     * Converts a byte value to the corresponding ExplorationState.
     * If the value does not match any state, it defaults to HIDDEN.
     *
     * @param value the byte value to convert
     * @return the corresponding ExplorationState
     */
    public static @NotNull ExplorationState fromValue(byte value) {
        for (ExplorationState state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        return HIDDEN;
    }

}