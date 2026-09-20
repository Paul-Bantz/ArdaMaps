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

import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.widgets.popup.PopupSurface;
import com.duom.ardamaps.gui.widgets.popup.TravelsPopupContent;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Rotated paper sheet tab that opens the movement-tracking popup.
 */
public class TravelsSheetButton {

    /** Sheet left offset from the book texture's left edge. */
    private static final int SHEET_OFFSET_X = 6;

    /** Screen x, relative to the book left edge, the sheet is clipped at. */
    private static final int SHEET_CLIP_X = 22;

    /** Extra width past the clip line so the sheet always overshoots it. */
    private static final int SHEET_OVERSHOOT = 32;

    /** Vertical bleed for rotated paper inside the axis-aligned scissor band. */
    private static final int SHEET_BLEED_Y = 4;

    /** Counter-clockwise sheet rotation in degrees. */
    private static final float SHEET_ROTATION = -2.5f;

    /** Horizontal slide applied while hovered. */
    private static final int HOVER_SLIDE = 5;

    /** Drawn width of the cast-shadow slice. */
    private static final int SHADOW_WIDTH = 12;

    /** Pixel size of the square cast-shadow source texture. */
    private static final int SHADOW_TEXTURE_SIZE = 24;

    /** Hover animation duration in milliseconds. */
    private static final long HOVER_ANIMATION_MS = 120L;

    /** Supplies the current outer book texture area. */
    private final Supplier<BackgroundRenderer.GuiLayout> bookAreaSupplier;

    /** Smooth hover slide animation. */
    private final ExpansionAnimation hoverAnimation = new ExpansionAnimation(HOVER_ANIMATION_MS);

    /** The x-coordinate of the unshifted sheet. */
    private int x;

    /** The y-coordinate of the sheet. */
    private int y;

    /** The sheet width. */
    private int width;

    /** The sheet height. */
    private int height;

    /** Screen x coordinate where the visible sheet is clipped. */
    private int clipRight;

    /**
     * Creates a new travels sheet button.
     *
     * @param bookAreaSupplier Supplies the current outer book texture area.
     */
    public TravelsSheetButton(Supplier<BackgroundRenderer.GuiLayout> bookAreaSupplier) {

        this.bookAreaSupplier = bookAreaSupplier;
    }

    /**
     * Recalculates sheet geometry from the current book area.
     */
    public void layout() {

        BackgroundRenderer.GuiLayout bookArea = bookAreaSupplier.get();
        x = bookArea.topLeftX() + SHEET_OFFSET_X - ModConstants.POPUP_SHADOW_INSET;
        width = SHEET_CLIP_X - SHEET_OFFSET_X + SHEET_OVERSHOOT + ModConstants.POPUP_SHADOW_INSET;
        height = TravelsPopupContent.preferredHeight(bookArea);
        y = bookArea.topLeftY() + (bookArea.guiHeight() - height) / 2;
        clipRight = bookArea.topLeftX() + SHEET_CLIP_X;
    }

    /**
     * Renders the sheet tab.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     * @param ignoredDelta   The frame ignoredDelta.
     */
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float ignoredDelta) {

        boolean hovered = isMouseOver(mouseX, mouseY);
        float slide = HOVER_SLIDE * hoverAnimation.update(hovered);
        int drawX = Math.round(x - slide);

        context.enableScissor(x - SHEET_OFFSET_X, y, clipRight, y + height);
        try {
            context.pose().pushMatrix();
            context.pose().rotateAbout((float) Math.toRadians(SHEET_ROTATION), x, y);
            PopupSurface.drawBackground(context, drawX, y - SHEET_BLEED_Y, width, height + SHEET_BLEED_Y * 2);
            GuiTextures.blitScaled(context, ModConstants.SHADOW_HSLICE_TEXTURE,
                    clipRight - SHADOW_WIDTH, y - SHEET_BLEED_Y, SHADOW_WIDTH, height + SHEET_BLEED_Y * 2,
                    0, 0, SHADOW_TEXTURE_SIZE, SHADOW_TEXTURE_SIZE, SHADOW_TEXTURE_SIZE, SHADOW_TEXTURE_SIZE,
                    ModConstants.COLOR_WHITE);
            context.pose().popMatrix();
        } finally {
            context.disableScissor();
        }

        if (!hovered) return;

        context.requestCursor(CursorTypes.POINTING_HAND);
        context.setTooltipForNextFrame(Client.mc().font,
                Component.translatable("ardamaps.client.map.screen.travels.tooltip"),
                mouseX,
                mouseY);
    }

    /**
     * Checks whether the mouse is over the visible sheet tab.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True when the mouse is over the visible sheet.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {

        float slide = HOVER_SLIDE * hoverAnimation.value();
        return hitTest(mouseX, mouseY, Math.round(x - slide), y, width, height, clipRight);
    }

    /**
     * Checks whether a point falls inside the visible, rotated, clipped sheet.
     *
     * @param mouseX    The mouse x coordinate.
     * @param mouseY    The mouse y coordinate.
     * @param x         The sheet x coordinate.
     * @param y         The sheet y coordinate.
     * @param width     The sheet width.
     * @param height    The sheet height.
     * @param clipRight The clipping boundary's right x coordinate.
     * @return True when the point is inside the visible rotated sheet.
     */
    static boolean hitTest(double mouseX, double mouseY, int x, int y, int width, int height, int clipRight) {

        if (mouseX >= clipRight) return false;

        double angle = Math.toRadians(-SHEET_ROTATION);
        double dx = mouseX - x;
        double dy = mouseY - y;
        double localX = dx * Math.cos(angle) - dy * Math.sin(angle);
        double localY = dx * Math.sin(angle) + dy * Math.cos(angle);

        return localX >= 0
                && localX <= width
                && localY >= 0
                && localY <= height;
    }
}
