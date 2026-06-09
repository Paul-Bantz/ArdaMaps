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
 * Utility class for managing the fog of war shader.
 */
public class FogOfWarShader {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(FogOfWarShader.class);
    
    /** The fog of war shader program. */
    private static ShaderProgram FOG_OF_WAR;

    /**
     * Get the fog of war shader program.
     *
     * @return The fog of war shader program.
     */
    public static ShaderProgram fogOfWar() {
        return FOG_OF_WAR;
    }

    /**
     * Initialize the fog of war shader.
     *
     * @param resourceManager The resource manager.
     */
    public static void load(ResourceManager resourceManager) {

        try {
            FOG_OF_WAR = new ShaderProgram(resourceManager, "fog_of_war", VertexFormats.POSITION_TEXTURE);
        } catch (IOException e) {
            LOGGER.error("Unable to load fog_of_war shader", e);
        }
    }

    /**
     * Set the texture scale uniform for the fog of war shader.
     *
     * @param scaleX The scale in the X direction.
     * @param scaleY The scale in the Y direction.
     */
    public static void setTextureScale(float scaleX, float scaleY) {

        RenderSystem.getShader();

        if (FOG_OF_WAR != null) {

            var fogScaleUniform = FOG_OF_WAR.getUniform("FogTexScale");

            if (fogScaleUniform != null) {
                fogScaleUniform.set(scaleX, scaleY);
            }
        }
    }

    /**
     * Set the zoom centre uniform for the fog of war shader.
     *
     * @param centerX The centre in the X direction.
     * @param centerY The centre in the Y direction.
     */
    public static void setZoomCenter(float centerX, float centerY) {

        RenderSystem.getShader();

        if (FOG_OF_WAR != null) {

            var paperScaleUniform = FOG_OF_WAR.getUniform("ZoomCenter");

            if (paperScaleUniform != null) {
                paperScaleUniform.set(centerX, centerY);
            }
        }
    }
}