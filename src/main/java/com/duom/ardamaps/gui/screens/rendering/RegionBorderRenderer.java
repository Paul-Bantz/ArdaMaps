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

package com.duom.ardamaps.gui.screens.rendering;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.data.map.region.RegionShape;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.map.rendering.GuiRenderStateAccess;
import com.duom.ardamaps.gui.map.rendering.RegionBorderRenderState;
import com.duom.ardamaps.gui.widgets.ExpansionAnimation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.*;

/**
 * Renders vector region borders on top of the map.
 */
public final class RegionBorderRenderer {

    /** Root border width in screen pixels. */
    private static final double ROOT_BORDER_WIDTH_PIXELS = 1.25;

    /** Non-root border width in screen pixels. */
    private static final double CHILD_BORDER_WIDTH_PIXELS = 1.3;

    /** Root border alpha. */
    private static final float ROOT_BORDER_ALPHA = 0.52f;

    /** Non-root border alpha before zoom fading. */
    private static final float CHILD_BORDER_ALPHA = 0.42f;

    /** Preferred non-root dot period in screen pixels. */
    private static final double CHILD_DOT_PERIOD_PIXELS = 4.5;

    /** Relative zoom bucket size used for mesh caching. */
    private static final double ZOOM_BUCKET_RATIO = 1.04;

    /** Cached dimension identifier. */
    private String cachedDimensionId;

    /** Cached zoom bucket. */
    private long cachedZoomBucket = Long.MIN_VALUE;

    /** Cached geometry last-update time. */
    private long cachedGeometryUpdate = Long.MIN_VALUE;

    /** Cached border mesh. */
    private RegionBorderRenderState.Vertex[] cachedVertices = new RegionBorderRenderState.Vertex[0];

    /**
     * Renders region borders for the current camera and exploration state.
     *
     * @param context     The GUI graphics extractor.
     * @param camera      The active map camera.
     * @param exploration The exploration state, or null.
     */
    public void render(GuiGraphicsExtractor context, MapCamera camera, @Nullable PlayerExploration exploration) {

        if (!ArdaMapsClient.CONFIG.isShowRegionBorders()) return;

        RegionGeometry geometry = ArdaMapsClient.CONFIG.getRegionGeometry(camera.getDimension().getId());
        if (geometry == null || geometry.lastUpdate() == null || geometry.regions().length == 0) return;

        boolean revealAll = ArdaMapsClient.CONFIG.isMapRevealAll();
        if (!revealAll && exploration == null) return;

        RegionBorderRenderState.Vertex[] vertices = meshFor(camera, geometry);
        if (vertices.length == 0) return;

        Identifier maskTexture = revealAll || exploration.getFogTextureId() == null
                ? ModConstants.FOG_MASK_REVEALED_TEXTURE
                : exploration.getFogTextureId();

        GuiRenderStateAccess.add(context, new RegionBorderRenderState(
                worldToScreenPose(camera),
                vertices,
                maskTexture,
                GuiRenderStateAccess.scissorArea(context)));
    }

    /**
     * Returns a cached or rebuilt border mesh.
     *
     * @param camera      The active map camera.
     * @param geometry    The region geometry.
     * @return The border mesh vertices.
     */
    private RegionBorderRenderState.Vertex[] meshFor(MapCamera camera, RegionGeometry geometry) {

        long geometryUpdate = geometry.lastUpdate().getTime();
        long zoomBucket = zoomBucket(camera.getVisualPixelsPerBlock());
        String dimensionId = geometry.dimensionId();

        if (Objects.equals(cachedDimensionId, dimensionId)
                && cachedZoomBucket == zoomBucket
                && cachedGeometryUpdate == geometryUpdate) {
            return cachedVertices;
        }

        cachedDimensionId = dimensionId;
        cachedZoomBucket = zoomBucket;
        cachedGeometryUpdate = geometryUpdate;
        cachedVertices = buildMesh(camera, geometry);
        return cachedVertices;
    }

