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

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Utility class for managing the BlueMap tile shader.
 * <br/>The shader samples the colour from the top half of a BlueMap PNG tile and
 * applies height-gradient shading and block-light using the metadata stored in
 * the bottom half of the same image.
 */
public class BlueMapTileShader {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(BlueMapTileShader.class);
    
    /** The BlueMap tile shader program. */
    private static ShaderProgram BLUEMAP_TILE;

    /**
     * Returns the BlueMap tile shader program.
     * Intended to be used as a method reference with {@link RenderSystem#setShader}.
     *
     * @return The shader program, or {@code null} if not yet loaded.
     */
    public static ShaderProgram blueMapTile() {
        return BLUEMAP_TILE;
    }

    /**
     * Loads (or reloads) the BlueMap tile shader from the given resource manager.
     * Should be called from a {@link net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener}.
     *
     * @param resourceManager The resource manager to load the shader from.
     */
    public static void load(ResourceManager resourceManager) {

        try {
            BLUEMAP_TILE = new ShaderProgram(resourceManager, "bluemap_tile", VertexFormats.POSITION_TEXTURE);
        } catch (IOException e) {
            LOGGER.error("Unable to load bluemap_tile shader", e);
        }
    }

    /**
     * Sets the {@code SunlightStrength} uniform (0.0 = fully block-lit, 1.0 = full sun).
     *
     * @param value Sunlight strength in [0, 1].
     */
    public static void setSunlightStrength(float value) {
        setUniform1f("SunlightStrength", value);
    }

    private static void setUniform1f(String name, float value) {

        RenderSystem.getShader();

        if (BLUEMAP_TILE != null) {
            var uniform = BLUEMAP_TILE.getUniform(name);
            if (uniform != null) {
                uniform.set(value);
            }
        }
    }

    /**
     * Sets the {@code AmbientLight} uniform (minimum brightness when block light = 0, 0.0–1.0).
     *
     * @param value Ambient light level in [0, 1].
     */
    public static void setAmbientLight(float value) {
        setUniform1f("AmbientLight", value);
    }

    /**
     * Sets the {@code LodScale} uniform used to normalize height-gradient shading across LOD levels.
     * Typically {@code lodFactor^(lod - 1)}.
     *
     * @param value LOD scale factor (1.0 at finest LOD).
     */
    public static void setLodScale(float value) {
        setUniform1f("LodScale", value);
    }

    // --- Internal helpers ---

    /**
     * Sets the {@code TexelSize} uniform — one texel step in normalized UV space.
     * Should be {@code (1/imageSize, 1/(imageSize*2))} where {@code imageSize = renderSize + 1}.
     *
     * @param x UV step in the horizontal (U) direction.
     * @param y UV step in the vertical (V) direction.
     */
    public static void setTexelSize(float x, float y) {

        RenderSystem.getShader();

        if (BLUEMAP_TILE != null) {
            var uniform = BLUEMAP_TILE.getUniform("TexelSize");
            if (uniform != null) {
                uniform.set(x, y);
            }
        }
    }
}
