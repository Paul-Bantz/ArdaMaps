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
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.core.data.trail.PlayerTrail;
import com.duom.ardamaps.core.data.trail.TrailSegment;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.map.rendering.GuiRenderStateAccess;
import com.duom.ardamaps.gui.map.rendering.RegionBorderRenderState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders the player's recorded movement trail on top of the map.
 */
public final class TrailRenderer {

    /** Travel path width in screen pixels. */
    private static final double TRAIL_WIDTH_PIXELS = 1.6;

    /** Gap endpoint tick length in screen pixels. */
    private static final double GAP_TICK_LENGTH_PIXELS = 6.0;

    /** Alpha applied to the travel path color. */
    private static final float TRAIL_ALPHA = 0.8F;

    /** Base screen-space decimation distance in pixels. */
    private static final double DECIMATE_EPSILON_PIXELS = 1.2;

    /** Maximum smoothed points retained in the render mesh build. */
    private static final int MAX_SMOOTHED_POINTS = 40_000;

    /** Number of Chaikin smoothing passes. */
    private static final int CHAIKIN_ITERATIONS = 3;

    /** Relative zoom bucket size used for mesh caching. */
    private static final double ZOOM_BUCKET_RATIO = 1.04;

    /** Cached dimension identifier. */
    private String cachedDimensionId;

    /** Cached zoom bucket. */
    private long cachedZoomBucket = Long.MIN_VALUE;

    /** Cached trail point count. */
    private int cachedPointCount = -1;

    /** Cached trail segment count. */
    private int cachedSegmentCount = -1;

    /** Cached pending-point state. */
    private boolean cachedHasPending;

    /** Cached pending X coordinate. */
    private int cachedPendingX;

    /** Cached pending Z coordinate. */
    private int cachedPendingZ;

    /** Cached trail mesh. */
    private RegionBorderRenderState.Vertex[] cachedVertices = new RegionBorderRenderState.Vertex[0];

    /**
     * Renders the movement trail for the current camera dimension.
     *
     * @param context The GUI graphics extractor.
     * @param camera  The active map camera.
     */
    public void render(GuiGraphicsExtractor context, MapCamera camera) {

        PlayerTrail trail = ArdaMapsClient.CONFIG.getClientProgress().trail(camera.getDimension().getId(), false);
        if (trail == null || trail.segments().isEmpty()) return;

        RegionBorderRenderState.Vertex[] vertices = meshFor(camera, trail);
        if (vertices.length == 0) return;

        GuiRenderStateAccess.add(context, new RegionBorderRenderState(
                worldToScreenPose(camera),
                vertices,
                ModConstants.FOG_MASK_REVEALED_TEXTURE,
                GuiRenderStateAccess.scissorArea(context)));
    }

    /**
     * Returns a cached or rebuilt trail mesh.
     *
     * @param camera The active map camera.
     * @param trail  The player trail.
     * @return The trail mesh vertices.
     */
    private RegionBorderRenderState.Vertex[] meshFor(MapCamera camera, PlayerTrail trail) {

        long zoomBucket = zoomBucket(camera.getVisualPixelsPerBlock());
        String dimensionId = camera.getDimension().getId();
        boolean hasPending = trail.hasPendingPoint();
        int pendingX = hasPending ? trail.pendingX() : 0;
        int pendingZ = hasPending ? trail.pendingZ() : 0;

        if (Objects.equals(cachedDimensionId, dimensionId)
                && cachedZoomBucket == zoomBucket
                && cachedPointCount == trail.pointCount()
                && cachedSegmentCount == trail.segmentCount()
                && cachedHasPending == hasPending
                && cachedPendingX == pendingX
                && cachedPendingZ == pendingZ) {
            return cachedVertices;
        }

        cachedDimensionId = dimensionId;
        cachedZoomBucket = zoomBucket;
        cachedPointCount = trail.pointCount();
        cachedSegmentCount = trail.segmentCount();
        cachedHasPending = hasPending;
        cachedPendingX = pendingX;
        cachedPendingZ = pendingZ;
        cachedVertices = buildMesh(camera, trail);
        return cachedVertices;
    }

