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

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Builds region lookup textures from first-party region descriptors and world-space polygons.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RegionLookupBuilder {

    /** Maximum size (in pixels) of either dimension of the region lookup texture. */
    public static final int REGION_LOOKUP_MAX_DIMENSION = 512;

    /**
     * Builds a region lookup texture.
     *
     * @param dimension      the dimension definition containing world bounds
     * @param regions        ordered root region descriptors
     * @param regionPolygons polygons for each region, in the same order as {@code regions}
     * @return the generated lookup texture
     */
    public static RegionLookupTexture build(Dimension dimension, List<Region> regions, List<List<List<Vec2d>>> regionPolygons) {
        double worldW = dimension.getWidth();
        double worldH = dimension.getHeight();
        double scale = REGION_LOOKUP_MAX_DIMENSION / Math.max(worldW, worldH);

        int texW = Math.max(1, (int) Math.round(worldW * scale));
        int texH = Math.max(1, (int) Math.round(worldH * scale));

        double scaleX = (double) texW / worldW;
        double scaleZ = (double) texH / worldH;

        byte[] pixels = new byte[texW * texH];

        for (int idx = 0; idx < regions.size() && idx < regionPolygons.size(); idx++) {
            byte color = (byte) (idx + 1);

            for (List<Vec2d> polygon : regionPolygons.get(idx)) {
                List<Vec2d> vertices = new ArrayList<>();

                for (Vec2d vertex : polygon) {
                    double px = (vertex.x() - dimension.getXMin()) * scaleX;
                    double pz = (vertex.y() - dimension.getZMin()) * scaleZ;
                    vertices.add(new Vec2d(px, pz));
                }

                rasterizePolygon(pixels, texW, texH, vertices, color);
            }
        }

        return new RegionLookupTexture(pixels, regions.toArray(new Region[0]), texW, texH, dimension.getId(), new Date());
    }

    /**
     * Rasterize a single polygon into {@code pixels} using a Scanline fill.
     */
    static void rasterizePolygon(byte[] pixels, int texW, int texH, List<Vec2d> vertices, byte color) {
        if (vertices.size() < 3) return;

        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Vec2d v : vertices) {
            if (v.y() < minY) minY = v.y();
            if (v.y() > maxY) maxY = v.y();
        }

        int yStart = Math.max(0, (int) Math.ceil(minY));
        int yEnd = Math.min(texH - 1, (int) Math.floor(maxY));
        int n = vertices.size();

        for (int y = yStart; y <= yEnd; y++) {
            List<Double> intersections = getIntersectionsWithScanline(vertices, y, n);
            intersections.sort(Double::compareTo);

            for (int i = 0; i + 1 < intersections.size(); i += 2) {
                int xStart = Math.max(0, (int) Math.ceil(intersections.get(i)));
                int xEnd = Math.min(texW - 1, (int) Math.floor(intersections.get(i + 1)));
                for (int x = xStart; x <= xEnd; x++) {
                    pixels[y * texW + x] = color;
                }
            }
        }
    }

    /**
     * Computes the X coordinates of the intersections between a polygon and a horizontal scanline at Y.
     */
    static @NonNull List<Double> getIntersectionsWithScanline(List<Vec2d> vertices, int y, int n) {
        double scanY = y + 0.5;
        List<Double> intersections = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Vec2d a = vertices.get(i);
            Vec2d b = vertices.get((i + 1) % n);

            double ay = a.y(), by = b.y();
            if (ay == by) continue;
            if ((scanY < Math.min(ay, by)) || (scanY >= Math.max(ay, by))) continue;

            double t = (scanY - ay) / (by - ay);
            intersections.add(a.x() + t * (b.x() - a.x()));
        }

        return intersections;
    }
}
