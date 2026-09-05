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

/**
 * Smooth toggle animation for folding and unfolding GUI controls.
 */
public final class ExpansionAnimation {

    /** Animation duration in milliseconds. */
    private final long durationMs;

    /** Current animated expansion amount from folded to unfolded. */
    private float expansion;

    /** Wall-clock time when the current expansion transition started. */
    private long expansionStartMs;

    /** Target expansion state for the current transition. */
    private boolean expandedTarget;

    /**
     * Creates a new expansion animation.
     *
     * @param durationMs Animation duration in milliseconds.
     */
    public ExpansionAnimation(long durationMs) {

        this.durationMs = durationMs;
    }

    /**
     * Updates the smooth expansion amount toward the requested state.
     *
     * @param expand Whether the control should expand.
     * @return The updated expansion amount.
     */
    public float update(boolean expand) {

        long now = System.currentTimeMillis();
        if (expandedTarget != expand) {
            expansion = currentExpansion(now);
            expandedTarget = expand;
            expansionStartMs = now;
        }

        expansion = currentExpansion(now);
        return expansion;
    }

    /**
     * Immediately sets the animation to the given state without a transition.
     *
     * @param expanded Whether the control should be fully expanded.
     */
    public void snap(boolean expanded) {

        expandedTarget = expanded;
        expansion = expanded ? 1f : 0f;
        expansionStartMs = System.currentTimeMillis() - durationMs;
    }

    /**
     * Returns the current expansion amount.
     *
     * @return The current expansion amount.
     */
    public float value() {

        return expansion;
    }

    /**
     * Returns whether the animation is targeting the expanded state.
     *
     * @return Whether the animation is expanding.
     */
    public boolean isExpanding() {

        return expandedTarget;
    }

    /**
     * Applies smoothstep easing to a normalised progress value.
     *
     * @param t The normalised progress value.
     * @return The eased progress value.
     */
    public static float smoothstep(float t) {

        float clamped = Math.min(1f, Math.max(0f, t));

        return clamped * clamped * (3f - 2f * clamped);
    }

    /**
     * Computes the eased expansion value for the current animation target.
     *
     * @param now The current wall-clock time in milliseconds.
     * @return The eased expansion amount.
     */
    private float currentExpansion(long now) {

        float t = durationMs <= 0L ? 1f : (now - expansionStartMs) / (float) durationMs;
        float eased = smoothstep(t);

        return expandedTarget ? eased : 1f - eased;
    }
}