    /**
     * Builds a CPU-expanded stroke mesh in world coordinates.
     *
     * @param camera The active map camera.
     * @param trail  The player trail.
     * @return The trail mesh vertices.
     */
    private static RegionBorderRenderState.Vertex[] buildMesh(MapCamera camera, PlayerTrail trail) {

        double pixelsPerBlock = Math.max(0.001, camera.getVisualPixelsPerBlock());
        double halfWidthWorld = TRAIL_WIDTH_PIXELS / pixelsPerBlock / 2.0;
        double halfTickLengthWorld = GAP_TICK_LENGTH_PIXELS / pixelsPerBlock / 2.0;
        int color = withAlpha();
        FogUv fogUv = FogUv.from(camera.getDimension());
        ArrayList<Polyline> polylines = decimatedPolylines(trail, pixelsPerBlock);
        ArrayList<RegionBorderRenderState.Vertex> vertices = new ArrayList<>();

        for (Polyline polyline : polylines) {
            List<Point> smoothed = chaikin(polyline.points(), CHAIKIN_ITERATIONS);
            addPolyline(vertices, smoothed, halfWidthWorld, color, fogUv);
            if (polyline.gapStart() && smoothed.size() >= 2) {
                Point start = smoothed.getFirst();
                Point next = smoothed.get(1);
                addTick(vertices, start, next.x() - start.x(), next.z() - start.z(), halfTickLengthWorld,
                        halfWidthWorld, color, fogUv);
            }
            if (polyline.gapEnd() && smoothed.size() >= 2) {
                Point end = smoothed.getLast();
                Point previous = smoothed.get(smoothed.size() - 2);
                addTick(vertices, end, end.x() - previous.x(), end.z() - previous.z(), halfTickLengthWorld,
                        halfWidthWorld, color, fogUv);
            }
        }

        return vertices.toArray(new RegionBorderRenderState.Vertex[0]);
    }

    /**
     * Copies and decimates all trail segments within the smoothed-point budget.
     *
     * @param trail          The player trail.
     * @param pixelsPerBlock The current visual pixels per block.
     * @return Decimated polylines.
     */
    static ArrayList<Polyline> decimatedPolylines(PlayerTrail trail, double pixelsPerBlock) {

        double epsilon = DECIMATE_EPSILON_PIXELS;
        ArrayList<Polyline> polylines = decimatedPolylines(trail, pixelsPerBlock, epsilon);
        int smoothedPointCount = smoothedPointCount(polylines);

        if (smoothedPointCount <= MAX_SMOOTHED_POINTS) return polylines;

        double scale = (double) smoothedPointCount / MAX_SMOOTHED_POINTS;
        epsilon *= Math.max(1.0, scale);
        return decimatedPolylines(trail, pixelsPerBlock, epsilon);
    }

    /**
     * Copies and decimates all trail segments with a fixed screen-space epsilon.
     *
     * @param trail          The player trail.
     * @param pixelsPerBlock The current visual pixels per block.
     * @param epsilonPixels  The minimum retained point distance in screen pixels.
     * @return Decimated polylines.
     */
    private static ArrayList<Polyline> decimatedPolylines(PlayerTrail trail, double pixelsPerBlock,
                                                          double epsilonPixels) {

        ArrayList<Polyline> polylines = new ArrayList<>();
        List<TrailSegment> segments = trail.segments();

        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            TrailSegment segment = segments.get(segmentIndex);
            ArrayList<Point> points = points(segment, trail, segmentIndex == segments.size() - 1);
            ArrayList<Point> decimated = decimate(points, epsilonPixels / pixelsPerBlock);
            if (decimated.size() >= 2) polylines.add(new Polyline(decimated, segment.startsAtGap(),
                    segment.endsAtGap()));
        }

