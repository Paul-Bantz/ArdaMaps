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

package com.duom.ardamaps.gui.hud.toposcope.rendering;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.conversion.DistanceUnitConverter;
import com.duom.ardamaps.core.data.conversion.VectorProjection;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.hud.toposcope.Toposcope;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Renders location overlays on the HUD.
 */
@Environment(EnvType.CLIENT)
public class ToposcopeRenderer {

    /** Distance at which text starts to fade out (in blocks). */
    private static final double FADE_START = 20.0;

    /** Linear distance (in blocks) below which locations are culled entirely. Derived from the squared {@link ArdaMapsClient#LOCATION_NEAR_DISTANCE}. */
    private static final double NEAR_FADE_END = Math.sqrt(ArdaMapsClient.LOCATION_NEAR_DISTANCE);

    /** Linear distance (in blocks) at which the near fade begins (100% opacity). Derived from {@link ArdaMapsClient#LOCATION_NEAR_DISTANCE} + 10 squared-blocks. */
    private static final double NEAR_FADE_START = Math.sqrt(ArdaMapsClient.LOCATION_NEAR_DISTANCE + 10);

    /** screen mappings list (avoid new alloc every tick). */
    private static final List<ScreenMappedLocation> screenMappings = new ArrayList<>();

    /** Hint text to display over the hotbar when hovering a location. */
    private static final Text TELEPORT_HINT = Text.translatable("ardamaps.client.generic.teleport");

    /** Hint text to display over the hotbar when hovering a location that can be set as a waypoint. */
    private static final Text SET_WAYPOINT_HINT = Text.translatable("ardamaps.client.generic.set.waypoint");

    /** Hint text to display over the hotbar when hovering a location that already has a waypoint. */
    private static final Text REMOVE_WAYPOINT_HINT = Text.translatable("ardamaps.client.generic.remove.waypoint");

    /** Currently hovered location, if any. */
    private static LocationClient hoveredLocation = null;

    /** Cached screen dimensions to avoid redundant calls. Updated when screen changes dimensions. */
    private static int cachedScreenW = -1;

    private static int cachedScreenH = -1;

    /** X position of the teleport hint text displayed over the hotbar when location is explored. */
    private static int exploredTeleportHintXPosition = 0;

    /** X position of the set waypoint hint text displayed over the hotbar when location is explored. */
    private static int exploredSetWaypointHintXPosition = 0;

    /** X position of the set waypoint hint text displayed over the hotbar when location is unknown. */
    private static int unknownSetWaypointHintXPosition = 0;

    /** Y position of the hint text displayed over the hotbar */
    private static int hintYPosition = 0;

    /** Max toposcope draw distance in real world units */
    private static double rawMaxToposcopeRenderDistance;

    /** Tracks dimension for dimension change cache invalidation */
    private static String cachedDimensionId = "";

    /** Max toposcope draw distance */
    private static double maxToposcopeRenderDistanceSquared;

    /**
     * Renders the HUD overlay for cached signs.
     *
     * @param drawContext The drawing context.
     */
    public static void render(DrawContext drawContext) {

        if (!Toposcope.overlayEnabled) return;

        // Skip rendering if map screen is open
        if (Client.isShowingMapScreen()) return;

        var player = Client.player();
        if (player == null) return;

        if (rawMaxToposcopeRenderDistance != ArdaMapsClient.CONFIG.getToposcopeDrawDistance()
                || maxToposcopeRenderDistanceSquared == 0
                || !Objects.equals(Client.currentDimensionId(), (cachedDimensionId))) {

            rawMaxToposcopeRenderDistance = ArdaMapsClient.CONFIG.getToposcopeDrawDistance();

            cachedDimensionId = Client.currentDimensionId();

            var blockToposcopeRenderDistance = ArdaMapsClient.CONFIG.getToposcopeDrawDistanceBlocks(Client.currentDimension());
            maxToposcopeRenderDistanceSquared = blockToposcopeRenderDistance * blockToposcopeRenderDistance;
        }

        TextRenderer textRenderer = Client.mc().textRenderer;

        var exploration = ArdaMapsClient.CONFIG.getClientProgress().getExplorationState(Client.currentDimensionId(), false);

        if (exploration == null) return;

        var locations = ArdaMapsClient.NEAR_LOCATIONS;

        if (locations.isEmpty()) return;

        // Refresh width and height if needed
        if (cachedScreenW != Client.getScaledWindowWidth() || cachedScreenH != Client.getScaledWindowHeight()) {

            cachedScreenW = Client.getScaledWindowWidth();
            cachedScreenH = Client.getScaledWindowHeight();

            var halfScreenW = cachedScreenW / 2;
            int waypointHintWidth = Math.max(textRenderer.getWidth(SET_WAYPOINT_HINT), textRenderer.getWidth(REMOVE_WAYPOINT_HINT));
            exploredTeleportHintXPosition = halfScreenW - (textRenderer.getWidth(TELEPORT_HINT) + waypointHintWidth + 32) / 2;
            exploredSetWaypointHintXPosition = exploredTeleportHintXPosition + 20 + textRenderer.getWidth(TELEPORT_HINT);
            unknownSetWaypointHintXPosition = halfScreenW - (waypointHintWidth + 12) / 2;

            // Hotbar is 22px high, add 18px padding to position hint text above it
            hintYPosition = cachedScreenH - 40;
        }

        refreshScreenMappings(locations, exploration, player);
        renderScreenMappedLocations(drawContext, textRenderer);
    }

