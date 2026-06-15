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

package com.duom.ardamaps.gui.hud.compass.rendering;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.conversion.DistanceUnitConverter;
import com.duom.ardamaps.core.data.conversion.VectorProjection;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import com.duom.ardamaps.gui.widgets.ToastWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Renders a compass HUD element
 */
public class CompassRenderer {

    /** Y offset from top of the screen **/
    public static final int Y_OFFSET = 10;

    /** Field of view in degrees **/
    public static final float FOV_DEGREES = 90f;

    final static List<Cardinal> CARDINALS = List.of(
            new Cardinal("N", 180),
            new Cardinal("E", 270),
            new Cardinal("S", 0),
            new Cardinal("W", 90)
    );

    /** Map of angles to landmarks - avoid allocating every tick **/
    private static final LinkedHashMap<Float, LocationClient> angleToLandmarkMap = new LinkedHashMap<>();

    /** Compass width **/
    private static final int COMPASS_WIDTH = 360;

    /** Compass horizontal bar height **/
    private static final int COMPASS_TRACK_HEIGHT = 12;

    /** Half compass horizontal bar height **/
    private static final int HALF_COMPASS_TRACK_HEIGHT = COMPASS_TRACK_HEIGHT / 2;

    /** Landmark icon size (squared image) **/
    private static final int LANDMARK_ICON_SIZE = 20;

    /** Half compass width **/
    private static final float HALF_COMPASS_WIDTH = COMPASS_WIDTH / 2f;

    /** Compass track offset Y **/
    private static final int COMPASS_TRACK_OFFSET_Y = Y_OFFSET + LANDMARK_ICON_SIZE;

    /** Half field of view in degrees **/
    private static final float HALF_FOV = FOV_DEGREES / 2f;

    /** Distance at which POI markers start to fade **/
    private static final double FADE_START = 20.0;

    /** Distance squared at which POI markers start to fade **/
    private static final double FADE_START_SQUARED = FADE_START * FADE_START;

    /** Minimum alpha value for fading **/
    private static final float MIN_ALPHA = 0.1f;

    /** Max compass draw distance in real world units*/
    private static double rawMaxCompassRenderDistance;

    /** Tracks dimension for dimension change cache invalidation */
    private static String cachedDimensionId = "";

    /** Max compass draw distance */
    private static double maxCompassRenderDistanceSquared;

    /**
     * Renders the compass
     *
     * @param context   the draw context
     * @param tickDelta unused
     */
    public static void render(DrawContext context, float tickDelta) {

        var player = Client.player();
        if (player == null) return;
        if (Client.mc().isInSingleplayer()) return;

        // Skip rendering if map screen is open
        if (Client.isShowingMapScreen()) return;

        var globalAlpha = ArdaMapsClient.CONFIG.getCompassOpacity();
        if (globalAlpha <= 0.02f) return;

        if (rawMaxCompassRenderDistance != ArdaMapsClient.CONFIG.getCompassDrawDistance()
                || maxCompassRenderDistanceSquared == 0
                || !Objects.equals(Client.currentDimensionId(),(cachedDimensionId))) {

            rawMaxCompassRenderDistance = ArdaMapsClient.CONFIG.getCompassDrawDistance();

            cachedDimensionId = Client.currentDimensionId();

            var blockCompassRenderDistance = ArdaMapsClient.CONFIG.getCompassDrawDistanceBlocks(Client.currentDimension());
            maxCompassRenderDistanceSquared = blockCompassRenderDistance * blockCompassRenderDistance;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        var textRenderer = Client.mc().textRenderer;
        var playerPos = player.getPos();

        int screenWidth = context.getScaledWindowWidth();
        int centerX = screenWidth / 2;

        float yaw = MathHelper.lerp(tickDelta, player.prevYaw, player.getYaw());
        yaw = MathHelper.wrapDegrees(yaw);

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, -150);

        drawBackground(context, centerX, globalAlpha);

        renderMarkers(context, playerPos, yaw, centerX, globalAlpha);
        renderWaypoint(context, playerPos, yaw, centerX, textRenderer, globalAlpha);
        renderCardinals(context, yaw, centerX, globalAlpha);

        matrices.pop();

        RenderSystem.disableBlend();
    }

