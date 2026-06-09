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

import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A HUD toast widget that fades in, displays for a short time, then fades out.
 *
 * <p>Toasts render on the right side of the screen (lower third) and show a semi-transparent
 * black background with an optional 24×24 icon followed by the message text.</p>
 *
 * <p>Lifecycle: fade-in {@value #FADE_IN_MS} ms -> display {@value #DISPLAY_MS} ms
 * -> fade-out {@value #FADE_OUT_MS} ms (total {@value #TOTAL_MS} ms).</p>
 */
public class ToastWidget {

    /** Duration of the fade-in phase in milliseconds. */
    public static final long FADE_IN_MS = 500L;

    /** Duration of the fully-visible display phase in milliseconds. */
    public static final long DISPLAY_MS = 2000L;

    /** Duration of the fade-out phase in milliseconds. */
    public static final long FADE_OUT_MS = 500L;

    /** Total lifetime of the toast in milliseconds. */
    public static final long TOTAL_MS = FADE_IN_MS + DISPLAY_MS + FADE_OUT_MS;

    /** Size of the icon in pixels (square). */
    private static final int ICON_SIZE = 24;

    /** Horizontal/vertical padding inside the background rectangle. */
    private static final int PADDING = 6;

    /** Gap between the icon and the text. */
    private static final int ICON_TEXT_GAP = 6;

    /** Height of the toast background rectangle. */
    private static final int TOAST_HEIGHT = PADDING * 2 + ICON_SIZE;

    /** Message to display. */
    private final Text message;

    /** Optional icon, rendered left of the message. */
    @Nullable
    private final Identifier icon;

    /** Red component of the icon for tinting */
    private final float iconR;

    /** Green component of the icon for tinting */
    private final float iconG;

    /** Blue component of the icon for tinting */
    private final float iconB;

    /** System time (ms) when this toast was created. */
    private final long startTimeMs;

    /**
     * Creates a new toast with an immediate start time.
     *
     * @param message the text to display
     * @param icon    optional icon identifier (nullable)
     */
    public ToastWidget(Text message, @Nullable Identifier icon) {

        this(message, icon, System.currentTimeMillis());
    }

    /**
     * Creates a new toast with an immediate start time.
     *
     * @param message the text to display
     * @param icon    optional icon identifier (nullable)
     * @param r          red component of the icon tint (0.0-1.0)
     * @param g          green component of the icon tint (0.0-1.0)
     * @param b          blue component of the icon tint (0.0-1).
     */
    public ToastWidget(Text message, @Nullable Identifier icon, float r, float g, float b) {

        this(message, icon, System.currentTimeMillis(),  r, g, b);
    }

    /**
     * Creates a new toast with a specified start time.
     *
     * @param message     the text to display
     * @param icon        optional icon identifier (nullable)
     * @param startTimeMs the toast display start time
     */
    public ToastWidget(Text message, @Nullable Identifier icon, long startTimeMs) {
        this(message, icon, startTimeMs, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Creates a new toast with a specified start time.
     *
     * @param message     the text to display
     * @param icon        optional icon identifier (nullable)
     * @param startTimeMs the toast display start time
     * @param r          red component of the icon tint (0.0-1.0)
     * @param g          green component of the icon tint (0.0-1.0)
     * @param b          blue component of the icon tint (0.0-1).
     */
    public ToastWidget(Text message, @Nullable Identifier icon, long startTimeMs, float r, float g, float b) {
        this.message = message;
        this.icon = icon;
        this.startTimeMs = startTimeMs;
        this.iconR = r;
        this.iconG = g;
        this.iconB = b;
    }

    /**
     * Returns {@code true} while the toast is still within its lifetime.
     *
     * @return {@code false} once the toast should be removed from the queue
     */
    public boolean isAlive() {
        return System.currentTimeMillis() - startTimeMs < TOTAL_MS;
    }

    /**
     * Renders this toast to the HUD. Call every frame from a {@code HudRenderCallback}.
     *
     * @param context the draw context provided by the HUD render callback
     */
    public void render(DrawContext context) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;

        float alpha = Math.max(0f, Math.min(1f, getAlpha()));
        if (alpha <= 0.05f) return;

        var textRenderer = mc.textRenderer;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        int textWidth = textRenderer.getWidth(message);
        int toastWidth = PADDING + (icon != null ? ICON_SIZE + ICON_TEXT_GAP : 0) + textWidth + PADDING;

        int x = screenW - toastWidth;
        int y = screenH * 2 / 3;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int contentX = x + PADDING;
        int iconY = y + PADDING;

        if (icon != null) {

            var sprite = IconSpriteAtlas.retrieveSprite(icon);

            RenderSystem.setShaderColor(iconR, iconG, iconB, alpha);

            if (sprite != null)
                context.drawSprite(contentX, iconY, 0, ICON_SIZE, ICON_SIZE, IconSpriteAtlas.retrieveSprite(icon));
            else
                context.drawTexture(icon, contentX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            contentX += ICON_SIZE + ICON_TEXT_GAP;
        }

        int textY = y + (TOAST_HEIGHT - textRenderer.fontHeight) / 2;

        // Embed alpha into white so drawText respects it (colour is ARGB)
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0x00FFFFFF;

        context.drawText(textRenderer, message, contentX, textY, textColor, true);

        RenderSystem.disableBlend();
    }

    /**
     * Computes the current opacity of the toast in the {@code [0.0, 1.0]} range,
     * applying linear transitions for the fade-in and fade-out phases.
     *
     * @return alpha value
     */
    private float getAlpha() {
        long elapsed = System.currentTimeMillis() - startTimeMs;

        if (elapsed < FADE_IN_MS) {
            // Fade-in: 0
            return (float) elapsed / FADE_IN_MS;
        } else if (elapsed < FADE_IN_MS + DISPLAY_MS) {
            // Fully visible
            return 1.0f;
        } else {
            // Fade-out
            long fadeElapsed = elapsed - FADE_IN_MS - DISPLAY_MS;
            return 1.0f - (float) fadeElapsed / FADE_OUT_MS;
        }
    }
}