        return polylines;
    }

    /**
     * Copies segment coordinates and appends the live pending point when needed.
     *
     * @param segment     The trail segment.
     * @param trail       The owning player trail.
     * @param lastSegment Whether this is the newest segment.
     * @return Copied points.
     */
    private static ArrayList<Point> points(TrailSegment segment, PlayerTrail trail, boolean lastSegment) {

        ArrayList<Point> points = new ArrayList<>(segment.size() + 1);
        for (int index = 0; index < segment.size(); index++) {
            points.add(new Point(segment.x(index), segment.z(index)));
        }

        if (!lastSegment || !trail.hasPendingPoint() || points.isEmpty()) return points;

        Point last = points.getLast();
        if (last.x() != trail.pendingX() || last.z() != trail.pendingZ())
            points.add(new Point(trail.pendingX(), trail.pendingZ()));

        return points;
    }

    /**
     * Decimates a polyline by removing points closer than epsilon to the last kept point.
     *
     * @param points       The source points.
     * @param epsilonWorld The minimum retained point distance in world units.
     * @return Decimated points.
     */
    static ArrayList<Point> decimate(List<Point> points, double epsilonWorld) {

        ArrayList<Point> decimated = new ArrayList<>();
        if (points.isEmpty()) return decimated;

        double epsilonSquared = epsilonWorld * epsilonWorld;
        Point first = points.getFirst();
        Point lastKept = first;
        decimated.add(first);

        for (int index = 1; index < points.size() - 1; index++) {
            Point point = points.get(index);
            if (distanceSquared(lastKept, point) < epsilonSquared) continue;

            decimated.add(point);
            lastKept = point;
        }

        Point last = points.getLast();
        if (decimated.getLast() != last) decimated.add(last);
        return decimated;
    }

    /**
     * Smooths an open polyline with Chaikin corner cutting.
     *
     * @param points     The source points.
     * @param iterations The number of smoothing iterations.
     * @return Smoothed points.
     */
    static List<Point> chaikin(List<Point> points, int iterations) {

        if (points.size() <= 2 || iterations <= 0) return points;

        List<Point> current = points;
        for (int iteration = 0; iteration < iterations; iteration++) {
            ArrayList<Point> next = new ArrayList<>((current.size() - 1) * 2 + 1);
            next.add(current.getFirst());

            for (int index = 0; index < current.size() - 1; index++) {
                Point a = current.get(index);
                Point b = current.get(index + 1);
                next.add(interpolate(a, b, 0.25));
                next.add(interpolate(a, b, 0.75));
            }

            next.add(current.getLast());
            current = next;
        }

        return current;
    }

    /**
     * Adds one smoothed open polyline to the mesh.
     *
     * @param vertices       The output vertex list.
     * @param points         The smoothed points.
     * @param halfWidthWorld The stroke half-width in world units.
     * @param color          The packed ARGB color.
     * @param fogUv          The fog UV coordinate calculator.
     */
    private static void addPolyline(ArrayList<RegionBorderRenderState.Vertex> vertices, List<Point> points,
                                    double halfWidthWorld, int color, FogUv fogUv) {

        if (points.size() < 2) return;

        Vec2d[] joins = buildJoins(points, halfWidthWorld);

        for (int index = 0; index < points.size() - 1; index++) {
            Point start = points.get(index);
            Point end = points.get(index + 1);
            if (start.x() == end.x() && start.z() == end.z()) continue;

            addSegment(vertices, start, end, joins[index], joins[index + 1], color, fogUv);
        }
    }

    /**
     * Builds per-vertex join offsets for an open polyline.
     *
     * @param points         The polyline points.
     * @param halfWidthWorld The stroke half-width in world units.
     * @return The join offsets.
     */
    private static Vec2d[] buildJoins(List<Point> points, double halfWidthWorld) {

        Vec2d[] joins = new Vec2d[points.size()];
        Point first = points.getFirst();
        Point second = points.get(1);
        Point nextToLast = points.get(points.size() - 2);
        Point last = points.getLast();

        joins[0] = StrokeGeometry.endCapOffset(second.x() - first.x(), second.z() - first.z(), halfWidthWorld);
        joins[joins.length - 1] = StrokeGeometry.endCapOffset(last.x() - nextToLast.x(), last.z() - nextToLast.z(),
                halfWidthWorld);

        for (int index = 1; index < points.size() - 1; index++) {
            Point previous = points.get(index - 1);
            Point current = points.get(index);
            Point next = points.get(index + 1);
            joins[index] = StrokeGeometry.joinOffset(previous.x(), previous.z(), current.x(), current.z(),
                    next.x(), next.z(), halfWidthWorld);
        }

        return joins;
    }

    /**
     * Adds one segment quad to the mesh.
     *
     * @param vertices  The output vertex list.
     * @param start     The segment start point.
     * @param end       The segment end point.
     * @param startJoin The start join offset.
     * @param endJoin   The end join offset.
     * @param color     The packed ARGB color.
     * @param fogUv     The fog UV coordinate calculator.
     */
    private static void addSegment(ArrayList<RegionBorderRenderState.Vertex> vertices, Point start, Point end,
                                   Vec2d startJoin, Vec2d endJoin, int color, FogUv fogUv) {

        vertices.add(vertex(start.x() + startJoin.x(), start.z() + startJoin.y(), 1.0F, color, fogUv));
        vertices.add(vertex(start.x() - startJoin.x(), start.z() - startJoin.y(), -1.0F, color, fogUv));
        vertices.add(vertex(end.x() - endJoin.x(), end.z() - endJoin.y(), -1.0F, color, fogUv));
        vertices.add(vertex(end.x() + endJoin.x(), end.z() + endJoin.y(), 1.0F, color, fogUv));
    }

    /**
     * Adds one perpendicular gap marker tick.
     *
     * @param vertices            The output vertex list.
     * @param center              The tick center point.
     * @param directionX          The adjacent trail direction X.
     * @param directionZ          The adjacent trail direction Z.
     * @param halfTickLengthWorld Half of the tick length in world units.
     * @param halfWidthWorld      Half of the tick thickness in world units.
     * @param color               The packed ARGB color.
     * @param fogUv               The fog UV coordinate calculator.
     */
    static void addTick(ArrayList<RegionBorderRenderState.Vertex> vertices, Point center, double directionX,
                        double directionZ, double halfTickLengthWorld, double halfWidthWorld, int color, FogUv fogUv) {

        double directionLength = Math.hypot(directionX, directionZ);
        if (directionLength == 0.0) return;

        Vec2d longOffset = StrokeGeometry.endCapOffset(directionX, directionZ, halfTickLengthWorld);
        double thicknessX = directionX / directionLength * halfWidthWorld;
        double thicknessZ = directionZ / directionLength * halfWidthWorld;

        vertices.add(vertex(center.x() + longOffset.x() + thicknessX, center.z() + longOffset.y() + thicknessZ,
                1.0F, color, fogUv));
        vertices.add(vertex(center.x() + longOffset.x() - thicknessX, center.z() + longOffset.y() - thicknessZ,
                -1.0F, color, fogUv));
        vertices.add(vertex(center.x() - longOffset.x() - thicknessX, center.z() - longOffset.y() - thicknessZ,
                -1.0F, color, fogUv));
        vertices.add(vertex(center.x() - longOffset.x() + thicknessX, center.z() - longOffset.y() + thicknessZ,
                1.0F, color, fogUv));
    }

    /**
     * Creates a mesh vertex.
     *
     * @param x     The world X coordinate.
     * @param z     The world Z coordinate.
     * @param side  The normalized side coordinate.
     * @param color The packed ARGB color.
     * @param fogUv The fog UV coordinate calculator.
     * @return The mesh vertex.
     */
    private static RegionBorderRenderState.Vertex vertex(double x, double z, float side, int color, FogUv fogUv) {

        return new RegionBorderRenderState.Vertex((float) x, (float) z, fogUv.u(x), fogUv.v(z), side, 0.0F,
                0.0F, 0.0F, color);
    }

    /**
     * Computes the squared distance between two points.
     *
     * @param a The first point.
     * @param b The second point.
     * @return The squared distance.
     */
    private static double distanceSquared(Point a, Point b) {

        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        return dx * dx + dz * dz;
    }

    /**
     * Linearly interpolates between two points.
     *
     * @param a The first point.
     * @param b The second point.
     * @param t The interpolation fraction.
     * @return The interpolated point.
     */
    private static Point interpolate(Point a, Point b, double t) {

        return new Point(a.x() * (1.0 - t) + b.x() * t, a.z() * (1.0 - t) + b.z() * t);
    }

    /**
     * Counts the points produced by smoothing the decimated polylines.
     *
     * @param polylines The decimated polylines.
     * @return The estimated smoothed point count.
     */
    private static int smoothedPointCount(List<Polyline> polylines) {

        int count = 0;
        int multiplier = 1 << TrailRenderer.CHAIKIN_ITERATIONS;

        for (Polyline polyline : polylines) {
            int size = polyline.points().size();
            count += size <= 2 ? size : (size - 1) * multiplier + 1;
        }

        return count;
    }

    /**
     * Applies a float alpha to the trail RGB color.
     *
     * @return The packed ARGB color.
     */
    private static int withAlpha() {

        int alphaByte = Math.round(Math.min(1.0F, TrailRenderer.TRAIL_ALPHA) * 255.0F);
        return alphaByte << 24 | ModConstants.COLOR_TRAIL_YELLOW & 0x00FFFFFF;
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
     * One decimated trail segment.
     *
     * @param points   The segment points.
     * @param gapStart Whether this polyline starts after a gap.
     * @param gapEnd   Whether this polyline ends before a gap.
     */
    record Polyline(List<Point> points, boolean gapStart, boolean gapEnd) {
    }

    /**
     * One trail point.
     *
     * @param x The world X coordinate.
     * @param z The world Z coordinate.
     */
    record Point(double x, double z) {
    }

    /**
     * Fog mask UV calculator for a dimension.
     *
     * @param xMin   The dimension minimum X.
     * @param zMin   The dimension minimum Z.
     * @param width  The dimension width.
     * @param height The dimension height.
     */
    record FogUv(double xMin, double zMin, double width, double height) {

        /**
         * Builds a fog UV calculator from a dimension.
         *
         * @param dimension The map dimension.
         * @return The fog UV calculator.
         */
        private static FogUv from(Dimension dimension) {

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
