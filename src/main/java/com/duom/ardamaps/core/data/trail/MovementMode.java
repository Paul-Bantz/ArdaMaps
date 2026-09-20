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

package com.duom.ardamaps.core.data.trail;

import lombok.Getter;

/**
 * Movement modes tracked by client-side trail recording.
 */
public enum MovementMode {

    /** Movement on foot, mounts, vehicles or other ground-like travel. */
    WALK(0, "movement.mode.walk"),

    /** Movement while in or under water. */
    SWIM(1, "movement.mode.swim"),

    /** Movement while flying, fall-flying or spectating. */
    FLY(2, "movement.mode.fly");

    /** Stable serialized mode identifier. */
    @Getter
    private final byte id;

    /** Translation key for this mode's display name. */
    @Getter
    private final String displayNameKey;

    /**
     * Constructs a movement mode.
     *
     * @param id             Stable serialized identifier.
     * @param translationKey Translation key for display.
     */
    MovementMode(int id, String translationKey) {

        this.id = (byte) id;
        this.displayNameKey = translationKey;
    }

    /**
     * Resolves a movement mode from its serialized identifier.
     *
     * @param id Serialized identifier.
     * @return Matching movement mode, or {@link #WALK} for unknown values.
     */
    public static MovementMode fromId(byte id) {

        for (MovementMode mode : values()) {
            if (mode.id == id) return mode;
        }

        return WALK;
    }
}