    /**
     * Refreshes the list of screen-mapped locations based on the player's current position and exploration state. Only includes locations that are not hidden and within the configured draw distance.
     *
     * @param locations   The list of all locations to consider for rendering.
     * @param exploration The player's exploration state, used to determine which locations are hidden.
     * @param player      The player entity, used to calculate distances to locations.
     */
    private static void refreshScreenMappings(Map<Double, LocationClient> locations, PlayerExploration exploration, ClientPlayerEntity player) {

        screenMappings.clear();

        for (var entry : locations.entrySet()) {

            var squaredDistance = entry.getKey();

            if (squaredDistance > maxToposcopeRenderDistanceSquared) break;

            // Cull locations that are too close (they have been "discovered" at this range)
            if (squaredDistance <= ArdaMapsClient.LOCATION_NEAR_DISTANCE) continue;

            var location = entry.getValue();

            if (!ArdaMapsClient.CONFIG.isMapRevealAll()) {

                // Skip if location is hidden in exploration
                var explorationState = exploration.stateAtWorldPos(location.getPosition().x, location.getPosition().z);
                if (explorationState == ExplorationState.HIDDEN) continue;
            }

            Vec2f screen = VectorProjection.projectToScreen(location.getPosition());

            if (screen == null) continue;

            screenMappings.add(new ScreenMappedLocation(location, screen, location.getPosition().distanceTo(player.getPos())));
        }

        // Sort by screen X (then Y) before grouping so membership is deterministic across frames
        screenMappings.sort(Comparator.comparingDouble((ScreenMappedLocation loc) -> loc.screen.x)
                .thenComparingDouble(loc -> loc.screen.y));
    }

    /**
     * Renders location markers on the screen based on their screen-mapped positions. Handles grouping of nearby markers, hover detection, and drawing of location names and distances.
     *
     * @param drawContext  The drawing context for rendering operations.
     * @param textRenderer The text renderer for drawing location names and distances.
     */
    private static void renderScreenMappedLocations(DrawContext drawContext, TextRenderer textRenderer) {
        List<List<ScreenMappedLocation>> groups = groupByScreenPosition();

        // Track hovered location
        hoveredLocation = null;

        var matrices = drawContext.getMatrices();
        matrices.push();
        matrices.translate(0, 0, -150);

        for (var group : groups) {

            // Stable sort by world position so the render order never changes between frames
            group.sort(Comparator.comparingDouble((ScreenMappedLocation loc) -> loc.location.getPosition().x)
                    .thenComparingDouble(loc -> loc.location.getPosition().z));

            // Compute the average screen position of all group members as a stable anchor
            float sumX = 0, sumY = 0;
            for (var m : group) {
                sumX += m.screen.x;
                sumY += m.screen.y;
            }
            Vec2f groupAnchor = new Vec2f(sumX / group.size(), sumY / group.size());

            for (int index = 0; index < group.size(); index++)
                drawMarker(drawContext, group, index, groupAnchor, textRenderer);
        }
        matrices.pop();
    }

    /**
     * Groups landmarks that are close together on the screen using spatial grid hashing.
     *
     * @return A list of groups of landmarks.
     */
    private static List<List<ScreenMappedLocation>> groupByScreenPosition() {

        final double THRESHOLD = 30;

        List<List<ScreenMappedLocation>> groups = new ArrayList<>();
        boolean[] grouped = new boolean[screenMappings.size()];

        for (int i = 0; i < screenMappings.size(); i++) {
            if (grouped[i]) continue;

            List<ScreenMappedLocation> group = new ArrayList<>();
            group.add(screenMappings.get(i));
            grouped[i] = true;

            // Check all other landmarks for proximity
            for (int j = i + 1; j < screenMappings.size(); j++) {
                if (grouped[j]) continue;

                // Check if close to ANY member of current group (transitive grouping)
                for (var member : group) {

                    double dx = screenMappings.get(j).screen.x - member.screen.x;
                    double dy = screenMappings.get(j).screen.y - member.screen.y;
                    double distSq = dx * dx + dy * dy;

                    if (distSq <= THRESHOLD * THRESHOLD) {
                        group.add(screenMappings.get(j));
                        grouped[j] = true;
                        break;
                    }
                }
            }

            groups.add(group);
        }

        return groups;
    }

