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

package com.duom.ardamaps.core.data.map;

import com.duom.ardamaps.core.data.config.Dimension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Result of {@link com.duom.ardamaps.core.consumers.ArdaRegionsHook#generateRegionLookup}.
 *
 * <p>Each element of {@code pixels} holds a 1-based index into {@code regions}
 * (0 = no region). With fewer than 128 regions a {@code byte[]} is 4× smaller
 * than the previous {@code int[]} representation and fits entirely in L1/L2 cache.</p>
 *
 * @param pixels     Per-pixel region index in row-major order (row = Z axis, column = X axis).
 *                   Value 0 means "no region". Value {@code k > 0} maps to {@code regions[k-1]}.
 * @param regions    Ordered array of root regions; index {@code i} corresponds to pixel value {@code i+1}.
 * @param texWidth   Width  of the texture in pixels.
 * @param texHeight  Height of the texture in pixels.
 * @param dimensionId ID of the dimension this texture corresponds to
 * @param lastUpdate Timestamp of the last update of the region data, used for cache invalidation.
 */
public record RegionLookupTexture(byte[] pixels,
                                  Region[] regions,
                                  int texWidth,
                                  int texHeight,
                                  String dimensionId,
                                  Date lastUpdate) implements Serializable {

    /** A default empty texture instance, used when no data is available or an error occurs. */
    public static final RegionLookupTexture DEFAULT = new RegionLookupTexture(new byte[0], new Region[0], 0, 0, "minecraft:overworld", null);

    /**
     * Returns the name of the region at the given world position, or {@code null}
     * if the position is outside the world bounds or not covered by any region.
     *
     * <p>This is an O(1), allocation-free hot-path safe to call every render frame.</p>
     *
     * @param dimension The dimension to query.
     * @param worldX World X coordinate.
     * @param worldZ World Z coordinate.
     * @return The region name, or {@code null}.
     */
    public String getRegionAt(Dimension dimension, double worldX, double worldZ) {

        if (texWidth == 0 || texHeight == 0 || pixels.length == 0) return null;

        if (dimension == null) return null;

        if (!Objects.equals(dimension.getId(), dimensionId)) return null;

        int texX = (int) ((worldX - dimension.getXMin()) / (double) dimension.getWidth()  * texWidth);
        int texZ = (int) ((worldZ - dimension.getZMin()) / (double) dimension.getHeight() * texHeight);

        if (texX < 0 || texX >= texWidth || texZ < 0 || texZ >= texHeight) return null;

        // unsigned byte - 0..255
        int idx = pixels[texZ * texWidth + texX] & 0xFF;
        if (idx == 0 || idx > regions.length) return null;

        return regions[idx - 1].name();
    }

    /**
     * DEBUG - writes the lookup texture to a PNG file on disk.
     * Each distinct region gets a unique hue; unpainted pixels are black.
     *
     * @param outputFile The file to write the PNG to (e.g. {@code new File("region_lookup.png")}).
     * @throws IOException if the image cannot be written.
     */
    @SuppressWarnings("unused")
    public void debugSaveToFile(File outputFile) throws IOException {

        // Pre-build a palette: index 0 - black, 1..N - evenly spaced hues
        int[] palette = new int[regions.length + 1];
        palette[0] = 0xFF000000;
        for (int i = 0; i < regions.length; i++) {
            float hue = (float) i / regions.length;
            palette[i + 1] = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.9f, 0.9f) & 0x00FFFFFF);
        }

        int[] argbPixels = new int[texWidth * texHeight];
        for (int i = 0; i < pixels.length; i++) {
            int idx = pixels[i] & 0xFF;
            argbPixels[i] = idx < palette.length ? palette[idx] : 0xFF000000;
        }

        BufferedImage image = new BufferedImage(texWidth, texHeight, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, texWidth, texHeight, argbPixels, 0, texWidth);
        ImageIO.write(image, "PNG", outputFile);
    }
}
