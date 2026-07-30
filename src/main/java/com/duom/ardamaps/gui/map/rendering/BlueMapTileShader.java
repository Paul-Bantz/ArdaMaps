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

import com.duom.ardamaps.gui.ModConstants;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Static RenderPipeline declaration for BlueMap tile shading.
 */
public final class BlueMapTileShader {

    /** Pipeline that shades BlueMap colour/metadata tiles. */
    public static final RenderPipeline BLUEMAP_TILE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(ModConstants.modId("pipeline/bluemap_tile"))
            .withVertexShader(ModConstants.modId("core/bluemap_tile"))
            .withFragmentShader(ModConstants.modId("core/bluemap_tile"))
            .withSampler("Sampler0")
            .withUniform("BlueMapTileUniform", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .build());

    private static float sunlightStrength = 0.6F;
    private static float ambientLight = 0.35F;
    private static float lodScale = 1.0F;
    private static float texelSizeX = 1.0F;
    private static float texelSizeY = 1.0F;

    private BlueMapTileShader() {
    }

    public static RenderPipeline blueMapTile() {
        return BLUEMAP_TILE;
    }

    public static boolean isLoaded() {
        return true;
    }

    public static void load(@SuppressWarnings("unused") ResourceManager resourceManager) {
        // RenderPipelines are static declarations in 26.x; resources are reloaded by Minecraft's shader pipeline.
    }

    public static void setSunlightStrength(float value) {
        sunlightStrength = value;
    }

    public static void setAmbientLight(float value) {
        ambientLight = value;
    }

    public static void setLodScale(float value) {
        lodScale = value;
    }

    public static void setTexelSize(float x, float y) {
        texelSizeX = x;
        texelSizeY = y;
    }

    public static float sunlightStrength() {
        return sunlightStrength;
    }

    public static float ambientLight() {
        return ambientLight;
    }

    public static float lodScale() {
        return lodScale;
    }

    public static float texelSizeX() {
        return texelSizeX;
    }

    public static float texelSizeY() {
        return texelSizeY;
    }
}
