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
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * GUI render state for a single BlueMap tile shader pass.
 * UV extents exclude BlueMap's duplicated 1-pixel right/bottom overlap.
 */
public record BlueMapTileRenderState(
        Identifier texture,
        Matrix3x2f pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float sunlightStrength,
        float ambientLight,
        float lodScale,
        float texelSizeX,
        float uMax,
        float vMax,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    public BlueMapTileRenderState(
            Identifier texture,
            Matrix3x2f pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float sunlightStrength,
            float ambientLight,
            float lodScale,
            float texelSizeX,
            float uMax,
            float vMax,
            ScreenRectangle scissorArea
    ) {
        this(
                texture,
                pose,
                x0,
                y0,
                x1,
                y1,
                sunlightStrength,
                ambientLight,
                lodScale,
                texelSizeX,
                uMax,
                vMax,
                scissorArea,
                bounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {

        vertex(vertexConsumer, x0, y0, 0.0F, 0.0F);
        vertex(vertexConsumer, x0, y1, 0.0F, vMax);
        vertex(vertexConsumer, x1, y1, uMax, vMax);
        vertex(vertexConsumer, x1, y0, uMax, 0.0F);
    }

    @Override
    public RenderPipeline pipeline() {

        return BlueMapTileShader.blueMapTile();
    }

    @Override
    public TextureSetup textureSetup() {

        var textureView = Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView();
        return TextureSetup.singleTexture(textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    private void vertex(VertexConsumer vertexConsumer, float x, float y, float u, float v) {

        vertexConsumer.addVertexWith2DPose(pose, x, y).setUv(u, v);
        CustomVertexAttributes.set(
                vertexConsumer,
                ModVertexFormats.TILE_PARAMS,
                sunlightStrength,
                ambientLight,
                lodScale,
                texelSizeX);
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
}
