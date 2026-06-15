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

import com.duom.ardamaps.gui.ModConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Renders the GUI background using a 9-slice technique.The texture represents the right page of a book (512×512).
 * A mirrored copy is drawn as the left page, and the original as the right page, split at screen centre (width/2).
 * <p>Texture is drawn at GUI_RATIO (default 16:9) to maintain consistency across screen resolutions</b></p>
 * <br/>
 * Texture layout (right page default - left mirrored at render time):
 * <table>
 *   <tr>
 *     <td>128px</td>
 *     <td>256px stretched</td>
 *     <td>128px</td>
 *   </tr>
 *   <tr>
 *     <td>128px stretched</td>
 *     <td>256px tiled</td>
 *     <td>128px stretched</td>
 *   </tr>
 *   <tr>
 *     <td>128px</td>
 *     <td>256px stretched</td>
 *     <td>128px</td>
 *   </tr>
 * </table>
 */
public class BackgroundRenderer {

    /** Aspect ratio of the entire book GUI (including margins) to maintain consistency across screen sizes. */
    private static final float GUI_RATIO = 16f / 9f;

    /** Scale factor applied to the original texture size when rendering on screen. */
    private static final float GUI_SCALE = .5f;

    /** Original texture size */
    private static final int TEXTURE_SIZE = 512;

    /** Margin between book area and screen edges */
    private static final int UI_MARGIN = 25;

    /** Corner patch size (fixed) */
    private static final int CORNER_SIZE = 128;

    /** Side patch size (fixed horizontally stretched vertically) */
    private static final int SIDE_SIZE = 256;

    /** Centre patch size (tiled) */
    private static final int CENTER_SIZE = 256;

    /** Scaled corner size on screen */
    private static final int SCALED_CORNER = Math.round(CORNER_SIZE * GUI_SCALE);

    /** Padding from page horizontal edges to usable content area */
    private static final int CONTENT_PADDING_X = 42;

    /** Padding from page vertical edges to usable content area */
    private static final int CONTENT_PADDING_Y = 16;

    /** Total horizontal padding from left edge of left page to right edge of right page */
    private static final int INNER_PADDING_X = 2 * CONTENT_PADDING_X;

    /** Total vertical padding from top edge to bottom edge of page (including inner gutter) */
    private static final int INNER_PADDING_Y = 2 * CONTENT_PADDING_Y + 8;

    /** Cached screen height from last render, used to detect when to recalculate layout. */
    private int width = -1;

    /** Cached screen height from last render, used to detect when to recalculate layout. */
    private int height = -1;

    /** Top-left corner X of the entire book GUI (including margins) */
    private int guiTopLeftX;

    /** Top-left corner Y of the entire book GUI (including margins) */
    private int guiTopLeftY;

    /** Width of each page texture (including corners) - scaled and cached */
    private int pageWidth;

    /** Height of the book texture (including corners) - scaled and cached */
    private int pageHeight;

    /** Usable inner area of each page (excluding corners) - scaled and cached */
    private int innerW;

    /** Usable inner area of each page (excluding corners) - scaled and cached */
    private int innerH;

    /**
     * Renders the book GUI background, recalculating layout if screen dimensions have changed since last render.
     *
     * @param context      the DrawContext to render with, provided by the caller's render method
     * @param screenWidth  current screen width in pixels, used to detect when to recalculate layout
     * @param screenHeight current screen height in pixels, used to detect when to recalculate layout
     */
    public void render(DrawContext context, int screenWidth, int screenHeight) {
        invalidate(screenWidth, screenHeight);
        drawNineSliceGui(context);
    }

    /**
     * Recalculates the layout of the GUI if the screen dimensions have changed since the last render.
     * Computes the largest 16:9 book area (including UI margins) that fits on screen, and caches the positions and sizes of each 9-slice piece for rendering.
     *
     * @param width  current screen width in pixels
     * @param height current screen height in pixels
     */
    public void invalidate(int width, int height) {

        // Exit early if dimensions haven't changed since last calculation
        if (width == this.width && height == this.height) return;

        this.width = width;
        this.height = height;

        // Compute the largest 16:9 book area (including UI margins) that fits on screen
        int bookW, bookH;
        if ((float) width / height >= GUI_RATIO) {
            // Height-constrained
            bookH = height;
            bookW = Math.round(height * GUI_RATIO);
        } else {
            // Width-constrained
            bookW = width;
            bookH = Math.round(width / GUI_RATIO);
        }

        pageHeight = bookH - 2 * UI_MARGIN;
        pageWidth = bookW / 2 - UI_MARGIN;

        innerW = pageWidth - 2 * SCALED_CORNER;
        innerH = pageHeight - 2 * SCALED_CORNER;

        // Centre the book on screen
        guiTopLeftX = (width - bookW) / 2 + UI_MARGIN;
        guiTopLeftY = (height - bookH) / 2 + UI_MARGIN;
    }

