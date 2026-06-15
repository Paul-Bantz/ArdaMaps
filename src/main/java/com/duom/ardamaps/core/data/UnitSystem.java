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

import net.minecraft.text.Text;

/**
 * Enumeration of supported unit systems for measurements.
 */
public enum UnitSystem {

    /** The metric system, using meters and kilometers. */
    METRIC("unit.system.metric"),
    IMPERIAL("unit.system.imperial");

    /** The translation key for the display name of this unit system. */
    private final String displayNameKey;

    /** The cached display name for this unit system, initialized to the translation key. */
    private String displayName;

    /**
     * Constructs a new UnitSystem with the specified translation key for display names and units.
     *
     * @param translationKey The translation key used to retrieve the display name, unit, and subunit for this unit system.
     */
    UnitSystem(String translationKey) {

        this.displayNameKey = translationKey;
        this.displayName = translationKey;
    }

    /**
     * Retrieves the display name for this unit system, using lazy loading to fetch the translated string only when needed.
     *
     * @return The display name for this unit system.
     */
    public String getDisplayName() {

        if (displayName.equals(displayNameKey)) displayName = Text.translatable(displayNameKey).getString();

        return displayName;
    }
}