    /**
     * Render the  compass background
     *
     * @param context     the draw context
     * @param centerX     the centre of the screen
     * @param globalAlpha the global alpha
     */
    private static void drawBackground(DrawContext context, int centerX, float globalAlpha) {

        RenderSystem.setShaderColor(1f, 1f, 1f, globalAlpha);
        context.drawTexture(ModConstants.COMPASS_BACKGROUND, centerX - COMPASS_WIDTH / 2, COMPASS_TRACK_OFFSET_Y, 0, 0, COMPASS_WIDTH, COMPASS_TRACK_HEIGHT, COMPASS_WIDTH, COMPASS_TRACK_HEIGHT);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Render the location markers on the compass
     *
     * @param context     the draw context
     * @param playerPos   the player position
     * @param playerYaw   the player yaw
     * @param centerX     the centre of the screen
     * @param globalAlpha the global alpha
     */
    private static void renderMarkers(DrawContext context, Vec3d playerPos, float playerYaw, int centerX, float globalAlpha) {

        var exploration = ArdaMapsClient.CONFIG.getClientProgress().getExplorationState(Client.currentDimensionId(), false);

        if (exploration == null) return;

        projectLocations(ArdaMapsClient.NEAR_LOCATIONS, exploration);

        int zOffset = 0;
        for (var entry : angleToLandmarkMap.entrySet()) {

            float x = angleToScreenX(playerYaw, entry.getKey(), centerX);

            if (x != Float.POSITIVE_INFINITY && x != Float.NEGATIVE_INFINITY && isOnScreen(x, centerX)) {

                float alpha = Math.min(
                        getPoiAlpha(entry.getValue().getPosition().squaredDistanceTo(playerPos)),
                        getAlpha(x, centerX)
                );
                alpha = Math.min(alpha, globalAlpha);

                if (entry.getValue().isRevealed())
                    drawLocationIcon(context, alpha, x, entry.getValue().getIcon(), zOffset);
                else
                    drawUnknownLocationSprite(context, alpha, x, zOffset);

                zOffset++;
            }
        }
    }

    /**
     * Render the waypoint on the compass
     *
     * @param context      the draw context
     * @param playerPos    the player position
     * @param yaw          the player yaw
     * @param centerX      the centre of the screen
     * @param textRenderer the text renderer
     */
    private static void renderWaypoint(DrawContext context, Vec3d playerPos, float yaw, int centerX, TextRenderer textRenderer, float globalAlpha) {

        var currentDimensionId = Client.currentDimensionId();
        var waypointsToRemove = new ArrayList<Waypoint>();

        for (var waypoint : ArdaMapsClient.CONFIG.getWaypoints(currentDimensionId)) {

            var waypointDistance = waypoint.getPosition().distanceTo(playerPos);

            if (waypointDistance < 2) {

                waypointsToRemove.add(waypoint);

                if (waypoint.showToast())
                    ArdaMapsClient.showToast(new ToastWidget(
                            Text.translatable("ardamaps.client.waypoint.reached"),
                            waypoint.icon(),
                            waypoint.r(), waypoint.g(), waypoint.b()));
            }

            // Skip icon rendering if not set
            if (waypoint.icon() == null) continue;

            var realWorldUnits = DistanceUnitConverter.asRealWorldUnits(Client.currentDimension(), waypointDistance);
            var halfCompassWidth = COMPASS_WIDTH / 2f;
            float waypointAngle = VectorProjection.projectToHorizontalAngle(waypoint.getPosition());
            float x = angleToScreenX(yaw, waypointAngle, centerX);

            if (x == Float.POSITIVE_INFINITY) x = centerX + halfCompassWidth;
            else if (x == Float.NEGATIVE_INFINITY) x = centerX - halfCompassWidth;

            var icon = IconSpriteAtlas.retrieveSprite(waypoint.icon());

            context.getMatrices().push();
            context.getMatrices().translate( x - (float) LANDMARK_ICON_SIZE / 2, Y_OFFSET, 100);
            RenderSystem.setShaderColor(waypoint.r(), waypoint.g(), waypoint.b(), globalAlpha);

            if (icon != null
                    && icon.getContents() != null
                    && !Objects.equals(icon.getContents().getId(), MissingSprite.getMissingSpriteId())) {

                context.drawSprite(0, 0, 0, LANDMARK_ICON_SIZE, LANDMARK_ICON_SIZE, IconSpriteAtlas.retrieveSprite(ModConstants.ICON_WAYPOINT));

            } else {

                context.drawTexture(waypoint.icon(), 0, 0, 0, 0, LANDMARK_ICON_SIZE, LANDMARK_ICON_SIZE, LANDMARK_ICON_SIZE, LANDMARK_ICON_SIZE);
            }

            context.drawText(textRenderer, realWorldUnits, (LANDMARK_ICON_SIZE / 2)-(textRenderer.getWidth(realWorldUnits) / 2), LANDMARK_ICON_SIZE + 15, ModConstants.COLOR_WHITE, false);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            context.getMatrices().pop();
        }

        // Safely remove waypoints here
        for (var waypoint : waypointsToRemove) {
            ArdaMapsClient.CONFIG.removeWaypoint(waypoint);
        }
    }

    /**
     * Render the cardinal directions on the compass
     *
     * @param context      the draw context
     * @param yaw          the player yaw
     * @param centerX      the centre of the screen
     * @param globalAlpha  the transparency of the compass
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private static void renderCardinals(DrawContext context, float yaw, int centerX, float globalAlpha) {

        for (Cardinal cardinal : CARDINALS) {

            float x = angleToScreenX(yaw, cardinal.yaw(), centerX);
            var sprite = switch (cardinal.label()) {
                case "N" -> ModConstants.ICON_CARDINAL_NORTH;
                case "E" -> ModConstants.ICON_CARDINAL_EAST;
                case "S" -> ModConstants.ICON_CARDINAL_SOUTH;
                case "W" -> ModConstants.ICON_CARDINAL_WEST;
                default -> null;
            };

            if (x != Float.POSITIVE_INFINITY && x != Float.NEGATIVE_INFINITY) {

                float alpha = Math.min(getAlpha(x, centerX), globalAlpha);

                RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
                context.drawSprite((int) (x - HALF_COMPASS_TRACK_HEIGHT), COMPASS_TRACK_OFFSET_Y, 0, COMPASS_TRACK_HEIGHT, COMPASS_TRACK_HEIGHT, IconSpriteAtlas.retrieveSprite(sprite));
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        }
    }

    /**
     * Project the landmark position given their relative angle to cardinal angle
     *
     * @param locations the locations to project
     */
    private static void projectLocations(Map<Double, LocationClient> locations, PlayerExploration exploration) {

        angleToLandmarkMap.clear();

        for (var entry : locations.entrySet()) {

            var squaredDistance = entry.getKey();

            if (squaredDistance > maxCompassRenderDistanceSquared) break;

            var location = entry.getValue();

            if (!ArdaMapsClient.CONFIG.isMapRevealAll()) {

                // Do not project hidden locations - they should not appear on the compass at all
                var explorationState = exploration.stateAtWorldPos(location.getPosition().x, location.getPosition().z);
                if (explorationState == ExplorationState.HIDDEN) continue;
            }

            float projected = VectorProjection.projectToHorizontalAngle(location.getPosition());
            angleToLandmarkMap.put(projected, location);
        }
    }

    /**
     * Converts an angle difference to a screen X coordinate on the compass bar.
     *
     * @param angle      The player's current yaw angle in degrees
     * @param otherAngle The target angle (cardinal or landmark) in degrees
     * @param centerX    The screen centre X coordinate
     * @return The screen X position, or Float.NaN if the target is outside the FOV
     */
    private static float angleToScreenX(float angle, float otherAngle, int centerX) {

        float delta = otherAngle - angle;

        delta = ((delta + 540) % 360) - 180;

        if (Math.abs(delta) > FOV_DEGREES / 2) {

            return delta > 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }

        float percent = delta / HALF_FOV;
        return centerX + percent * HALF_COMPASS_WIDTH;
    }

    /**
     * Check if x position is on screen
     *
     * @param x       the x position
     * @param centerX the centre of the screen
     * @return true if on screen
     */
    private static boolean isOnScreen(float x, int centerX) {
        int leftEdge = centerX - COMPASS_WIDTH / 2;
        int rightEdge = centerX + COMPASS_WIDTH / 2;
        return x >= leftEdge && x <= rightEdge;
    }

    /**
     * Calculates the text colour with alpha based on distance to landmark.
     *
     * @param distanceToLocationSquared Distance to the landmark in blocks.
     * @return ARGB color integer.
     */
    private static float getPoiAlpha(double distanceToLocationSquared) {

        double drawDistanceSquared = DistanceUnitConverter.blocksToRealWorldUnits(Client.currentDimension(), ArdaMapsClient.CONFIG.getCompassDrawDistance() * ArdaMapsClient.CONFIG.getCompassDrawDistance());
        double t = (distanceToLocationSquared - FADE_START_SQUARED) / (drawDistanceSquared - FADE_START_SQUARED);
        t = MathHelper.clamp(t, 0.0, 1.0);
        double alphaFactor = 1.0 - t;
        return (float) Math.max(alphaFactor, 0.1);
    }

    /**
     * Calculate alpha based on distance to centre
     *
     * @param x       the x position
     * @param centerX the centre x position
     * @return the alpha value
     */
    public static float getAlpha(float x, float centerX) {

        float half = COMPASS_WIDTH / 2f;

        // distance from centre normalized to [0..1]
        float distanceToCenter = Math.abs(x - centerX) / half;
        distanceToCenter = Math.min(distanceToCenter, 1f);

        // linear fade: 1.0 at center - MIN_ALPHA at edges
        return 1f - distanceToCenter * (1f - MIN_ALPHA);
    }

    /**
     * Draw the location icon on the compass
     *
     * @param context       the draw context
     * @param alpha         the alpha value
     * @param x             the x position on the screen
     * @param textureToDraw the texture to draw
     * @param zOffset       the z offset on which to draw the icon
     */
    private static void drawLocationIcon(DrawContext context, float alpha, float x, Identifier textureToDraw, int zOffset) {

        if (textureToDraw == null) return;

        context.getMatrices().push();
        context.getMatrices().translate(x - (float) LANDMARK_ICON_SIZE / 2, Y_OFFSET, zOffset);

        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        context.drawSprite(0, 0, 0, LANDMARK_ICON_SIZE, LANDMARK_ICON_SIZE, IconSpriteAtlas.retrieveSprite(textureToDraw));
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        context.getMatrices().pop();
    }

    /**
     * Draw the unknown location icon on the compass
     *
     * @param context the draw context
     * @param alpha   the alpha value
     * @param x       the x position on the screen
     * @param zOffset the z offset on which to draw the icon
     */
    private static void drawUnknownLocationSprite(DrawContext context, float alpha, float x, int zOffset) {

        context.getMatrices().push();
        context.getMatrices().translate(x - (float) LANDMARK_ICON_SIZE / 2, Y_OFFSET, zOffset);

        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        context.drawSprite(0, 0, 0, LANDMARK_ICON_SIZE, LANDMARK_ICON_SIZE, IconSpriteAtlas.retrieveSprite(ModConstants.UNKNOWN_ICON));
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        context.getMatrices().pop();
    }

    /** Cardinal directions **/
    record Cardinal(String label, float yaw) {
    }
}