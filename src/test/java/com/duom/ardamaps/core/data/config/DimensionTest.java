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

package com.duom.ardamaps.core.data.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Dimension} derived values and identity semantics.
 */
class DimensionTest {

    /**
     * Inclusive coordinate bounds include both endpoints in width and height.
     */
    @Test
    void constructor_inclusiveBounds_derivesWidthAndHeight() {

        Dimension dimension = new Dimension("Test", "test:dimension", 1f, -10, 10, 20, 29, false);

        assertEquals(21, dimension.getWidth());
        assertEquals(10, dimension.getHeight());
    }

    /**
     * A zero scale factor must not create an infinite scale that contaminates distance conversion math.
     */
    @Test
    void constructor_zeroScaleFactor_usesNeutralScale() {

        Dimension dimension = new Dimension("Test", "test:dimension", 0f, 0, 100, 0, 100, false);

        assertEquals(0f, dimension.getScaleFactor());
        assertEquals(1f, dimension.getScale());
        assertTrue(Float.isFinite(dimension.getScale()));
    }

    /**
     * Config refreshes create new Dimension instances for the same dimension ID, so equality must not depend on object identity.
     */
    @Test
    void equalsAndHashCode_useDimensionId() {

        Dimension first = new Dimension("First", "minecraft:overworld", 1f, 0, 100, 0, 100, false);
        Dimension second = new Dimension("Second", "minecraft:overworld", 58f, -100, 100, -100, 100, true);
        Dimension other = new Dimension("Other", "minecraft:the_nether", 1f, 0, 100, 0, 100, false);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, other);
    }
}
