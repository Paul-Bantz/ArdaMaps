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
import org.jspecify.annotations.NonNull;

/**
 * GUI render state for region border stroke meshes.
 *
 * @param pose        The world-to-screen pose matrix.
 * @param vertices    The mesh vertices in world coordinates.
 * @param maskTexture The exploration mask texture.
 * @param scissorArea The active scissor area.
 * @param bounds      The element bounds.
 */
public record RegionBorderRenderState(Matrix3x2f pose,
                                      Vertex[] vertices,
                                      Identifier maskTexture,
                                      ScreenRectangle scissorArea,
                                      ScreenRectangle bounds) implements GuiElementRenderState {

    /**
     * Constructs a render state for a region border mesh.
     *
     * @param pose        The world-to-screen pose matrix.
     * @param vertices    The mesh vertices in world coordinates.
     * @param maskTexture The exploration mask texture.
     * @param scissorArea The active scissor area.
     */
    public RegionBorderRenderState(Matrix3x2f pose, Vertex[] vertices, Identifier maskTexture,
                                   ScreenRectangle scissorArea) {
        this(pose, vertices, maskTexture, scissorArea, scissorArea);
    }

    /**
     * Builds the border mesh vertices for rendering.
     *
     * @param vertexConsumer The vertex consumer to append vertices to.
     */
    @Override
    public void buildVertices(@NonNull VertexConsumer vertexConsumer) {

        for (Vertex vertex : vertices) {
            vertexConsumer.addVertexWith2DPose(pose, vertex.x(), vertex.z())
                    .setColor(vertex.color())
                    .setUv(vertex.fogU(), vertex.fogV());
            CustomVertexAttributes.set(vertexConsumer, ModVertexFormats.BORDER_SIDE, vertex.side());
            CustomVertexAttributes.set(vertexConsumer, ModVertexFormats.BORDER_DASH, vertex.arcLength(),
                    vertex.dotPeriod(), vertex.dotRadius());
        }
    }

    /**
     * Returns the render pipeline.
     *
     * @return The region border pipeline.
     */
    @Override
    public @NonNull RenderPipeline pipeline() {

        return RegionBorderShader.regionBorder();
    }

    /**
     * Returns the texture setup.
     *
     * @return The mask texture setup.
     */
    @Override
    public @NonNull TextureSetup textureSetup() {

        var textureManager = Minecraft.getInstance().getTextureManager();
        var maskView = textureManager.getTexture(maskTexture).getTextureView();
        return TextureSetup.singleTexture(maskView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }

    /**
     * A single world-space border mesh vertex.
     *
     * @param x          The world X coordinate.
     * @param z          The world Z coordinate.
     * @param fogU       The fog mask U coordinate.
     * @param fogV       The fog mask V coordinate.
     * @param side       The normalized side coordinate.
     * @param arcLength  The border arc length in screen pixels.
     * @param dotPeriod  The dot period in screen pixels, or zero for solid.
     * @param dotRadius  The dot radius in screen pixels.
     * @param color      The packed ARGB color.
     */
    public record Vertex(float x, float z, float fogU, float fogV, float side, float arcLength, float dotPeriod,
                         float dotRadius, int color) {
    }
}
