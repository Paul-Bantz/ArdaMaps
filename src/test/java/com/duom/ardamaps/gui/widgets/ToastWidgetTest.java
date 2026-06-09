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

package com.duom.ardamaps.gui.widgets;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ToastWidget}. Ensure opacity values are consistent through and through, and validate lifecycle.
 */
class ToastWidgetTest {

    /**
     * A brand-new toast must be alive immediately after creation. If this returns false, no toasts ever appear on screen.
     */
    @Test
    void isAlive_freshlyCreated_returnsTrue() {

        ToastWidget widget = new ToastWidget(text(), null);
        assertTrue(widget.isAlive(), "A freshly created toast must be alive");
    }

    /**
     * Use a mocked Text to avoid triggering Minecraft's text serialization stack in tests.
     */
    private static Text text() {
        return mock(Text.class);
    }

    /**
     * The queue-removal predicate relies on isAlive() returning false once TOTAL_MS has elapsed.
     * If this is wrong, dead toasts accumulate in the queue indefinitely.
     */
    @Test
    void isAlive_afterTotalLifetime_returnsFalse() {

        long expiredStart = System.currentTimeMillis() - ToastWidget.TOTAL_MS - 1;

        ToastWidget widget = new ToastWidget(text(), null, expiredStart);

        assertFalse(widget.isAlive(), "A toast past its total lifetime must not be alive");
    }

    /**
     * One millisecond before expiry the toast must still be alive - it must not expire early.
     */
    @Test
    void isAlive_justBeforeTotalLifetime_returnsTrue() {

        long almostExpiredStart = System.currentTimeMillis() - ToastWidget.TOTAL_MS + 50;

        ToastWidget widget = new ToastWidget(text(), null, almostExpiredStart);
        assertTrue(widget.isAlive(), "A toast just before its deadline must still be alive");
    }

    /**
     * At elapsed=0 the fade-in formula returns 0/FADE_IN_MS = 0.
     * A non-zero value here causes the toast to pop in instead of fading.
     * Validate that the toast's opacity is low at start time
     *
     * @throws Exception if the reflective call to getAlpha() fails
     */
    @Test
    void getAlpha_atVeryStart_returnsNearZero() throws Exception {

        long now = System.currentTimeMillis();
        ToastWidget widget = new ToastWidget(text(), null, now);

        float alpha = getAlpha(widget);

        // Allow a small epsilon for the handful of ms between construction and assertion.
        assertTrue(alpha >= 0f && alpha < 0.1f,
                "Alpha at the very start of fade-in must be near 0, was: " + alpha);
    }

    /**
     * Helper: invoke the private getAlpha() method via reflection.
     *
     * @param widget the ToastWidget instance to test
     * @return the alpha value (0-255) returned by getAlpha()
     * @throws Exception if the reflective call to getAlpha() fails
     */
    private static float getAlpha(ToastWidget widget) throws Exception {

        Method getAlphaHandle = ToastWidget.class.getDeclaredMethod("getAlpha");
        getAlphaHandle.setAccessible(true);

        return (float) getAlphaHandle.invoke(widget);
    }

    /**
     * Validate that the toast's opacity is bounded strictly between 0 and 1 during its show period
     *
     * @throws Exception if the reflective call to getAlpha() fails
     */
    @Test
    void getAlpha_midFadeIn_returnsInterpolatedValue() throws Exception {

        Text text = mock(Text.class);

        /*
         *  Mockito instrumentation on the first mock() call in the JVM can take hundreds of ms.
         *  Pre-create the mock BEFORE capturing the start time so that instrumentation delay
         *  does not inflate the measured elapsed time and push us past the 500ms fade-in window.
         */
        long start = System.currentTimeMillis() - 100; // 100ms elapsed from now
        ToastWidget widget = new ToastWidget(text, null, start);

        float alpha = getAlpha(widget);

        // 100ms elapsed -> alpha = 0.2; plus test overhead (should be << 400ms) still < 1.0.
        assertTrue(alpha > 0f && alpha < 1.0f,
                "Alpha during fade-in must be strictly between 0 and 1, was: " + alpha);
    }

    /**
     * During the fully-visible display phase, alpha must be exactly 1.0.
     * Any other value causes the text to appear semi-transparent when it should be opaque.
     *
     * @throws Exception if the reflective call to getAlpha() fails
     */
    @Test
    void getAlpha_duringDisplayPhase_returnsOne() throws Exception {

        long midDisplayStart = System.currentTimeMillis()
                - ToastWidget.FADE_IN_MS
                - ToastWidget.DISPLAY_MS / 2;

        ToastWidget widget = new ToastWidget(text(), null, midDisplayStart);
        assertEquals(1.0f, getAlpha(widget), 0.01f,
                "Alpha during display phase must be 1.0");
    }

    /**
     * At FADE_IN + DISPLAY + FADE_OUT/2, the fade-out formula should return approximately 0.5.
     * Tests the fade-out branch of getAlpha().
     *
     * @throws Exception if the reflective call to getAlpha() fails
     */
    @Test
    void getAlpha_midFadeOut_returnsInterpolatedValue() throws Exception {

        long midFadeOutStart = System.currentTimeMillis()
                - ToastWidget.FADE_IN_MS
                - ToastWidget.DISPLAY_MS
                - ToastWidget.FADE_OUT_MS / 2;

        ToastWidget widget = new ToastWidget(text(), null, midFadeOutStart);

        float alpha = getAlpha(widget);

        assertTrue(alpha > 0.3f && alpha < 0.7f,
                "Alpha at mid fade-out must be ~0.5, was: " + alpha);
    }
}