    /**
     * Builds a CPU-expanded stroke mesh in world coordinates.
     *
     * @param camera      The active map camera.
     * @param geometry    The region geometry.
     * @return The border mesh vertices.
     */
    private static RegionBorderRenderState.Vertex[] buildMesh(MapCamera camera, RegionGeometry geometry) {

        double pixelsPerBlock = Math.max(0.001, camera.getVisualPixelsPerBlock());
        double zoomT = normalizedZoom(camera);
        FogUv fogUv = FogUv.from(camera.getDimension());
        ArrayList<RegionBorderRenderState.Vertex> vertices = new ArrayList<>();
        Set<EdgeKey> seenEdges = new HashSet<>();
        RegionShape[] shapes = geometry.regions().clone();
        Arrays.sort(shapes, Comparator.comparingInt(RegionShape::depth));

        for (RegionShape shape : shapes) {
            float alpha = borderAlpha(shape.depth(), zoomT);
            if (alpha <= 0.01F) continue;

            double width = shape.depth() == 0 ? ROOT_BORDER_WIDTH_PIXELS : CHILD_BORDER_WIDTH_PIXELS;
            double halfWidthWorld = width / pixelsPerBlock / 2.0;
            int color = withAlpha(alpha);

            for (int[] ring : shape.rings()) {
                addRing(vertices, seenEdges, ring, halfWidthWorld, pixelsPerBlock, shape.depth() == 0, color, fogUv);
            }
        }

        return vertices.toArray(new RegionBorderRenderState.Vertex[0]);
    }

    /**
     * Adds one ring's border segments to the mesh.
     *
     * @param vertices       The output vertex list.
     * @param seenEdges      The dedupe set.
     * @param ring           The interleaved x,z ring.
     * @param halfWidthWorld The stroke half-width in world units.
     * @param pixelsPerBlock The current visual pixels per block.
     * @param solid          Whether the ring should be solid.
     * @param color          The packed ARGB color.
     * @param fogUv          The fog UV coordinate calculator.
     */
    private static void addRing(ArrayList<RegionBorderRenderState.Vertex> vertices, Set<EdgeKey> seenEdges, int[] ring,
                                double halfWidthWorld, double pixelsPerBlock, boolean solid, int color,
                                FogUv fogUv) {

        if (ring == null || ring.length < 4) return;

        int vertexCount = ring.length / 2;
        Vec2d[] joins = buildJoins(ring, halfWidthWorld);
        float dotPeriod = solid ? 0.0F : snappedDotPeriod(ring, pixelsPerBlock);
        float dotRadius = (float) (halfWidthWorld * pixelsPerBlock);
        double arcLength = 0.0;

        for (int index = 0; index < vertexCount; index++) {
            int next = (index + 1) % vertexCount;
            int x0 = ring[index * 2];
            int z0 = ring[index * 2 + 1];
            int x1 = ring[next * 2];
            int z1 = ring[next * 2 + 1];

            if (x0 == x1 && z0 == z1) continue;

            double segmentPixels = Math.hypot(x1 - x0, z1 - z0) * pixelsPerBlock;
            double arcStart = arcLength;
            double arcEnd = arcLength + segmentPixels;
            arcLength = arcEnd;

            EdgeKey key = EdgeKey.of(x0, z0, x1, z1);
            if (!seenEdges.add(key)) continue;

            addSegment(vertices, x0, z0, x1, z1, joins[index], joins[next], (float) arcStart, (float) arcEnd,
                    dotPeriod, dotRadius, color, fogUv);
        }
    }

    /**
     * Computes a closed-ring dot period that lands evenly at the join.
     *
     * @param ring           The interleaved x,z ring.
     * @param pixelsPerBlock The current visual pixels per block.
     * @return The snapped dot period in screen pixels.
     */
    private static float snappedDotPeriod(int[] ring, double pixelsPerBlock) {

        double totalPixels = 0.0;
        int vertexCount = ring.length / 2;

        for (int index = 0; index < vertexCount; index++) {
            int next = (index + 1) % vertexCount;
            int x0 = ring[index * 2];
            int z0 = ring[index * 2 + 1];
            int x1 = ring[next * 2];
            int z1 = ring[next * 2 + 1];
            totalPixels += Math.hypot(x1 - x0, z1 - z0) * pixelsPerBlock;
        }

        if (totalPixels <= 0.0) return (float) CHILD_DOT_PERIOD_PIXELS;

        double dots = Math.max(1.0, Math.round(totalPixels / CHILD_DOT_PERIOD_PIXELS));
        return (float) (totalPixels / dots);
    }

    /**
     * Builds per-vertex join offsets for a closed ring.
     *
     * @param ring           The interleaved x,z ring.
     * @param halfWidthWorld The stroke half-width in world units.
     * @return The join offsets.
     */
    private static Vec2d[] buildJoins(int[] ring, double halfWidthWorld) {

        int vertexCount = ring.length / 2;
        Vec2d[] joins = new Vec2d[vertexCount];

        for (int index = 0; index < vertexCount; index++) {
            int previous = (index - 1 + vertexCount) % vertexCount;
            int next = (index + 1) % vertexCount;

            double px = ring[previous * 2];
            double pz = ring[previous * 2 + 1];
            double cx = ring[index * 2];
            double cz = ring[index * 2 + 1];
            double nx = ring[next * 2];
            double nz = ring[next * 2 + 1];

            joins[index] = joinOffset(px, pz, cx, cz, nx, nz, halfWidthWorld);
        }

        return joins;
    }

