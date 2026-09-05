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

    /** Source texture width of the scroll-button PNG. */
    private static final int SCROLL_TEX_W = 96;

    /** Source texture height of the scroll-button PNG. */
    private static final int SCROLL_TEX_H = 48;

    /** Source width of each rolled end in the scroll-button PNG. */
    private static final int SCROLL_SIDE = 27;

    /** Source texture width of the page separator PNG. */
    private static final int SEPARATOR_TEX_W = 512;

    /** Source texture height of the page separator PNG. */
    private static final int SEPARATOR_TEX_H = 40;

    /** Source width of each separator end slice. */
    private static final int SEPARATOR_EDGE_W = 42;

    /** Source width of each separator grow-cap slice. */
    private static final int SEPARATOR_CAP_W = 22;

    /** Source width of each separator grow-line slice. */
    private static final int SEPARATOR_LINE_W = 28;

    /** Source width of each separator optional flourish slice. */
    private static final int SEPARATOR_FLOURISH_W = 34;

    /** Source width of the separator center ornament slice. */
    private static final int SEPARATOR_CENTER_W = 216;

    /** Minimum source width allowed for each separator grow-line slice. */
    private static final int SEPARATOR_MIN_LINE_W = 4;

    /** Source u coordinate of the left separator edge. */
    private static final int SEPARATOR_LEFT_EDGE_U = 0;

    /** Source u coordinate of the left separator grow left cap. */
    private static final int SEPARATOR_LEFT_GROW_LCAP_U = 42;

    /** Source u coordinate of the left separator grow line. */
    private static final int SEPARATOR_LEFT_GROW_LINE_U = 64;

    /** Source u coordinate of the left separator grow right cap. */
    private static final int SEPARATOR_LEFT_GROW_RCAP_U = 92;

    /** Source u coordinate of the left separator flourish. */
    private static final int SEPARATOR_LEFT_FLOURISH_U = 114;

    /** Source u coordinate of the separator center ornament. */
    private static final int SEPARATOR_CENTER_U = 148;

    /** Source u coordinate of the right separator flourish. */
    private static final int SEPARATOR_RIGHT_FLOURISH_U = 364;

    /** Source u coordinate of the right separator grow left cap. */
    private static final int SEPARATOR_RIGHT_GROW_LCAP_U = 398;

    /** Source u coordinate of the right separator grow line. */
    private static final int SEPARATOR_RIGHT_GROW_LINE_U = 420;

    /** Source u coordinate of the right separator grow right cap. */
    private static final int SEPARATOR_RIGHT_GROW_RCAP_U = 448;

    /** Source u coordinate of the right separator edge. */
    private static final int SEPARATOR_RIGHT_EDGE_U = 470;

    private GuiTextures() {
    }

    /**
     * Draws the standard scroll-button background without tinting.
     *
     * @param context The GUI draw context.
     * @param x       The destination x coordinate.
     * @param y       The destination y coordinate.
     * @param width   The destination width.
     * @param height  The destination height.
     */
    public static void blitScrollBackground(GuiGraphicsExtractor context,
                                            int x, int y, int width, int height) {

        blitScrollBackground(context, x, y, width, height, ModConstants.COLOR_WHITE);
    }

    /**
     * Draws the standard scroll-button background with an ARGB tint.
     *
     * @param context The GUI draw context.
     * @param x       The destination x coordinate.
     * @param y       The destination y coordinate.
     * @param width   The destination width.
     * @param height  The destination height.
     * @param argb    The ARGB tint to apply.
     */
    public static void blitScrollBackground(GuiGraphicsExtractor context,
                                            int x, int y, int width, int height, int argb) {

        blitScrollThreeSlice(context, x, y, width, height, argb);
    }

    /**
     * Draws the scroll button as two scaled ends and one stretched center band.
     *
     * @param context The GUI draw context.
     * @param x       The destination x coordinate.
     * @param y       The destination y coordinate.
     * @param width   The destination width.
     * @param height  The destination height.
     * @param argb    The ARGB tint to apply.
     */
    private static void blitScrollThreeSlice(GuiGraphicsExtractor context,
                                             int x, int y, int width, int height, int argb) {

        if (width <= 0 || height <= 0) return;

        int side = Math.min(Math.round(SCROLL_SIDE * height / (float) SCROLL_TEX_H), width / 2);
        int centerWidth = width - side * 2;

        blitScaled(context, ModConstants.SCROLL_BUTTON_TEXTURE, x, y, side, height,
                0, 0, SCROLL_SIDE, SCROLL_TEX_H, SCROLL_TEX_W, SCROLL_TEX_H, argb);
        blitScaled(context, ModConstants.SCROLL_BUTTON_TEXTURE, x + width - side, y, side, height,
                SCROLL_TEX_W - SCROLL_SIDE, 0, SCROLL_SIDE, SCROLL_TEX_H,
                SCROLL_TEX_W, SCROLL_TEX_H, argb);

        if (centerWidth > 0) {
            blitScaled(context, ModConstants.SCROLL_BUTTON_TEXTURE, x + side, y, centerWidth, height,
                    SCROLL_SIDE, 0, SCROLL_TEX_W - SCROLL_SIDE * 2, SCROLL_TEX_H,
                    SCROLL_TEX_W, SCROLL_TEX_H, argb);
        }
    }

    /**
     * Draws an ornamented separator while stretching only the connecting line slices.
     *
     * @param context The GUI draw context.
     * @param x       The destination x coordinate.
     * @param y       The destination y coordinate.
     * @param width   The destination width.
     * @param height  The destination height.
     * @param argb    The ARGB tint to apply.
     */
    public static void blitSeparator(GuiGraphicsExtractor context, int x, int y,
                                     int width, int height, int argb) {

        if (width <= 0 || height <= 0) return;

        int edge = scaleSeparator(SEPARATOR_EDGE_W, height);
        int cap = scaleSeparator(SEPARATOR_CAP_W, height);
        int flourish = scaleSeparator(SEPARATOR_FLOURISH_W, height);
        int center = scaleSeparator(SEPARATOR_CENTER_W, height);
        int minGrow = Math.max(1, scaleSeparator(SEPARATOR_MIN_LINE_W, height));
        boolean drawFlourish = width >= 2 * (edge + 2 * cap + flourish + minGrow) + center;

        int centerX = x + (width - center) / 2;
        int leftLineX = x + edge + cap;
        int leftFlourishX = centerX - flourish;
        int leftInnerCapX = centerX - (drawFlourish ? flourish : 0) - cap;
        int leftLineWidth = leftInnerCapX - leftLineX;

        int rightEdgeX = x + width - edge;
        int rightOuterCapX = rightEdgeX - cap;
        int rightFlourishX = centerX + center;
        int rightInnerCapX = rightFlourishX + (drawFlourish ? flourish : 0);
        int rightLineX = rightInnerCapX + cap;
        int rightLineWidth = rightOuterCapX - rightLineX;

        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, x, y, edge, height,
                SEPARATOR_LEFT_EDGE_U, 0, SEPARATOR_EDGE_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, x + edge, y, cap, height,
                SEPARATOR_LEFT_GROW_LCAP_U, 0, SEPARATOR_CAP_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, leftLineX, y, leftLineWidth, height,
                SEPARATOR_LEFT_GROW_LINE_U, 0, SEPARATOR_LINE_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, leftInnerCapX, y, cap, height,
                SEPARATOR_LEFT_GROW_RCAP_U, 0, SEPARATOR_CAP_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        if (drawFlourish) {
            blitScaled(context, ModConstants.SEPARATOR_TEXTURE, leftFlourishX, y, flourish, height,
                    SEPARATOR_LEFT_FLOURISH_U, 0, SEPARATOR_FLOURISH_W, SEPARATOR_TEX_H,
                    SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        }
        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, centerX, y, center, height,
                SEPARATOR_CENTER_U, 0, SEPARATOR_CENTER_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        if (drawFlourish) {
            blitScaled(context, ModConstants.SEPARATOR_TEXTURE, rightFlourishX, y, flourish, height,
                    SEPARATOR_RIGHT_FLOURISH_U, 0, SEPARATOR_FLOURISH_W, SEPARATOR_TEX_H,
                    SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        }
        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, rightInnerCapX, y, cap, height,
                SEPARATOR_RIGHT_GROW_LCAP_U, 0, SEPARATOR_CAP_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, rightLineX, y, rightLineWidth, height,
                SEPARATOR_RIGHT_GROW_LINE_U, 0, SEPARATOR_LINE_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, rightOuterCapX, y, cap, height,
                SEPARATOR_RIGHT_GROW_RCAP_U, 0, SEPARATOR_CAP_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
        blitScaled(context, ModConstants.SEPARATOR_TEXTURE, rightEdgeX, y, edge, height,
                SEPARATOR_RIGHT_EDGE_U, 0, SEPARATOR_EDGE_W, SEPARATOR_TEX_H,
                SEPARATOR_TEX_W, SEPARATOR_TEX_H, argb);
    }

    /**
     * Scales a separator source-space size to the requested destination height.
     *
     * @param sourceSize The source-space size.
     * @param height     The destination height.
     * @return The scaled destination-space size.
     */
    private static int scaleSeparator(int sourceSize, int height) {

        return Math.round(sourceSize * height / (float) SEPARATOR_TEX_H);
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

        blitNineSliced(context, texture, x, y, width, height,
                leftSlice, topSlice, rightSlice, bottomSlice,
                regionWidth, regionHeight, u, v, textureWidth, textureHeight, argb, false);
    }

    /**
     * Draws a per-edge nine-sliced texture while stretching non-corner bands.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param leftSlice     The source width of the left slice.
     * @param topSlice      The source height of the top slice.
     * @param rightSlice    The source width of the right slice.
     * @param bottomSlice   The source height of the bottom slice.
     * @param regionWidth   The source region width.
     * @param regionHeight  The source region height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     * @param argb          The ARGB tint to apply.
     */
    public static void blitNineSlicedStretched(GuiGraphicsExtractor context, Identifier texture,
                                               int x, int y, int width, int height,
                                               int leftSlice, int topSlice, int rightSlice, int bottomSlice,
                                               int regionWidth, int regionHeight,
                                               int u, int v, int textureWidth, int textureHeight,
                                               int argb) {

        blitNineSliced(context, texture, x, y, width, height,
                leftSlice, topSlice, rightSlice, bottomSlice,
                regionWidth, regionHeight, u, v, textureWidth, textureHeight, argb, true);
    }

    /**
     * Draws a per-edge nine-sliced texture with tiled or stretched non-corner bands.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param leftSlice     The source width of the left slice.
     * @param topSlice      The source height of the top slice.
     * @param rightSlice    The source width of the right slice.
     * @param bottomSlice   The source height of the bottom slice.
     * @param regionWidth   The source region width.
     * @param regionHeight  The source region height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     * @param argb          The ARGB tint to apply.
     * @param stretchInner  Whether non-corner bands should be stretched instead of tiled.
     */
    private static void blitNineSliced(GuiGraphicsExtractor context, Identifier texture,
                                       int x, int y, int width, int height,
                                       int leftSlice, int topSlice, int rightSlice, int bottomSlice,
                                       int regionWidth, int regionHeight,
                                       int u, int v, int textureWidth, int textureHeight,
                                       int argb, boolean stretchInner) {

        if (width <= 0 || height <= 0) return;

        if (width == regionWidth && height == regionHeight) {
            context.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, argb);
            return;
        }

        int left = Math.min(Math.min(leftSlice, regionWidth / 2), width / 2);
        int right = Math.min(Math.min(rightSlice, regionWidth / 2), width / 2);
        int top = Math.min(Math.min(topSlice, regionHeight / 2), height / 2);
        int bottom = Math.min(Math.min(bottomSlice, regionHeight / 2), height / 2);
        int centerWidth = width - left - right;
        int centerHeight = height - top - bottom;
        int sourceCenterWidth = regionWidth - left - right;
        int sourceCenterHeight = regionHeight - top - bottom;

        if (top > 0) {
            if (left > 0) blit(context, texture, x, y, u, v, left, top, left, top, textureWidth, textureHeight, argb);
            if (centerWidth > 0 && sourceCenterWidth > 0) {
                blitBand(context, texture, x + left, y, centerWidth, top,
                        u + left, v, sourceCenterWidth, top, textureWidth, textureHeight, argb, stretchInner);
            }
            if (right > 0) {
                blit(context, texture, x + width - right, y, u + regionWidth - right, v,
                        right, top, right, top, textureWidth, textureHeight, argb);
            }
        }

        if (centerHeight > 0) {
            if (left > 0 && sourceCenterHeight > 0) {
                blitBand(context, texture, x, y + top, left, centerHeight,
                        u, v + top, left, sourceCenterHeight, textureWidth, textureHeight, argb, stretchInner);
            }
            if (centerWidth > 0 && sourceCenterWidth > 0 && sourceCenterHeight > 0) {
                blitBand(context, texture, x + left, y + top, centerWidth, centerHeight,
                        u + left, v + top, sourceCenterWidth, sourceCenterHeight,
                        textureWidth, textureHeight, argb, stretchInner);
            }
            if (right > 0 && sourceCenterHeight > 0) {
                blitBand(context, texture, x + width - right, y + top, right, centerHeight,
                        u + regionWidth - right, v + top, right, sourceCenterHeight,
                        textureWidth, textureHeight, argb, stretchInner);
            }
        }

        if (bottom > 0) {
            if (left > 0) {
                blit(context, texture, x, y + height - bottom, u, v + regionHeight - bottom,
                        left, bottom, left, bottom, textureWidth, textureHeight, argb);
            }
            if (centerWidth > 0 && sourceCenterWidth > 0) {
                blitBand(context, texture, x + left, y + height - bottom, centerWidth, bottom,
                        u + left, v + regionHeight - bottom, sourceCenterWidth, bottom,
                        textureWidth, textureHeight, argb, stretchInner);
            }
            if (right > 0) {
                blit(context, texture, x + width - right, y + height - bottom,
                        u + regionWidth - right, v + regionHeight - bottom,
                        right, bottom, right, bottom, textureWidth, textureHeight, argb);
            }
        }
    }

    /**
     * Draws one nine-slice band, either tiled or stretched to the destination size.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param sourceWidth   The source region width.
     * @param sourceHeight  The source region height.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     * @param argb          The ARGB tint to apply.
     * @param stretch       Whether to stretch instead of tile.
     */
    private static void blitBand(GuiGraphicsExtractor context, Identifier texture,
                                 int x, int y, int width, int height,
                                 int u, int v, int sourceWidth, int sourceHeight,
                                 int textureWidth, int textureHeight, int argb,
                                 boolean stretch) {

        if (stretch) {
            blitScaled(context, texture, x, y, width, height, u, v, sourceWidth, sourceHeight,
                    textureWidth, textureHeight, argb);
        } else {
            blitRepeating(context, texture, x, y, width, height, u, v, sourceWidth, sourceHeight,
                    textureWidth, textureHeight, argb);
        }
    }

    private static void blit(GuiGraphicsExtractor context, Identifier texture,
                             int x, int y, int u, int v, int width, int height,
                             int regionWidth, int regionHeight, int textureWidth, int textureHeight,
                             int argb) {

        context.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, argb);
    }

    /**
     * Draws a scaled source rectangle from a GUI texture.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param sourceWidth   The source region width.
     * @param sourceHeight  The source region height.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     * @param argb          The ARGB tint to apply.
     */
    public static void blitScaled(GuiGraphicsExtractor context, Identifier texture,
                                  int x, int y, int width, int height,
                                  int u, int v, int sourceWidth, int sourceHeight,
                                  int textureWidth, int textureHeight, int argb) {

        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;

        blit(context, texture, x, y, u, v, width, height, sourceWidth, sourceHeight,
                textureWidth, textureHeight, argb);
    }

    /**
     * Draws a source rectangle repeatedly along x while scaling each tile to the destination height.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param sourceWidth   The source region width for one full tile.
     * @param sourceHeight  The source region height.
     * @param tileWidth     The destination width for one full tile.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     * @param argb          The ARGB tint to apply.
     */
    public static void blitRepeatingScaled(GuiGraphicsExtractor context, Identifier texture,
                                           int x, int y, int width, int height,
                                           int u, int v, int sourceWidth, int sourceHeight,
                                           int tileWidth,
                                           int textureWidth, int textureHeight, int argb) {

        blitRepeatingScaledAxis(context, texture, x, y, width, height, u, v, sourceWidth, sourceHeight,
                tileWidth, textureWidth, textureHeight, argb, false);
    }

    /**
     * Draws a source rectangle repeatedly along y while scaling each tile to the destination width.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param sourceWidth   The source region width.
     * @param sourceHeight  The source region height for one full tile.
     * @param tileHeight    The destination height for one full tile.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     * @param argb          The ARGB tint to apply.
     */
    public static void blitRepeatingScaledVertical(GuiGraphicsExtractor context, Identifier texture,
                                                   int x, int y, int width, int height,
                                                   int u, int v, int sourceWidth, int sourceHeight,
                                                   int tileHeight,
                                                   int textureWidth, int textureHeight, int argb) {

        blitRepeatingScaledAxis(context, texture, x, y, width, height, u, v, sourceWidth, sourceHeight,
                tileHeight, textureWidth, textureHeight, argb, true);
    }

    /**
     * Draws a source rectangle repeatedly along one axis while scaling along the other.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param sourceWidth   The source region width.
     * @param sourceHeight  The source region height.
     * @param tile          The destination size for one full tile along the repeated axis.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     * @param argb          The ARGB tint to apply.
     * @param vertical      Whether to repeat along y instead of x.
     */
    private static void blitRepeatingScaledAxis(GuiGraphicsExtractor context, Identifier texture,
                                                int x, int y, int width, int height,
                                                int u, int v, int sourceWidth, int sourceHeight,
                                                int tile,
                                                int textureWidth, int textureHeight, int argb,
                                                boolean vertical) {

        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0 || tile <= 0) return;

        int end = vertical ? y + height : x + width;
        int draw = vertical ? y : x;
        int sourceFull = vertical ? sourceHeight : sourceWidth;

        while (draw < end) {
            int chunk = Math.min(tile, end - draw);
            int sourceChunk = chunk == tile
                    ? sourceFull
                    : Math.max(1, Math.round(sourceFull * (chunk / (float) tile)));

            if (vertical) {
                blit(context, texture, x, draw, u, v, width, chunk, sourceWidth, sourceChunk,
                        textureWidth, textureHeight, argb);
            } else {
                blit(context, texture, draw, y, u, v, chunk, height, sourceChunk, sourceHeight,
                        textureWidth, textureHeight, argb);
            }
            draw += chunk;
        }
    }

    /**
     * Returns the standard tint for active or disabled widgets.
     *
     * @param active Whether the widget is active.
     * @return The ARGB tint.
     */
    public static int tint(boolean active) {

        return active ? ModConstants.COLOR_WHITE : argb(.85f, .85f, .85f, 1f);
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

    /**
     * Draws a nine-sliced texture while scaling source corners to a different destination size.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param sourceCorner  The source-space corner size.
     * @param drawnCorner   The destination-space corner size.
     * @param regionWidth   The source region width.
     * @param regionHeight  The source region height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     */
    public static void blitNineSlicedScaled(GuiGraphicsExtractor context, Identifier texture,
                                            int x, int y, int width, int height,
                                            int sourceCorner, int drawnCorner,
                                            int regionWidth, int regionHeight,
                                            int u, int v, int textureWidth, int textureHeight) {

        blitNineSlicedScaled(context, texture, x, y, width, height, sourceCorner, drawnCorner,
                regionWidth, regionHeight, u, v, textureWidth, textureHeight, ModConstants.COLOR_WHITE);
    }

    /**
     * Draws a tinted nine-sliced texture while scaling source corners to a different destination size.
     *
     * @param context       The GUI draw context.
     * @param texture       The texture to draw.
     * @param x             The destination x coordinate.
     * @param y             The destination y coordinate.
     * @param width         The destination width.
     * @param height        The destination height.
     * @param sourceCorner  The source-space corner size.
     * @param drawnCorner   The destination-space corner size.
     * @param regionWidth   The source region width.
     * @param regionHeight  The source region height.
     * @param u             The source u coordinate.
     * @param v             The source v coordinate.
     * @param textureWidth  The full texture width.
     * @param textureHeight The full texture height.
     * @param argb          The ARGB tint to apply.
     */
    public static void blitNineSlicedScaled(GuiGraphicsExtractor context, Identifier texture,
                                            int x, int y, int width, int height,
                                            int sourceCorner, int drawnCorner,
                                            int regionWidth, int regionHeight,
                                            int u, int v, int textureWidth, int textureHeight,
                                            int argb) {

        if (width <= 0 || height <= 0) return;

        int corner = Math.min(drawnCorner, Math.min(width / 2, height / 2));
        int centerWidth = width - corner * 2;
        int centerHeight = height - corner * 2;
        int sourceCenterWidth = regionWidth - sourceCorner * 2;
        int sourceCenterHeight = regionHeight - sourceCorner * 2;
        int rightX = x + width - corner;
        int bottomY = y + height - corner;
        int sourceRightU = u + regionWidth - sourceCorner;
        int sourceBottomV = v + regionHeight - sourceCorner;

        if (corner > 0) {
            blit(context, texture, x, y, u, v,
                    corner, corner, sourceCorner, sourceCorner, textureWidth, textureHeight, argb);
            blit(context, texture, rightX, y, sourceRightU, v,
                    corner, corner, sourceCorner, sourceCorner, textureWidth, textureHeight, argb);
            blit(context, texture, x, bottomY, u, sourceBottomV,
                    corner, corner, sourceCorner, sourceCorner, textureWidth, textureHeight, argb);
            blit(context, texture, rightX, bottomY, sourceRightU, sourceBottomV,
                    corner, corner, sourceCorner, sourceCorner, textureWidth, textureHeight, argb);
        }

        if (centerWidth > 0 && sourceCenterWidth > 0 && corner > 0) {
            blit(context, texture, x + corner, y, u + sourceCorner, v,
                    centerWidth, corner, sourceCenterWidth, sourceCorner, textureWidth, textureHeight, argb);
            blit(context, texture, x + corner, bottomY, u + sourceCorner, sourceBottomV,
                    centerWidth, corner, sourceCenterWidth, sourceCorner, textureWidth, textureHeight, argb);
        }

        if (centerHeight > 0 && sourceCenterHeight > 0 && corner > 0) {
            blit(context, texture, x, y + corner, u, v + sourceCorner,
                    corner, centerHeight, sourceCorner, sourceCenterHeight, textureWidth, textureHeight, argb);
            blit(context, texture, rightX, y + corner, sourceRightU, v + sourceCorner,
                    corner, centerHeight, sourceCorner, sourceCenterHeight, textureWidth, textureHeight, argb);
        }

        if (centerWidth > 0 && centerHeight > 0 && sourceCenterWidth > 0 && sourceCenterHeight > 0) {
            blit(context, texture, x + corner, y + corner, u + sourceCorner, v + sourceCorner,
                    centerWidth, centerHeight, sourceCenterWidth, sourceCenterHeight,
                    textureWidth, textureHeight, argb);
        }
    }

    public static void blitRepeating(GuiGraphicsExtractor context, Identifier texture,
                                     int x, int y, int width, int height,
                                     int u, int v, int tileWidth, int tileHeight,
                                     int textureWidth, int textureHeight) {
        blitRepeating(context, texture, x, y, width, height, u, v, tileWidth, tileHeight,
                textureWidth, textureHeight, ModConstants.COLOR_WHITE);
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