    /**
     * Renders the book GUI background using a 9-slice technique. The right page is drawn directly from the texture,
     * while the left page is drawn as a horizontally flipped copy with UV coordinates swapped for correct mirroring.
     * Nearest-neighbour filtering is enabled during rendering to maintain crisp pixel art, and restored to linear filtering afterwards.
     *
     * @param context the DrawContext to render with, provided by the caller's render method
     */
    private void drawNineSliceGui(DrawContext context) {
        // Enable nearest-neighbor filtering for crisp pixel rendering
        MinecraftClient.getInstance().getTextureManager().bindTexture(ModConstants.GUI_TEXTURE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        int spineX = guiTopLeftX + pageWidth; // spine = right edge of left page
        drawLeftPage(context);
        drawRightPage(context, spineX, guiTopLeftY);

        // Restore linear filtering
        MinecraftClient.getInstance().getTextureManager().bindTexture(ModConstants.GUI_TEXTURE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    /**
     * Draws the left page as a horizontally mirrored copy of the right page.
     * Each 9-slice piece is placed at the correct mirrored screen coordinate with
     * left/right UV patches swapped. The centre is tiled identically (symmetric).
     *
     * @param context the DrawContext to render with, provided by the caller's render method
     */
    private void drawLeftPage(DrawContext context) {
        // Screen bounds of the left page
        int x = guiTopLeftX;
        int rx = guiTopLeftX + pageWidth;
        int y = guiTopLeftY;
        int x2 = rx - SCALED_CORNER;
        int y2 = y + SCALED_CORNER + innerH;

        // Outer-left corners use the right-edge UV (384), inner-right corners use left-edge UV (0)
        drawTextureH(context, x, y, SCALED_CORNER, SCALED_CORNER, 384, 0, CORNER_SIZE, CORNER_SIZE);
        drawTextureH(context, x2, y, SCALED_CORNER, SCALED_CORNER, 0, 0, CORNER_SIZE, CORNER_SIZE);
        drawTextureH(context, x, y2, SCALED_CORNER, SCALED_CORNER, 384, 384, CORNER_SIZE, CORNER_SIZE);
        drawTextureH(context, x2, y2, SCALED_CORNER, SCALED_CORNER, 0, 384, CORNER_SIZE, CORNER_SIZE);

        // Top & bottom edges stretched horizontally, UV mirrored
        drawTextureH(context, x + SCALED_CORNER, y, innerW, SCALED_CORNER, 128, 0, SIDE_SIZE, CORNER_SIZE);
        drawTextureH(context, x + SCALED_CORNER, y2, innerW, SCALED_CORNER, 128, 384, SIDE_SIZE, CORNER_SIZE);

        // Left & right edges stretched vertically, UV swapped
        drawTextureH(context, x, y + SCALED_CORNER, SCALED_CORNER, innerH, 384, 128, CORNER_SIZE, SIDE_SIZE);
        drawTextureH(context, x2, y + SCALED_CORNER, SCALED_CORNER, innerH, 0, 128, CORNER_SIZE, SIDE_SIZE);

        // Center tiled (symmetric, same UV)
        tileBookTextureH(context, x + SCALED_CORNER, y + SCALED_CORNER, innerW, innerH, 128, 128, CENTER_SIZE, CENTER_SIZE);
    }

    /**
     * Draws the right page directly from the texture using the 9-slice technique.
     * Each piece is drawn at the correct screen coordinate with the original UV coordinates.
     *
     * @param context the DrawContext to render with, provided by the caller's render method
     * @param x       the X coordinate of the top-left corner of the right page (including border)
     * @param y       the Y coordinate of the top-left corner of the right page (including border)
     */
    private void drawRightPage(DrawContext context, int x, int y) {
        int x2 = x + SCALED_CORNER + innerW; // start of right corner column
        int y2 = y + SCALED_CORNER + innerH; // start of bottom corner row

        // Corners
        context.drawTexture(ModConstants.GUI_TEXTURE, x, y, SCALED_CORNER, SCALED_CORNER, 0, 0, CORNER_SIZE, CORNER_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.drawTexture(ModConstants.GUI_TEXTURE, x2, y, SCALED_CORNER, SCALED_CORNER, 384, 0, CORNER_SIZE, CORNER_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.drawTexture(ModConstants.GUI_TEXTURE, x, y2, SCALED_CORNER, SCALED_CORNER, 0, 384, CORNER_SIZE, CORNER_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.drawTexture(ModConstants.GUI_TEXTURE, x2, y2, SCALED_CORNER, SCALED_CORNER, 384, 384, CORNER_SIZE, CORNER_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        // Top & bottom edges - stretched horizontally
        context.drawTexture(ModConstants.GUI_TEXTURE, x + SCALED_CORNER, y, innerW, SCALED_CORNER, 128, 0, SIDE_SIZE, CORNER_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.drawTexture(ModConstants.GUI_TEXTURE, x + SCALED_CORNER, y2, innerW, SCALED_CORNER, 128, 384, SIDE_SIZE, CORNER_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        // Left & right edges - stretched vertically
        context.drawTexture(ModConstants.GUI_TEXTURE, x, y + SCALED_CORNER, SCALED_CORNER, innerH, 0, 128, CORNER_SIZE, SIDE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        context.drawTexture(ModConstants.GUI_TEXTURE, x2, y + SCALED_CORNER, SCALED_CORNER, innerH, 384, 128, CORNER_SIZE, SIDE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        // Centre - tiled
        tileBookTexture(context, x + SCALED_CORNER, y + SCALED_CORNER, innerW, innerH, 128, 128, CENTER_SIZE, CENTER_SIZE);
    }

    /**
     * Draws a single texture patch horizontally flipped using a Tessellator quad
     * with U coordinates swapped (u+srcW at left, u at right).
     *
     * @param context the DrawContext to render with, provided by the caller's render method
     * @param x       the X coordinate of the top-left corner of the destination rectangle to draw the texture patch into
     * @param y       the Y coordinate of the top-left corner of the destination rectangle to draw the texture patch into
     * @param w       the width of the destination rectangle to draw the texture patch into
     * @param h       the height of the destination rectangle to draw the texture patch into
     * @param u       the X coordinate of the top-left corner of the source texture patch in the texture image
     * @param v       the Y coordinate of the top-left corner of the source texture patch in the texture image
     * @param srcW    the width of the source texture patch in the texture image
     * @param srcH    the height of the source texture patch in the texture image
     */
    private void drawTextureH(DrawContext context,
                              int x, int y, int w, int h,
                              int u, int v, int srcW, int srcH) {
        var matrix = context.getMatrices().peek().getPositionMatrix();
        float u0 = (u + srcW) / (float) TEXTURE_SIZE;  // flipped: u+srcW on left
        float u1 = u / (float) TEXTURE_SIZE;  // flipped: u on right
        float v0 = v / (float) TEXTURE_SIZE;
        float v1 = (v + srcH) / (float) TEXTURE_SIZE;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, ModConstants.GUI_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        var buf = Tessellator.getInstance().getBuffer();
        buf.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,
                net.minecraft.client.render.VertexFormats.POSITION_TEXTURE);
        buf.vertex(matrix, x, y, 0).texture(u0, v0).next();
        buf.vertex(matrix, x, y + h, 0).texture(u0, v1).next();
        buf.vertex(matrix, x + w, y + h, 0).texture(u1, v1).next();
        buf.vertex(matrix, x + w, y, 0).texture(u1, v0).next();
        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    /**
     * Tiled version of drawTextureH - tiles the flipped patch to fill destinationWidth × destinationHeight.
     *
     * @param context           the DrawContext to render with, provided by the caller's render method
     * @param destinationX      the X coordinate of the top-left corner of the destination rectangle to
     *                          draw the tiled texture patch into
     * @param destinationY      the Y coordinate of the top-left corner of the destination rectangle to
     *                          draw the tiled texture patch into
     * @param destinationWidth  the width of the destination rectangle to draw the tiled texture patch into
     * @param destinationHeight the height of the destination rectangle to draw the tiled texture patch into
     * @param u                 the X coordinate of the top-left corner of the source texture patch in the texture image
     * @param v                 the Y coordinate of the top-left corner of the source texture patch in the texture image
     * @param srcW              the width of the source texture patch in the texture image
     * @param srcH              the height of the source texture patch in the texture image
     */
    @SuppressWarnings("SameParameterValue")
    private void tileBookTextureH(DrawContext context,
                                  int destinationX, int destinationY,
                                  int destinationWidth, int destinationHeight,
                                  int u, int v, int srcW, int srcH) {

        int tileW = Math.round(srcW * GUI_SCALE);
        int tileH = Math.round(srcH * GUI_SCALE);
        if (tileW <= 0 || tileH <= 0) return;

        for (int dy = 0; dy < destinationHeight; dy += tileH) {
            int drawH = Math.min(tileH, destinationHeight - dy);
            int partSrcH = drawH < tileH ? Math.round(srcH * ((float) drawH / tileH)) : srcH;

            for (int dx = 0; dx < destinationWidth; dx += tileW) {
                int drawW = Math.min(tileW, destinationWidth - dx);
                int partSrcW = drawW < tileW ? Math.round(srcW * ((float) drawW / tileW)) : srcW;
                drawTextureH(context, destinationX + dx, destinationY + dy, drawW, drawH,
                        u, v, partSrcW, partSrcH);
            }
        }
    }

    /**
     * Tiles a patch of GuiConstants.GUI_TEXTURE into a destination rectangle, clipping the last tile.
     *
     * @param context           the DrawContext to render with, provided by the caller's render method
     * @param destinationX      the X coordinate of the top-left corner of the destination rectangle to
     *                          draw the tiled texture patch into
     * @param destinationY      the Y coordinate of the top-left corner of the destination rectangle to
     *                          draw the tiled texture patch into
     * @param destinationWidth  the width of the destination rectangle to draw the tiled texture patch into
     * @param destinationHeight the height of the destination rectangle to draw the tiled texture patch into
     * @param u                 the X coordinate of the top-left corner of the source texture patch in the texture image
     * @param v                 the Y coordinate of the top-left corner of the source texture patch in the texture image
     * @param srcW              the width of the source texture patch in the texture image
     * @param srcH              the height of the source texture patch in the texture image
     */
    @SuppressWarnings("SameParameterValue")
    private void tileBookTexture(DrawContext context,
                                 int destinationX, int destinationY,
                                 int destinationWidth, int destinationHeight,
                                 int u, int v, int srcW, int srcH) {
        int tileW = Math.round(srcW * GUI_SCALE);
        int tileH = Math.round(srcH * GUI_SCALE);
        if (tileW <= 0 || tileH <= 0) return;

        for (int dy = 0; dy < destinationHeight; dy += tileH) {
            int drawH = Math.min(tileH, destinationHeight - dy);
            int partSrcH = drawH < tileH ? Math.round(srcH * ((float) drawH / tileH)) : srcH;

            for (int dx = 0; dx < destinationWidth; dx += tileW) {
                int drawW = Math.min(tileW, destinationWidth - dx);
                int partSrcW = drawW < tileW ? Math.round(srcW * ((float) drawW / tileW)) : srcW;
                context.drawTexture(ModConstants.GUI_TEXTURE,
                        destinationX + dx, destinationY + dy, drawW, drawH,
                        u, v, partSrcW, partSrcH, TEXTURE_SIZE, TEXTURE_SIZE);
            }
        }
    }

    /**
     * @return the usable content area of the GUI, excluding borders, as a GuiLayout record. Coordinates are in screen pixels.
     */
    public GuiLayout getGuiContentArea() {

        return getGuiContentArea(0);
    }

    /**
     * Returns the usable content area of the GUI, excluding borders, as a GuiLayout record. Coordinates are in screen pixels.
     *
     * @param extraPadding additional padding to apply on top of the default content padding
     * @return the usable content area of the GUI, excluding borders, as a GuiLayout record.
     */
    public GuiLayout getGuiContentArea(int extraPadding) {

        int contentX = guiTopLeftX + CONTENT_PADDING_X + extraPadding;
        int contentY = guiTopLeftY + CONTENT_PADDING_Y + extraPadding;
        int contentW = pageWidth * 2 - INNER_PADDING_X - extraPadding * 2;
        int contentH = pageHeight - INNER_PADDING_Y - extraPadding * 2;

        return new GuiLayout(contentX, contentY, contentW, contentH);
    }

    /**
     * Simple record class representing the usable content area of the GUI, excluding borders. Coordinates are in screen pixels.
     *
     * @param topLeftX  X coordinate of the top-left corner of the content area (inside the border)
     * @param topLeftY  Y coordinate of the top-left corner of the content area (inside the border)
     * @param guiWidth  Width of the content area spanning both pages (inside the border)
     * @param guiHeight Height of the content area (inside the border)
     */
    public record GuiLayout(int topLeftX, int topLeftY, int guiWidth, int guiHeight) {
    }
}