    /**
     * Computes a miter join offset with a bevel fallback for sharp turns.
     *
     * @param px             The previous vertex X.
     * @param pz             The previous vertex Z.
     * @param cx             The current vertex X.
     * @param cz             The current vertex Z.
     * @param nx             The next vertex X.
     * @param nz             The next vertex Z.
     * @param halfWidthWorld The stroke half-width in world units.
     * @return The join offset.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private static Vec2d joinOffset(double px, double pz, double cx, double cz, double nx, double nz, double halfWidthWorld) {

        double prevDx = cx - px;
        double prevDz = cz - pz;
        double nextDx = nx - cx;
        double nextDz = nz - cz;
        double prevLength = Math.hypot(prevDx, prevDz);
        double nextLength = Math.hypot(nextDx, nextDz);

        if (prevLength == 0.0 || nextLength == 0.0)
            return new Vec2d(0.0, halfWidthWorld);

        prevDx /= prevLength;
        prevDz /= prevLength;
        nextDx /= nextLength;
        nextDz /= nextLength;

        double tangentX = prevDx + nextDx;
        double tangentZ = prevDz + nextDz;
        double tangentLength = Math.hypot(tangentX, tangentZ);
        double nextNormalX = -nextDz;
        double nextNormalZ = nextDx;

        if (tangentLength == 0.0)
            return new Vec2d(nextNormalX * halfWidthWorld, nextNormalZ * halfWidthWorld);

        tangentX /= tangentLength;
        tangentZ /= tangentLength;
        double miterX = -tangentZ;
        double miterZ = tangentX;
        double denominator = miterX * nextNormalX + miterZ * nextNormalZ;

        if (Math.abs(denominator) < 0.1)
            return new Vec2d(nextNormalX * halfWidthWorld, nextNormalZ * halfWidthWorld);

        double miterLength = halfWidthWorld / denominator;
        double miterLimit = halfWidthWorld * 4.0;
        if (Math.abs(miterLength) > miterLimit)
            return new Vec2d(nextNormalX * halfWidthWorld, nextNormalZ * halfWidthWorld);

        return new Vec2d(miterX * miterLength, miterZ * miterLength);
    }

    /**
     * Adds one segment quad to the mesh.
     *
     * @param vertices       The output vertex list.
     * @param x0             The segment start X.
     * @param z0             The segment start Z.
     * @param x1             The segment end X.
     * @param z1             The segment end Z.
     * @param startJoin      The start join offset.
     * @param endJoin        The end join offset.
     * @param arcStart       The segment start arc length in pixels.
     * @param arcEnd         The segment end arc length in pixels.
     * @param dotPeriod      The dot period in screen pixels, or zero for solid.
     * @param dotRadius      The dot radius in screen pixels.
     * @param color          The packed ARGB color.
     * @param fogUv          The fog UV coordinate calculator.
     */
    private static void addSegment(ArrayList<RegionBorderRenderState.Vertex> vertices, int x0, int z0, int x1, int z1,
                                   Vec2d startJoin, Vec2d endJoin, float arcStart, float arcEnd, float dotPeriod,
                                   float dotRadius, int color, FogUv fogUv) {

        vertices.add(vertex(x0 + startJoin.x(), z0 + startJoin.y(), 1.0F, arcStart, dotPeriod, dotRadius, color, fogUv));
        vertices.add(vertex(x0 - startJoin.x(), z0 - startJoin.y(), -1.0F, arcStart, dotPeriod, dotRadius, color, fogUv));
        vertices.add(vertex(x1 - endJoin.x(), z1 - endJoin.y(), -1.0F, arcEnd, dotPeriod, dotRadius, color, fogUv));
        vertices.add(vertex(x1 + endJoin.x(), z1 + endJoin.y(), 1.0F, arcEnd, dotPeriod, dotRadius, color, fogUv));
    }

    /**
     * Creates a mesh vertex.
     *
     * @param x          The world X coordinate.
     * @param z          The world Z coordinate.
     * @param side       The normalized side coordinate.
     * @param arcLength  The dash arc length in screen pixels.
     * @param dotPeriod  The dot period in screen pixels, or zero for solid.
     * @param dotRadius  The dot radius in screen pixels.
     * @param color      The packed ARGB color.
     * @param fogUv      The fog UV coordinate calculator.
     * @return The mesh vertex.
     */
    private static RegionBorderRenderState.Vertex vertex(double x, double z, float side, float arcLength,
                                                         float dotPeriod, float dotRadius, int color, FogUv fogUv) {

        return new RegionBorderRenderState.Vertex((float) x, (float) z, fogUv.u(x), fogUv.v(z), side, arcLength,
                dotPeriod, dotRadius, color);
    }

