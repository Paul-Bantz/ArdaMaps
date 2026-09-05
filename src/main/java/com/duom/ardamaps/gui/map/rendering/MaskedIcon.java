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

package com.duom.ardamaps.gui.map.rendering;

import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * Facade for drawing icon-atlas sprites clipped by a raw texture alpha mask.
 */
public final class MaskedIcon {

    private MaskedIcon() {
    }

    /**
     * Draws an icon sprite through an alpha mask.
     *
     * @param context      The GUI draw context.
     * @param iconSpriteId The icon sprite identifier.
     * @param mask         The raw texture mask identifier.
     * @param x            The icon x position.
     * @param y            The icon y position.
     * @param width        The icon width.
     * @param height       The icon height.
     * @param maskU0       The left mask UV.
     * @param maskV0       The top mask UV.
     * @param maskU1       The right mask UV.
     * @param maskV1       The bottom mask UV.
     */
    public static void draw(GuiGraphicsExtractor context,
                            Identifier iconSpriteId,
                            Identifier mask,
                            int x,
                            int y,
                            int width,
                            int height,
                            float maskU0,
                            float maskV0,
                            float maskU1,
                            float maskV1) {

        // The atlas owns this sprite; closing it would free shared atlas pixels.
        @SuppressWarnings("resource") var sprite = IconSpriteAtlas.retrieveSprite(iconSpriteId);
        draw(context,
                MaskedIconRenderState.iconAtlasTexture(),
                sprite.getU0(),
                sprite.getV0(),
                sprite.getU1(),
                sprite.getV1(),
                mask,
                x,
                y,
                width,
                height,
                maskU0,
                maskV0,
                maskU1,
                maskV1);
    }

    /**
     * Draws a texture region through an alpha mask.
     *
     * @param context The GUI draw context.
     * @param texture The texture identifier.
     * @param iconU0  The left icon UV.
     * @param iconV0  The top icon UV.
     * @param iconU1  The right icon UV.
     * @param iconV1  The bottom icon UV.
     * @param mask    The raw texture mask identifier.
     * @param x       The icon x position.
     * @param y       The icon y position.
     * @param width   The icon width.
     * @param height  The icon height.
     * @param maskU0  The left mask UV.
     * @param maskV0  The top mask UV.
     * @param maskU1  The right mask UV.
     * @param maskV1  The bottom mask UV.
     */
    public static void draw(GuiGraphicsExtractor context,
                            Identifier texture,
                            float iconU0,
                            float iconV0,
                            float iconU1,
                            float iconV1,
                            Identifier mask,
                            int x,
                            int y,
                            int width,
                            int height,
                            float maskU0,
                            float maskV0,
                            float maskU1,
                            float maskV1) {

        GuiRenderStateAccess.add(context, new MaskedIconRenderState(
                texture,
                mask,
                new Matrix3x2f(context.pose()),
                x,
                y,
                x + width,
                y + height,
                iconU0,
                iconV0,
                iconU1,
                iconV1,
                maskU0,
                maskV0,
                maskU1,
                maskV1,
                GuiRenderStateAccess.scissorArea(context)));
    }
}
