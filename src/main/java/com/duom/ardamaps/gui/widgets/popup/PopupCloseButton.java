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

package com.duom.ardamaps.gui.widgets.popup;

import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.ModConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Shared renderer and hit tester for popup close buttons.
 */
public final class PopupCloseButton {

    /** Rendered size of the vanilla reject sprite, in pixels. */
    public static final int SIZE = 18;

    /** Inset from the popup's top-right corner. */
    private static final int INSET = 6;

    /** Vanilla sprite used for the close affordance. */
    private static final Identifier SPRITE = Identifier.withDefaultNamespace("pending_invite/reject_highlighted");

    private PopupCloseButton() {

    }

    /**
     * Gets the close button x coordinate.
     *
     * @param popupX     The popup x coordinate.
     * @param popupWidth The popup width.
     * @return The button x coordinate.
     */
    public static int x(int popupX, int popupWidth) {

        return popupX + popupWidth - SIZE - INSET;
    }

    /**
     * Gets the close button y coordinate.
     *
     * @param popupY The popup y coordinate.
     * @return The button y coordinate.
     */
    public static int y(int popupY) {

        return popupY + INSET;
    }

    /**
     * Checks whether the mouse is over the close button.
     *
     * @param popupX     The popup x coordinate.
     * @param popupY     The popup y coordinate.
     * @param popupWidth The popup width.
     * @param mouseX     The mouse x coordinate.
     * @param mouseY     The mouse y coordinate.
     * @return True when the mouse is over the button.
     */
    public static boolean isMouseOver(int popupX, int popupY, int popupWidth, double mouseX, double mouseY) {

        int buttonX = x(popupX, popupWidth);
        int buttonY = y(popupY);
        return mouseX >= buttonX
                && mouseX < buttonX + SIZE
                && mouseY >= buttonY
                && mouseY < buttonY + SIZE;
    }

    /**
     * Renders the close button.
     *
     * @param context    The GUI draw context.
     * @param popupX     The popup x coordinate.
     * @param popupY     The popup y coordinate.
     * @param popupWidth The popup width.
     * @param mouseX     The mouse x coordinate.
     * @param mouseY     The mouse y coordinate.
     */
    public static void render(GuiGraphicsExtractor context, int popupX, int popupY, int popupWidth, int mouseX, int mouseY) {

        boolean hovered = isMouseOver(popupX, popupY, popupWidth, mouseX, mouseY);
        int tint = hovered ? GuiTextures.withAlpha(ModConstants.COLOR_WHITE, 0.75f) : ModConstants.COLOR_WHITE;
        int buttonX = x(popupX, popupWidth);
        int buttonY = y(popupY);

        context.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE, buttonX, buttonY, SIZE, SIZE, tint);

        if (!hovered) return;

        context.requestCursor(CursorTypes.POINTING_HAND);
        context.setTooltipForNextFrame(Client.mc().font,
                Component.translatable("ardamaps.client.generic.close"),
                mouseX,
                mouseY);
    }
}
