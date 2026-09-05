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

package com.duom.ardamaps.gui.screens.rendering;

import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.widgets.ExpansionAnimation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Animated label drawn above the map frame for the region currently under the mouse cursor.
 * Public width and height values are expressed in screen pixels.
 */
@Environment(EnvType.CLIENT)
public class AnimatedRegionLabel {

    /** Fade-in and fade-out duration in milliseconds. */
    private static final long FADE_MS = 100L;

    /** Width and height interpolation duration in milliseconds. */
    private static final long ANIM_MS = 100L;

    /** Horizontal padding added around the region text. */
    private static final int LABEL_PADDING_X = 32;

    /** Vertical padding added around the region text. */
    private static final int LABEL_PADDING_Y = 24;

    /** Opacity multiplier applied to the secondary "In <region>" line. */
    private static final float SECONDARY_TEXT_ALPHA = .65f;

    /** Current visibility animation phase. */
    private Phase phase = Phase.HIDDEN;

    /** Current label opacity from zero to one. */
    private float alpha = 0f;

    /** Start time for the current phase in epoch milliseconds. */
    private long phaseStartMs = 0L;

    /** Latest region content requested by the map overlay renderer. */
    @Nullable
    private Content pendingContent;

    /** Region content currently drawn inside the label. */
    @Nullable
    private Content displayedContent;

    /** Animated label width in pixels. */
    private final AnimatedValue width = new AnimatedValue();

    /** Animated label height in pixels. */
    private final AnimatedValue labelHeight = new AnimatedValue();

    /**
     * Creates an animated region label.
     */
    public AnimatedRegionLabel() {

    }

    /**
     * Stores the latest region names requested by the caller.
     *
     * @param parentName The parent region name to display, or {@code null} to hide the label.
     * @param subName    The subregion name to display above the parent region, or {@code null}.
     */
    public void setRegion(@Nullable String parentName, @Nullable String subName) {

        pendingContent = parentName == null ? null : new Content(parentName, subName);
    }

    /**
     * Updates animation state and draws the label when visible.
     *
     * @param context The draw context.
     * @param font    The font renderer.
     * @param centerX The horizontal centre of the label.
     * @param y       The top y-coordinate of the label.
     */
    public void render(GuiGraphicsExtractor context, Font font, int centerX, int y) {

        long nowMs = System.currentTimeMillis();

        updatePhase(nowMs);
        updateDisplayedContent(font, nowMs);
        updateAnimations(nowMs);
        draw(context, font, centerX, y);
    }

    /**
     * Computes the single-line label base height for a font.
     *
     * @param font The font renderer.
     * @return The single-line label height in pixels.
     */
    public int height(Font font) {

        return font.lineHeight + LABEL_PADDING_Y;
    }

    /**
     * Updates the fade phase from elapsed time.
     *
     * @param nowMs The current epoch time in milliseconds.
     */
    private void updatePhase(long nowMs) {

        if (phase == Phase.FADING_IN) {
            alpha = Math.min(1f, (nowMs - phaseStartMs) / (float) FADE_MS);
            if (alpha >= 1f) phase = Phase.VISIBLE;
            return;
        }

        if (phase == Phase.FADING_OUT) {
            alpha = Math.max(0f, 1f - (nowMs - phaseStartMs) / (float) FADE_MS);
            if (alpha <= 0f) {
                phase = Phase.HIDDEN;
                displayedContent = null;
            }
            return;
        }

        alpha = phase == Phase.VISIBLE ? 1f : 0f;
    }

    /**
     * Reacts to the latest requested content and starts fade or size animations as needed.
     *
     * @param font  The font renderer used to measure label size.
     * @param nowMs The current epoch time in milliseconds.
     */
    private void updateDisplayedContent(Font font, long nowMs) {

        if (Objects.equals(pendingContent, displayedContent)) {
            if (displayedContent != null) updateTargetSize(font, displayedContent, nowMs);
            return;
        }

        if (pendingContent == null) {
            if (phase == Phase.VISIBLE || phase == Phase.FADING_IN) startFadeOut(nowMs);
            return;
        }

        displayedContent = pendingContent;
        updateTargetSize(font, displayedContent, nowMs);

        if (phase == Phase.HIDDEN || phase == Phase.FADING_OUT) startFadeIn(nowMs);
    }

    /**
     * Animates toward the target size for the currently displayed content.
     *
     * @param font    The font renderer used to measure label size.
     * @param content The displayed region content.
     * @param nowMs   The current epoch time in milliseconds.
     */
    private void updateTargetSize(Font font, Content content, long nowMs) {

        List<Component> lines = lines(content);
        width.animateTo(targetWidth(font, lines), nowMs);
        labelHeight.animateTo(targetHeight(font, lines), nowMs);
    }

    /**
     * Advances all active size animations.
     *
     * @param nowMs The current epoch time in milliseconds.
     */
    private void updateAnimations(long nowMs) {

        width.update(nowMs);
        labelHeight.update(nowMs);
    }

