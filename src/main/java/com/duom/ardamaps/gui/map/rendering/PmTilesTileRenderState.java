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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;

/**
 * GUI render state for a single PMTiles texture quad with floating-point screen bounds.
 */
public record PmTilesTileRenderState(
        Identifier texture,
        Matrix3x2f pose,
        float x0,
        float y0,
        float x1,
        float y1,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    /**
     * Constructs a render state for a single PMTiles texture quad, computing final bounds.
     *
     * @param texture The texture identifier to render.
     * @param pose The 2D transformation matrix for the quad.
     * @param x0 Left edge in screen pixels.
     * @param y0 Top edge in screen pixels.
     * @param x1 Right edge in screen pixels.
     * @param y1 Bottom edge in screen pixels.
     * @param scissorArea The scissor rectangle to apply, or null for no clipping.
     */
    public PmTilesTileRenderState(
            Identifier texture,
            Matrix3x2f pose,
            float x0,
            float y0,
            float x1,
            float y1,
            ScreenRectangle scissorArea
    ) {
        this(texture, pose, x0, y0, x1, y1, scissorArea, bounds(x0, y0, x1, y1, pose, scissorArea));
    }

    private static ScreenRectangle bounds(
            float x0,
            float y0,
            float x1,
            float y1,
            Matrix3x2f pose,
            ScreenRectangle scissorArea
    ) {
        ScreenRectangle transformed = new ScreenRectangle(
                (int) Math.floor(x0),
                (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0),
                (int) Math.ceil(y1 - y0))
                .transformMaxBounds(pose);

        return scissorArea == null ? transformed : scissorArea.intersection(transformed);
    }

    /**
     * Builds the four vertices of the texture quad for rendering.
     *
     * @param vertexConsumer The vertex consumer to append vertices to.
     */
    @Override
    public void buildVertices(@NonNull VertexConsumer vertexConsumer) {

        vertex(vertexConsumer, x0, y0, 0.0F, 0.0F);
        vertex(vertexConsumer, x0, y1, 0.0F, 1.0F);
        vertex(vertexConsumer, x1, y1, 1.0F, 1.0F);
        vertex(vertexConsumer, x1, y0, 1.0F, 0.0F);
    }

    private void vertex(VertexConsumer vertexConsumer, float x, float y, float u, float v) {

        vertexConsumer.addVertexWith2DPose(pose, x, y).setUv(u, v).setColor(-1);
    }

    /**
     * Returns the render pipeline for textured GUI elements.
     *
     * @return The GUI textured render pipeline.
     */
    @Override
    public @NonNull RenderPipeline pipeline() {

        return RenderPipelines.GUI_TEXTURED;
    }

    /**
     * Returns the texture setup with nearest-neighbor filtering and edge clamping.
     *
     * @return A texture setup using the tile's texture and nearest-neighbor sampling.
     */
    @Override
    public @NonNull TextureSetup textureSetup() {

        var textureView = Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView();
        return TextureSetup.singleTexture(textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }
}
