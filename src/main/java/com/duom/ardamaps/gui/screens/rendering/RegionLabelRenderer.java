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
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.core.data.map.region.RegionGeometry;
import com.duom.ardamaps.core.data.map.region.RegionShape;
import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.widgets.ExpansionAnimation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders on-map region labels from vector region geometry.
 */
public final class RegionLabelRenderer {

    /** Pixel padding around accepted label collision rectangles. */
    private static final int COLLISION_PADDING = 3;

    /** Base label text alpha. */
    private static final float BASE_ALPHA = 0.85f;

    /**
     * Renders visible region labels for the current camera.
     *
     * @param context     The GUI graphics extractor.
     * @param font        The font renderer.
     * @param camera      The active map camera.
     * @param exploration The current exploration state, or null.
     */
    public void render(GuiGraphicsExtractor context, Font font, MapCamera camera, @Nullable PlayerExploration exploration) {

        if (!ArdaMapsClient.CONFIG.isShowRegionBorders()) return;

        RegionGeometry geometry = ArdaMapsClient.CONFIG.getRegionGeometry(camera.getDimension().getId());
        if (geometry == null || geometry.lastUpdate() == null || geometry.regions().length == 0) return;

        boolean revealAll = ArdaMapsClient.CONFIG.isMapRevealAll();
        if (!revealAll && exploration == null) return;

        List<Candidate> candidates = collectCandidates(font, camera, geometry, exploration, revealAll);
        candidates.sort(Comparator
                .comparingInt(Candidate::depth)
                .thenComparing(Comparator.comparingDouble(Candidate::radius).reversed()));

        List<ScreenRectangle> accepted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (collides(candidate.bounds(), accepted)) continue;

            accepted.add(candidate.bounds());
            int color = GuiTextures.withAlpha(ModConstants.COLOR_WHITE, candidate.alpha());

            context.text(font, candidate.name(), candidate.x(), candidate.y(), color, true);
        }
    }

    /**
     * Collects all currently renderable label candidates.
     *
     * @param font        The font renderer.
     * @param camera      The active map camera.
     * @param geometry    The region geometry.
     * @param exploration The current exploration state, or null.
     * @param revealAll   Whether fog-of-war is disabled.
     * @return The candidate labels.
     */
    private static List<Candidate> collectCandidates(Font font, MapCamera camera, RegionGeometry geometry,
                                                     @Nullable PlayerExploration exploration, boolean revealAll) {

        double pixelsPerBlock = camera.getVisualPixelsPerBlock();
        double zoomT = normalizedZoom(camera);
        List<Candidate> candidates = new ArrayList<>();

        for (RegionShape shape : geometry.regions()) {
            if (!labelVisible(shape, exploration, revealAll)) continue;

            int textWidth = font.width(shape.name());
            double radiusPx = shape.labelRadius() * pixelsPerBlock;
            if (!labelFits(shape.depth(), radiusPx, textWidth)) continue;

            float zoomScale = shape.depth() == 0
                    ? 1.0F
                    : MapMarkerRenderer.markerZoomScale(camera.identityRelativeScale(), camera.minIdentityRelativeScale());
            float alpha = fadeAlpha(shape.depth(), zoomT) * zoomScale * BASE_ALPHA;

            if (alpha <= 0.01f) continue;

            Vec2d screen = camera.worldToScreenCoordinates(shape.labelAnchor());
            int x = (int) Math.round(screen.x()) - textWidth / 2;
            int y = (int) Math.round(screen.y()) - font.lineHeight / 2;

            ScreenRectangle bounds = new ScreenRectangle(
                    x - COLLISION_PADDING,
                    y - COLLISION_PADDING,
                    textWidth + COLLISION_PADDING * 2,
                    font.lineHeight + COLLISION_PADDING * 2);
            candidates.add(new Candidate(shape.name(), x, y, bounds, alpha, shape.labelRadius(), shape.depth()));
        }

        return candidates;
    }

    /**
     * Checks whether a horizontal label fits the available label radius.
     *
     * @param depth     The region hierarchy depth.
     * @param radiusPx  The label radius in screen pixels.
     * @param textWidth The label text width.
     * @return true when the label should render.
     */
    static boolean labelFits(int depth, double radiusPx, int textWidth) {

        return depth == 0 || 2.0 * radiusPx >= textWidth;
    }

    /**
     * Checks fog visibility for a label anchor.
     *
     * @param shape       The region shape.
     * @param exploration The exploration state, or null.
     * @param revealAll   Whether fog-of-war is disabled.
     * @return true when the label may be shown.
     */
    private static boolean labelVisible(RegionShape shape, @Nullable PlayerExploration exploration, boolean revealAll) {

        if (revealAll) return true;
        if (exploration == null) return false;

        return exploration.stateAtWorldPos(shape.labelAnchor().x(), shape.labelAnchor().y()).ordinal()
                >= ExplorationState.VISIBLE.ordinal();
    }

    /**
     * Computes the depth-based label alpha.
     *
     * @param depth The region hierarchy depth.
     * @param zoomT The normalized zoom, where 1 is fully zoomed out.
     * @return The label alpha multiplier.
     */
    private static float fadeAlpha(int depth, double zoomT) {

        if (depth == 0) return 1.0F;

        double end = 0.9 - Math.min(depth - 1, 5) * 0.09;
        double start = end - 0.22;
        float fadeOut = ExpansionAnimation.smoothstep((float) ((zoomT - start) / (end - start)));
        return 1.0F - fadeOut;
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
     * Checks a candidate rectangle against already accepted labels.
     *
     * @param bounds   The candidate bounds.
     * @param accepted The accepted bounds.
     * @return true when the candidate overlaps an accepted label.
     */
    private static boolean collides(ScreenRectangle bounds, List<ScreenRectangle> accepted) {

        for (ScreenRectangle other : accepted) {
            if (bounds.intersection(other) != null) return true;
        }

        return false;
    }

    /**
     * One renderable label candidate.
     *
     * @param name       The text to render.
     * @param x          The screen-space X coordinate.
     * @param y          The screen-space Y coordinate.
     * @param bounds     The collision bounds.
     * @param alpha      The text alpha.
     * @param radius     The label radius.
     * @param depth      The hierarchy depth.
     */
    private record Candidate(String name, int x, int y, ScreenRectangle bounds, float alpha, double radius, int depth) {
    }

}
