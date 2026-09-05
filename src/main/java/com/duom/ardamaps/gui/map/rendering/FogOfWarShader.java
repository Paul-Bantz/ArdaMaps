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
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Static RenderPipeline declaration for the fog-of-war pass.
 */
public final class FogOfWarShader {

    /** Pipeline used to combine map imagery with the exploration mask. */
    public static final RenderPipeline FOG_OF_WAR = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(ModConstants.modId("pipeline/fog_of_war"))
            .withVertexShader(ModConstants.modId("core/fog_of_war"))
            .withFragmentShader(ModConstants.modId("core/fog_of_war"))
            .withSampler("Sampler1")
            .withVertexFormat(ModVertexFormats.POSITION_TEX_PAPER, VertexFormat.Mode.QUADS)
            .build());

    private FogOfWarShader() {
    }

    public static RenderPipeline fogOfWar() {
        return FOG_OF_WAR;
    }
}