    /**
     * Draws an item in a group of locations at the same screen position.
     *
     * @param drawContext  The drawing context.
     * @param group        The group of locations. Used to offset the marker when overlapping.
     * @param groupIndex   The index of the location to draw within the group.
     * @param groupAnchor  The stable average screen position of the group.
     * @param textRenderer The text renderer for the location label.
     */
    private static void drawMarker(DrawContext drawContext, List<ScreenMappedLocation> group, int groupIndex, Vec2f groupAnchor, TextRenderer textRenderer) {

        int groupSpacing = 4;

        // Get screen centre (crosshair position)
        int screenCenterX = Client.getWindowCenterX();
        int screenCenterY = Client.getWindowCenterY();

        var entry = group.get(groupIndex);
        LocationClient location = entry.location;
        boolean isExplored = location.isRevealed();
        int lineHeight = textRenderer.fontHeight;

        int nameWidth = textRenderer.getWidth(location.getName());

        // Centre the whole stack around the group anchor so markers never overlap
        int markerSlot = 2 * lineHeight + groupSpacing;
        int totalHeight = group.size() * markerSlot - groupSpacing;
        int stackStartY = Math.round(groupAnchor.y) - totalHeight / 2;
        int baseY = stackStartY + groupIndex * markerSlot;
        int nameX = Math.round(groupAnchor.x) - nameWidth / 2;

        // Check if crosshair is over the name text
        boolean isHovered = isCrosshairOver(screenCenterX, screenCenterY, nameX, baseY, nameWidth, 2 * lineHeight);

        if (isHovered)
            hoveredLocation = location;

        var matrices = drawContext.getMatrices();
        matrices.translate(0, 0, 200);
        matrices.push();

        // Draw underline if hovered
        if (isHovered) {

            int iconSize = lineHeight * 2;
            int iconX = nameX - iconSize - 2;
            int bgWidth = nameWidth + iconSize + 34;
            int bgHeight = iconSize + lineHeight + 6;
            int bgX = iconX - 16;
            int bgY = baseY - 8;

            drawContext.drawNineSlicedTexture(ModConstants.MAP_GUI_ELEMENTS,
                    bgX, bgY,
                    bgWidth, bgHeight,
                    16,
                    16,
                    16,
                    16,
                    96,
                    48,
                    144, 160);

            boolean hasActiveWaypoint = getLocationActiveWaypoint(location).isPresent();

            if (isExplored) {

                var locationIcon = location.getIcon();

                if (locationIcon != null)
                    drawContext.drawSprite(iconX, baseY, 0, iconSize, iconSize, IconSpriteAtlas.retrieveSprite(locationIcon));

                if (ArdaMapsClient.CONFIG.isMapRevealAll() || location.isVisited())
                    drawWaypointAndTeleportHints(drawContext, textRenderer, hasActiveWaypoint);
                else
                    drawWaypointHint(drawContext, textRenderer, hasActiveWaypoint);

            } else {
                drawContext.drawSprite(iconX, baseY, 0, iconSize, iconSize, IconSpriteAtlas.retrieveSprite(ModConstants.UNKNOWN_ICON));
                drawWaypointHint(drawContext, textRenderer, hasActiveWaypoint);
            }
        }

        // Draw location name
        drawContext.drawText(
                textRenderer,
                location.getName(),
                nameX,
                baseY,
                getTextColor(entry.distance, isHovered),
                !isHovered
        );

        // Draw distance
        drawContext.drawText(
                textRenderer,
                DistanceUnitConverter.asRealWorldUnits(Client.currentDimension(), entry.distance),
                nameX,
                baseY + lineHeight,
                getTextColor(entry.distance, isHovered),
                !isHovered
        );

        matrices.pop();
    }

    /**
     * Checks if the crosshair is over the given text rectangle.
     *
     * @param crosshairX crosshair X position
     * @param crosshairY crosshair Y position
     * @param textX      text X position
     * @param textY      text Y position
     * @param textWidth  text width
     * @param textHeight text height
     * @return True if the crosshair is over the text, false otherwise.
     */
    private static boolean isCrosshairOver(int crosshairX, int crosshairY, int textX, int textY, int textWidth, int textHeight) {
        return crosshairX >= textX && crosshairX <= textX + textWidth && crosshairY >= textY && crosshairY <= textY + textHeight;
    }