    /**
     * Computes border alpha for a region depth and zoom.
     *
     * @param depth The region hierarchy depth.
     * @param zoomT The normalized zoom, where 1 is fully zoomed out.
     * @return The alpha multiplier.
     */
    private static float borderAlpha(int depth, double zoomT) {

        if (depth == 0) return ROOT_BORDER_ALPHA;

        double end = 0.94 - Math.min(depth - 1, 5) * 0.075;
        double start = end - 0.18;
        float fadeOut = ExpansionAnimation.smoothstep((float) ((zoomT - start) / (end - start)));
        return CHILD_BORDER_ALPHA * (1.0F - fadeOut);
    }

    /**
     * Applies a float alpha to an RGB color.
     *
     * @param alpha The alpha in [0, 1].
     * @return The packed ARGB color.
     */
    private static int withAlpha(float alpha) {

        int alphaByte = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return alphaByte << 24 | ModConstants.COLOR_LIGHT_BROWN & 0x00FFFFFF;
    }

    /**
     * Computes normalized log zoom, where 0 is identity-or-closer and 1 is minimum zoom.
     *
     * @param camera The active map camera.
     * @return The normalized zoom.
     */
    private static double normalizedZoom(MapCamera camera) {

        double identityRelativeScale = camera.identityRelativeScale();
        double minIdentityRelativeScale = camera.minIdentityRelativeScale();

        if (Double.isNaN(identityRelativeScale) || identityRelativeScale >= 1.0) return 0.0;
        if (Double.isNaN(minIdentityRelativeScale) || minIdentityRelativeScale >= 1.0) return 0.0;
        if (identityRelativeScale <= 0.0) return 1.0;

        double t = Math.log(identityRelativeScale) / Math.log(minIdentityRelativeScale);
        return Math.min(1.0, Math.max(0.0, t));
    }

    /**
     * Builds the affine world-to-screen pose matrix for a camera.
     *
     * @param camera The active map camera.
     * @return The world-to-screen pose.
     */
    private static Matrix3x2f worldToScreenPose(MapCamera camera) {

        double pixelsPerBlock = camera.getVisualPixelsPerBlock();
        Vec2d origin = camera.worldToScreenCoordinates(0.0, 0.0);
        return new Matrix3x2f()
                .translation((float) origin.x(), (float) origin.y())
                .scale((float) pixelsPerBlock, (float) pixelsPerBlock);
    }

    /**
     * Computes a stable zoom bucket.
     *
     * @param pixelsPerBlock The current visual pixels per block.
     * @return The zoom bucket.
     */
    private static long zoomBucket(double pixelsPerBlock) {

        return Math.round(Math.log(Math.max(0.001, pixelsPerBlock)) / Math.log(ZOOM_BUCKET_RATIO));
    }

    /**
     * Canonical undirected edge key.
     *
     * @param ax The first endpoint X.
     * @param az The first endpoint Z.
     * @param bx The second endpoint X.
     * @param bz The second endpoint Z.
     */
    private record EdgeKey(int ax, int az, int bx, int bz) {

        /**
         * Creates a direction-independent edge key.
         *
         * @param x0 The segment start X.
         * @param z0 The segment start Z.
         * @param x1 The segment end X.
         * @param z1 The segment end Z.
         * @return The canonical edge key.
         */
        private static EdgeKey of(int x0, int z0, int x1, int z1) {

            if (x0 < x1 || x0 == x1 && z0 <= z1) return new EdgeKey(x0, z0, x1, z1);
            return new EdgeKey(x1, z1, x0, z0);
        }
    }

    /**
     * Fog mask UV calculator for a dimension.
     *
     * @param xMin   The dimension minimum X.
     * @param zMin   The dimension minimum Z.
     * @param width  The dimension width.
     * @param height The dimension height.
     */
    private record FogUv(double xMin, double zMin, double width, double height) {

        /**
         * Builds a fog UV calculator from a dimension.
         *
         * @param dimension The map dimension.
         * @return The fog UV calculator.
         */
        private static FogUv from(com.duom.ardamaps.core.data.config.Dimension dimension) {

            return new FogUv(dimension.getXMin(), dimension.getZMin(), dimension.getWidth(), dimension.getHeight());
        }

        /**
         * Computes the mask U coordinate.
         *
         * @param x The world X coordinate.
         * @return The mask U coordinate.
         */
        private float u(double x) {

            return (float) ((x - xMin) / width);
        }

        /**
         * Computes the mask V coordinate.
         *
         * @param z The world Z coordinate.
         * @return The mask V coordinate.
         */
        private float v(double z) {

            return (float) ((z - zMin) / height);
        }
    }
}
