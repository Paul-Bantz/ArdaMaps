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
 * GUI render state for the fog-of-war parchment/mask shader pass.
 */
public record FogOfWarRenderState(
        Identifier paperTexture,
        Identifier maskTexture,
        Matrix3x2f pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float paperU0,
        float paperV0,
        float paperU1,
        float paperV1,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    public FogOfWarRenderState(
            Identifier paperTexture,
            Identifier maskTexture,
            Matrix3x2f pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float paperU0,
            float paperV0,
            float paperU1,
            float paperV1,
            ScreenRectangle scissorArea
    ) {
        this(
                paperTexture,
                maskTexture,
                pose,
                x0,
                y0,
                x1,
                y1,
                paperU0,
                paperV0,
                paperU1,
                paperV1,
                scissorArea,
                bounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {

        vertex(vertexConsumer, x0, y0, 0.0F, 0.0F, paperU0, paperV0);
        vertex(vertexConsumer, x0, y1, 0.0F, 1.0F, paperU0, paperV1);
        vertex(vertexConsumer, x1, y1, 1.0F, 1.0F, paperU1, paperV1);
        vertex(vertexConsumer, x1, y0, 1.0F, 0.0F, paperU1, paperV0);
    }

    @Override
    public RenderPipeline pipeline() {

        return FogOfWarShader.fogOfWar();
    }

    @Override
    public TextureSetup textureSetup() {

        var textureManager = Minecraft.getInstance().getTextureManager();
        var paperView = textureManager.getTexture(paperTexture).getTextureView();
        var maskView = textureManager.getTexture(maskTexture).getTextureView();
        var samplerCache = RenderSystem.getSamplerCache();

        return TextureSetup.doubleTexture(
                paperView,
                samplerCache.getRepeat(FilterMode.LINEAR),
                maskView,
                samplerCache.getClampToEdge(FilterMode.LINEAR));
    }

    private void vertex(VertexConsumer vertexConsumer, float x, float y, float u, float v, float paperU, float paperV) {

        vertexConsumer.addVertexWith2DPose(pose, x, y).setUv(u, v);
        CustomVertexAttributes.set(vertexConsumer, ModVertexFormats.UV_PAPER, paperU, paperV);
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