    /**
     * Returns the @{link {@link Waypoint}} if there is an active waypoint set within 5 blocks (XZ) of the given location.
     *
     * @param location The location to check against.
     * @return the waypoint if one exists for this location or an empty.
     */
    public static Optional<Waypoint> getLocationActiveWaypoint(LocationClient location) {

        return ArdaMapsClient.CONFIG.getWaypointAtCoordinates(Client.currentDimensionId(), location.getPosition().x, location.getPosition().z, 5);
    }

    /**
     * Draws the hint text and mouse icons on the bottom centre of the screen over the hotswap bar
     *
     * @param drawContext       the draw context
     * @param textRenderer      the text renderer
     * @param hasActiveWaypoint whether there is an active waypoint or not to adjust the hint text
     */
    private static void drawWaypointAndTeleportHints(DrawContext drawContext, TextRenderer textRenderer, boolean hasActiveWaypoint) {

        Text waypointHint = hasActiveWaypoint ? REMOVE_WAYPOINT_HINT : SET_WAYPOINT_HINT;
        drawContext.drawSprite(exploredTeleportHintXPosition, hintYPosition, 0, 8, 8, IconSpriteAtlas.retrieveSprite(ModConstants.ICON_MOUSE_LEFT_CLICK));
        drawContext.drawText(
                textRenderer,
                TELEPORT_HINT,
                exploredTeleportHintXPosition + 12,
                hintYPosition,
                ModConstants.COLOR_WHITE,
                true);

        drawContext.drawSprite(exploredSetWaypointHintXPosition, hintYPosition, 0, 8, 8, IconSpriteAtlas.retrieveSprite(ModConstants.ICON_MOUSE_RIGHT_CLICK));
        drawContext.drawText(
                textRenderer,
                waypointHint,
                exploredSetWaypointHintXPosition + 12,
                hintYPosition,
                ModConstants.COLOR_WHITE,
                true);
    }

    /**
     * Draws the waypoint hint on the bottom centre of the screen. Display will depend on whether there is an active waypoint on the location or not
     *
     * @param drawContext       the draw context
     * @param textRenderer      the text renderer
     * @param hasActiveWaypoint whether there is an active waypoint or not to adjust the hint text
     */
    private static void drawWaypointHint(DrawContext drawContext, TextRenderer textRenderer, boolean hasActiveWaypoint) {
        Text waypointHint = hasActiveWaypoint ? REMOVE_WAYPOINT_HINT : SET_WAYPOINT_HINT;
        drawContext.drawSprite(unknownSetWaypointHintXPosition, hintYPosition, 0, 8, 8, IconSpriteAtlas.retrieveSprite(ModConstants.ICON_MOUSE_RIGHT_CLICK));
        drawContext.drawText(
                textRenderer,
                waypointHint,
                unknownSetWaypointHintXPosition + 12,
                hintYPosition,
                ModConstants.COLOR_WHITE,
                true);
    }

    /**
     * Calculates the text colour with alpha based on distance to location.
     *
     * @param distanceToLandmark Distance to the location in blocks.
     * @param focused            whether the text is focused or not
     * @return ARGB color integer.
     */
    @SuppressWarnings("ExtractMethodRecommender")
    private static int getTextColor(double distanceToLandmark, boolean focused) {

        if (focused) return ModConstants.COLOR_DARK_BROWN;

        // Far fade: 100% -> 10% as distance approaches the configured draw distance
        double alphaDelta = (distanceToLandmark - FADE_START) / (ArdaMapsClient.CONFIG.getToposcopeDrawDistanceBlocks(Client.currentDimension()) - FADE_START);
        alphaDelta = MathHelper.clamp(alphaDelta, 0.0, 1.0);

        double alphaFactor = 1.0 - alphaDelta;
        alphaFactor = Math.max(alphaFactor, 0.1);

        // Near fade: 100% -> 0% as distance approaches NEAR_FADE_END (= sqrt(LOCATION_NEAR_DISTANCE))
        if (distanceToLandmark < NEAR_FADE_START) {
            double nearAlphaDelta = (distanceToLandmark - NEAR_FADE_END) / (NEAR_FADE_START - NEAR_FADE_END);
            nearAlphaDelta = MathHelper.clamp(nearAlphaDelta, 0.0, 1.0);
            alphaFactor *= nearAlphaDelta;
        }

        int alpha = (int) (alphaFactor * 255.0);
        return (alpha << 24) | ModConstants.COLOR_WHITE;
    }

    /**
     * Gets the currently hovered location, if any.
     *
     * @return The hovered location, or null if none.
     */
    public static @Nullable LocationClient getHoveredLocation() {
        return hoveredLocation;
    }

    /** A record to hold location data along with its screen position and distance. */
    private record ScreenMappedLocation(LocationClient location, Vec2f screen, double distance) {
    }
}