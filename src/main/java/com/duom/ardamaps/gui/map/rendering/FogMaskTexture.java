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

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * RED8 GPU texture backed by a one-byte-per-cell fog mask buffer.
 */
@Environment(EnvType.CLIENT)
public class FogMaskTexture extends AbstractTexture {

    /** Texture usage flags needed for shader sampling and CPU uploads. */
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;

    /** Texture width in cells. */
    private final int width;

    /** Texture height in cells. */
    private final int height;

    /** CPU-side mask bytes in row-major order. */
    private @Nullable ByteBuffer buffer;

    /** Minimum dirty row to upload. */
    private int dirtyYMin;

    /** Maximum dirty row to upload. */
    private int dirtyYMax;

    /**
     * Creates a RED8 fog mask texture.
     *
     * @param name   Texture debug-name supplier.
     * @param width  Texture width in cells.
     * @param height Texture height in cells.
     */
    public FogMaskTexture(Supplier<String> name, int width, int height) {

        this.width = width;
        this.height = height;
        this.buffer = MemoryUtil.memCalloc(width * height);

        this.texture = RenderSystem.getDevice()
                .createTexture(name, USAGE, TextureFormat.RED8, width, height, 1, 1);
        this.textureView = RenderSystem.getDevice().createTextureView(texture);
        this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);

        dirtyYMin = 0;
        dirtyYMax = height - 1;
        upload();
    }

    /**
     * Fills all mask texels and marks the whole texture dirty.
     *
     * @param maskValue RED8 mask byte.
     */
    public void fill(byte maskValue) {

        ByteBuffer target = buffer;
        if (target == null) return;

        MemoryUtil.memSet(target, maskValue);
        dirtyYMin = 0;
        dirtyYMax = height - 1;
    }

    /**
     * Sets one mask texel and marks its row dirty.
     *
     * @param x         Cell X coordinate.
     * @param y         Cell Y coordinate.
     * @param maskValue RED8 mask byte.
     */
    public void setPixel(int x, int y, byte maskValue) {

        ByteBuffer target = buffer;
        if (target == null) return;

        target.put(y * width + x, maskValue);
        dirtyYMin = Math.min(dirtyYMin, y);
        dirtyYMax = Math.max(dirtyYMax, y);
    }

    /**
     * Uploads the dirty row span to the GPU texture.
     */
    public void upload() {

        ByteBuffer source = buffer;
        if (dirtyYMin > dirtyYMax || source == null || texture == null) return;

        ByteBuffer uploadBuffer = source.duplicate();
        uploadBuffer.position(dirtyYMin * width);
        uploadBuffer.limit((dirtyYMax + 1) * width);

        RenderSystem.getDevice()
                .createCommandEncoder()
                .writeToTexture(texture, uploadBuffer.slice(), NativeImage.Format.LUMINANCE, 0, 0, 0, dirtyYMin, width, dirtyYMax - dirtyYMin + 1);

        dirtyYMin = Integer.MAX_VALUE;
        dirtyYMax = Integer.MIN_VALUE;
    }

    /**
     * Releases GPU and native CPU-buffer resources.
     */
    @Override
    public void close() {

        super.close();

        ByteBuffer released = buffer;
        buffer = null;
        if (released != null) MemoryUtil.memFree(released);
    }
}
