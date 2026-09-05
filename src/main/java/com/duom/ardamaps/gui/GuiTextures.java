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

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * GUI texture helpers for operations removed or made easy to mis-call in the 26.1 GUI API.
 */
public final class GuiTextures {

    private GuiTextures() {
    }

    public static void blitNineSliced(GuiGraphicsExtractor context, Identifier texture,
                                      int x, int y, int width, int height,
                                      int cornerWidth, int cornerHeight,
                                      int regionWidth, int regionHeight,
                                      int u, int v, int textureWidth, int textureHeight) {
        blitNineSliced(context, texture, x, y, width, height, cornerWidth, cornerHeight,
                regionWidth, regionHeight, u, v, textureWidth, textureHeight, ModConstants.COLOR_WHITE);
    }

    public static void blitNineSliced(GuiGraphicsExtractor context, Identifier texture,
                                      int x, int y, int width, int height,
                                      int cornerWidth, int cornerHeight,
                                      int regionWidth, int regionHeight,
                                      int u, int v, int textureWidth, int textureHeight,
                                      int argb) {
        blitNineSliced(context, texture, x, y, width, height,
                cornerWidth, cornerHeight, cornerWidth, cornerHeight,
                regionWidth, regionHeight, u, v, textureWidth, textureHeight, argb);
    }

    public static void blitNineSliced(GuiGraphicsExtractor context, Identifier texture,
                                      int x, int y, int width, int height,
                                      int leftSlice, int topSlice, int rightSlice, int bottomSlice,
                                      int regionWidth, int regionHeight,
                                      int u, int v, int textureWidth, int textureHeight,
                                      int argb) {
        if (width <= 0 || height <= 0) return;

        if (width == regionWidth && height == regionHeight) {
            context.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, argb);
            return;
        }

        int left = Math.min(leftSlice, width / 2);
        int right = Math.min(rightSlice, width / 2);
        int top = Math.min(topSlice, height / 2);
        int bottom = Math.min(bottomSlice, height / 2);
        int centerWidth = width - left - right;
        int centerHeight = height - top - bottom;
        int sourceCenterWidth = regionWidth - leftSlice - rightSlice;
        int sourceCenterHeight = regionHeight - topSlice - bottomSlice;

        if (top > 0) {
            if (left > 0) blit(context, texture, x, y, u, v, left, top, left, top, textureWidth, textureHeight, argb);
            if (centerWidth > 0 && sourceCenterWidth > 0) {
                blitRepeating(context, texture, x + left, y, centerWidth, top,
                        u + leftSlice, v, sourceCenterWidth, top, textureWidth, textureHeight, argb);
            }
            if (right > 0) {
                blit(context, texture, x + width - right, y, u + regionWidth - right, v,
                        right, top, right, top, textureWidth, textureHeight, argb);
            }
        }

        if (centerHeight > 0) {
            if (left > 0 && sourceCenterHeight > 0) {
                blitRepeating(context, texture, x, y + top, left, centerHeight,
                        u, v + topSlice, left, sourceCenterHeight, textureWidth, textureHeight, argb);
            }
            if (centerWidth > 0 && sourceCenterWidth > 0 && sourceCenterHeight > 0) {
                blitRepeating(context, texture, x + left, y + top, centerWidth, centerHeight,
                        u + leftSlice, v + topSlice, sourceCenterWidth, sourceCenterHeight, textureWidth, textureHeight, argb);
            }
            if (right > 0 && sourceCenterHeight > 0) {
                blitRepeating(context, texture, x + width - right, y + top, right, centerHeight,
                        u + regionWidth - right, v + topSlice, right, sourceCenterHeight, textureWidth, textureHeight, argb);
            }
        }