    /**
     * Draws the label background and text.
     *
     * @param context The draw context.
     * @param font    The font renderer.
     * @param centerX The horizontal centre of the label.
     * @param y       The top y-coordinate of the label.
     */
    private void draw(GuiGraphicsExtractor context, Font font, int centerX, int y) {

        if (alpha <= 0f || displayedContent == null) return;

        List<Component> lines = lines(displayedContent);
        int textBlockHeight = lines.size() * font.lineHeight;
        int h = Math.round(labelHeight.value());
        int w = Math.round(width.value());
        w += w & 1;
        int argb = GuiTextures.withAlpha(ModConstants.COLOR_WHITE, alpha);
        int primaryColor = GuiTextures.withAlpha(ModConstants.COLOR_DARK_BROWN, alpha);
        int secondaryColor = GuiTextures.withAlpha(ModConstants.COLOR_DARK_BROWN, alpha * SECONDARY_TEXT_ALPHA);

        context.pose().pushMatrix();
        context.pose().translate(centerX, y);
        try {
            GuiTextures.blitScrollBackground(context, -w / 2, 0, w, h, argb);

            int textTop = (h - textBlockHeight) / 2;
            for (int i = 0; i < lines.size(); i++) {
                Component line = lines.get(i);
                int color = i == 0 ? primaryColor : secondaryColor;
                context.text(font, line, -font.width(line) / 2, textTop + i * font.lineHeight, color, false);
            }
        } finally {
            context.pose().popMatrix();
        }
    }

    /**
     * Builds the rendered text lines for region content.
     *
     * @param content The region content to draw.
     * @return The rendered label lines.
     */
    private static List<Component> lines(Content content) {

        if (content.subName() == null) return List.of(Component.literal(content.parentName()));

        return List.of(
                Component.literal(content.subName()),
                Component.translatable("ardamaps.client.map.screen.region.in", content.parentName())
                        .withStyle(ChatFormatting.ITALIC)
        );
    }

    /**
     * Computes the full label width for rendered lines.
     *
     * @param font  The font renderer used to measure text width.
     * @param lines The rendered text lines.
     * @return The full label width in pixels.
     */
    private static int targetWidth(Font font, List<Component> lines) {

        int maxLineWidth = lines.stream()
                .mapToInt(font::width)
                .max()
                .orElse(0);
        return maxLineWidth + LABEL_PADDING_X;
    }

    /**
     * Computes the full label height for rendered lines.
     *
     * @param font  The font renderer.
     * @param lines The rendered text lines.
     * @return The full label height in pixels.
     */
    private static int targetHeight(Font font, List<Component> lines) {

        return lines.size() * font.lineHeight + LABEL_PADDING_Y;
    }

    /**
     * Starts fading the label in from its current alpha.
     *
     * @param nowMs The current epoch time in milliseconds.
     */
    private void startFadeIn(long nowMs) {

        phase = Phase.FADING_IN;
        phaseStartMs = nowMs - Math.round(alpha * FADE_MS);
    }

    /**
     * Starts fading the label out from its current alpha.
     *
     * @param nowMs The current epoch time in milliseconds.
     */
    private void startFadeOut(long nowMs) {

        phase = Phase.FADING_OUT;
        phaseStartMs = nowMs - Math.round((1f - alpha) * FADE_MS);
    }

    /**
     * Region label content.
     *
     * @param parentName The top-level region name.
     * @param subName    The subregion name, or {@code null}.
     */
    private record Content(String parentName, @Nullable String subName) {

    }

    /**
     * Smoothly animates one dimension value.
     */
    private static final class AnimatedValue {

        /** Sentinel value meaning the animation has not started. */
        private static final long NO_ANIMATION = Long.MIN_VALUE;

        /** Current interpolated value. */
        private float current = 0f;

        /** Value at the start of the current animation. */
        private float from = 0f;

        /** Value targeted by the current animation. */
        private float target = 0f;

        /** Start time for the current animation in epoch milliseconds. */
        private long startMs = NO_ANIMATION;

        /**
         * Starts or snaps the animation to a new target.
         *
         * @param newTarget The new target value.
         * @param nowMs     The current epoch time in milliseconds.
         */
        private void animateTo(float newTarget, long nowMs) {

            if (Math.round(target) == Math.round(newTarget)) return;

            from = current;
            target = newTarget;

            if (current <= 0f) {
                current = target;
                startMs = NO_ANIMATION;
                return;
            }

            startMs = nowMs;
        }

        /**
         * Advances the active animation.
         *
         * @param nowMs The current epoch time in milliseconds.
         */
        private void update(long nowMs) {

            if (startMs == NO_ANIMATION) {
                current = target;
                return;
            }

            float t = Math.min(1f, (nowMs - startMs) / (float) ANIM_MS);
            float eased = ExpansionAnimation.smoothstep(t);
            current = from + (target - from) * eased;

            if (t >= 1f) {
                current = target;
                startMs = NO_ANIMATION;
            }
        }

        /**
         * Returns the current animated value.
         *
         * @return The current value.
         */
        private float value() {

            return current;
        }
    }

    /**
     * Visibility animation states.
     */
    private enum Phase {

        /** Label is not visible and has no displayed text. */
        HIDDEN,

        /** Label is fading toward full opacity. */
        FADING_IN,

        /** Label is fully visible. */
        VISIBLE,

        /** Label is fading toward zero opacity. */
        FADING_OUT
    }
}
