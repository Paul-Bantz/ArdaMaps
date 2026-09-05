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

package com.duom.ardamaps.gui;

import com.duom.ardamaps.core.Client;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for rendering utility arithmetic.
 */
class RenderingUtilsTest {

    /**
     * Verifies GUI scale one snaps to whole GUI pixels.
     */
    @Test
    void toDevicePixel_scaleOneSnapsToWholePixels() {

        try (MockedStatic<Client> client = Mockito.mockStatic(Client.class)) {
            client.when(Client::getGuiScale).thenReturn(1);

            assertEquals(10f, RenderingUtils.toDevicePixel(10.3f));
            assertEquals(11f, RenderingUtils.toDevicePixel(10.5f));
        }
    }

    /**
     * Verifies GUI scale four snaps to quarter GUI pixels.
     */
    @Test
    void toDevicePixel_scaleFourSnapsToQuarterPixels() {

        try (MockedStatic<Client> client = Mockito.mockStatic(Client.class)) {
            client.when(Client::getGuiScale).thenReturn(4);

            assertEquals(10.25f, RenderingUtils.toDevicePixel(10.3f));
            assertEquals(10.5f, RenderingUtils.toDevicePixel(10.4f));
        }
    }

    /**
     * Verifies snapping preserves monotonic ordering across a coordinate sweep.
     */
    @Test
    void toDevicePixel_isMonotonic() {

        try (MockedStatic<Client> client = Mockito.mockStatic(Client.class)) {
            client.when(Client::getGuiScale).thenReturn(4);

            float previous = RenderingUtils.toDevicePixel(-20f);
            for (int i = -199; i <= 200; i++) {
                float snapped = RenderingUtils.toDevicePixel(i / 10f);
                assertTrue(snapped >= previous);
                previous = snapped;
            }
        }
    }
}