        if (bottom > 0) {
            if (left > 0) {
                blit(context, texture, x, y + height - bottom, u, v + regionHeight - bottom,
                        left, bottom, left, bottom, textureWidth, textureHeight, argb);
            }
            if (centerWidth > 0 && sourceCenterWidth > 0) {
                blitRepeating(context, texture, x + left, y + height - bottom, centerWidth, bottom,
                        u + leftSlice, v + regionHeight - bottom, sourceCenterWidth, bottom, textureWidth, textureHeight, argb);
            }
            if (right > 0) {
                blit(context, texture, x + width - right, y + height - bottom,
                        u + regionWidth - right, v + regionHeight - bottom,
                        right, bottom, right, bottom, textureWidth, textureHeight, argb);
            }
        }
    }

    private static void blit(GuiGraphicsExtractor context, Identifier texture,
                             int x, int y, int u, int v, int width, int height,
                             int regionWidth, int regionHeight, int textureWidth, int textureHeight,
                             int argb) {
        context.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, argb);
    }

    public static void blitRepeating(GuiGraphicsExtractor context, Identifier texture,
                                     int x, int y, int width, int height,
                                     int u, int v, int tileWidth, int tileHeight,
                                     int textureWidth, int textureHeight,
                                     int argb) {
        if (width <= 0 || height <= 0 || tileWidth <= 0 || tileHeight <= 0) return;

        int xParts = Math.ceilDiv(width, tileWidth);
        int yParts = Math.ceilDiv(height, tileHeight);
        int drawY = y;

        for (int yi = 0; yi < yParts; yi++) {
            int chunkHeight = chunkSize(height, yParts, yi);
            int sourceV = v + (tileHeight - chunkHeight) / 2;
            int drawX = x;

            for (int xi = 0; xi < xParts; xi++) {
                int chunkWidth = chunkSize(width, xParts, xi);
                int sourceU = u + (tileWidth - chunkWidth) / 2;
                blit(context, texture, drawX, drawY, sourceU, sourceV,
                        chunkWidth, chunkHeight, chunkWidth, chunkHeight, textureWidth, textureHeight, argb);
                drawX += chunkWidth;
            }

            drawY += chunkHeight;
        }
    }

    private static int chunkSize(int totalSize, int parts, int index) {
        int base = totalSize / parts;
        int remainder = totalSize % parts;
        return base + (index < remainder ? 1 : 0);
    }

    public static void blitNineSliced(GuiGraphicsExtractor context, Identifier texture,
                                      int x, int y, int width, int height,
                                      int leftSlice, int topSlice, int rightSlice, int bottomSlice,
                                      int regionWidth, int regionHeight,
                                      int u, int v, int textureWidth, int textureHeight) {
        blitNineSliced(context, texture, x, y, width, height,
                leftSlice, topSlice, rightSlice, bottomSlice,
                regionWidth, regionHeight, u, v, textureWidth, textureHeight, ModConstants.COLOR_WHITE);
    }

    public static void blitRepeating(GuiGraphicsExtractor context, Identifier texture,
                                     int x, int y, int width, int height,
                                     int u, int v, int tileWidth, int tileHeight,
                                     int textureWidth, int textureHeight) {
        blitRepeating(context, texture, x, y, width, height, u, v, tileWidth, tileHeight,
                textureWidth, textureHeight, ModConstants.COLOR_WHITE);
    }

    public static void blitMirroredH(GuiGraphicsExtractor context, Identifier texture,
                                     int x, int y, int width, int height,
                                     int u, int v, int sourceWidth, int sourceHeight,
                                     int textureWidth, int textureHeight) {
        context.blit(texture, x, y, x + width, y + height,
                (float) (u + sourceWidth) / textureWidth,
                (float) u / textureWidth,
                (float) v / textureHeight,
                (float) (v + sourceHeight) / textureHeight);
    }

    public static int argb(float red, float green, float blue, float alpha) {
        return (component(alpha) << 24)
                | (component(red) << 16)
                | (component(green) << 8)
                | component(blue);
    }

    private static int component(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    public static int withAlpha(int argb, float alpha) {
        return (argb & 0x00FFFFFF) | (component(alpha) << 24);
    }
}
