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

package com.duom.ardamaps.core;

import com.duom.ardamaps.core.data.location.LocationClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link PendingMapFocus}.
 */
class PendingMapFocusTest {

    /**
     * Clears static state after each test.
     */
    @AfterEach
    void tearDown() {

        PendingMapFocus.clear();
    }

    /**
     * Consuming within the focus window returns the pending location.
     */
    @Test
    void consume_withinWindow_returnsLocation() {

        LocationClient location = mock(LocationClient.class);

        PendingMapFocus.set(location, 1_000L);

        assertSame(location, PendingMapFocus.consume(1_000L + PendingMapFocus.WINDOW_MS));
    }

    /**
     * Consuming after the focus window expires returns {@code null}.
     */
    @Test
    void consume_afterWindow_returnsNull() {

        LocationClient location = mock(LocationClient.class);

        PendingMapFocus.set(location, 1_000L);

        assertNull(PendingMapFocus.consume(1_000L + PendingMapFocus.WINDOW_MS + 1L));
    }

    /**
     * Consuming a pending focus clears it so it only fires once.
     */
    @Test
    void consume_twice_returnsNullSecondTime() {

        LocationClient location = mock(LocationClient.class);

        PendingMapFocus.set(location, 1_000L);

        assertSame(location, PendingMapFocus.consume(2_000L));
        assertNull(PendingMapFocus.consume(2_000L));
    }

    /**
     * Setting another location replaces the earlier pending focus and resets expiry.
     */
    @Test
    void set_secondLocation_replacesFirstAndResetsExpiry() {

        LocationClient first = mock(LocationClient.class);
        LocationClient second = mock(LocationClient.class);

        PendingMapFocus.set(first, 1_000L);
        PendingMapFocus.set(second, 11_000L);

        assertSame(second, PendingMapFocus.consume(11_000L + PendingMapFocus.WINDOW_MS));
    }
}
