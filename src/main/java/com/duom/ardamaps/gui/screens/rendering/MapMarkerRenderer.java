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
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.Vec3d;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.core.data.map.markers.MarkersManager;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import com.duom.ardamaps.gui.map.PlayerIcon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders map markers, the player marker, and waypoints for {@link com.duom.ardamaps.gui.screens.MapScreen}.
 */
@Environment(EnvType.CLIENT)
public class MapMarkerRenderer {

    /** Rendered player marker scale factor */
    private static final float PLAYER_MARKER_SCALE = .25f;

    /** Rendered map marker scale factor */
    private static final float MARKER_SCALE = .6f;

    /** Minimum fraction of their regular size markers shrink to when fully zoomed out. */
    private static final float MARKER_MIN_ZOOM_SCALE = .25f;

    /** Rendered map marker base size in pixels. */
    private static final int BASE_MARKER_BACKGROUND_SIZE = (int) (MarkersManager.get().mapMarkerBackgroundSize() * MARKER_SCALE);

    /** Rendered map marker icon base size in pixels. */
    private static final int BASE_MARKER_ICON_SIZE = (int) (MarkersManager.get().mapMarkerIconSize() * MARKER_SCALE);

    /** Opacity applied to markers outside the currently displayed vertical range. */
    private static final float MARKER_OUT_OF_RANGE_OPACITY = 0.25f;

    /** Base x offset to position the marker icon within the marker background. */
    private static final int BASE_MARKER_ICON_X_OFFSET = (int) (MarkersManager.get().mapMarkerIconXOffset() * MARKER_SCALE);

    /** Base y offset to position the marker icon within the marker background. */
    private static final int BASE_MARKER_ICON_Y_OFFSET = (int) (MarkersManager.get().mapMarkerIconYOffset() * MARKER_SCALE);

    /** Base inset used to fill the marker background interior. */
    private static final int BASE_MARKER_INSET = 4;

    /** Reusable buffer for markers currently under the mouse cursor. */
    private final List<DeferredMarker> mouseOverMarkers = new ArrayList<>();

    /** Current frame marker background size in pixels. */
    private int markerBackgroundSize = BASE_MARKER_BACKGROUND_SIZE;

    /** Current frame half marker background size in pixels. */
    private int halfMarkerSize = markerBackgroundSize / 2;

    /** Current frame marker icon size in pixels. */
    private int markerIconSize = BASE_MARKER_ICON_SIZE;

    /** Current frame marker icon x offset in pixels. */
    private int markerIconXOffset = BASE_MARKER_ICON_X_OFFSET;

    /** Current frame marker icon y offset in pixels. */
    private int markerIconYOffset = BASE_MARKER_ICON_Y_OFFSET;

    /** Current frame marker fill inset in pixels. */
    private int markerInset = BASE_MARKER_INSET;

    /** Backing location list used by the cached marker-filter result. Compared by reference identity. */
    private List<LocationClient> cachedMarkerBackingLocations;

    /** Dimension key used by the cached marker-filter result. */
    private String cachedMarkerDimensionId;

    /** Marker type key used by the cached marker-filter result. */
    private String cachedMarkerTypeKey;

    /** Filtered locations rendered by the marker loop for the current dimension/type/backing-list tuple. */
    private List<LocationClient> cachedMarkerLocations = List.of();

    /** The currently hovered location. */
    private LocationClient mouseOverLocation;

    /** The currently hovered waypoint. */
    private Waypoint mouseOverWaypoint;

    /**
     * Renders all map marker overlays and updates hover state.
     *
     * @param context                 The draw context for the current frame.
     * @param textRenderer            The text renderer used for focused labels and waypoint tooltips.
     * @param mapCamera               The active map camera.
     * @param mapFrameRenderer        The frame renderer used for viewport hit-testing.
     * @param selectedRange           The currently selected vertical range, or null when the layer is unranged.
     * @param focusedLocationPosition The location currently displayed in the side panel, or null.
     * @param selectedTypeKey         The selected marker type filter key, or null to render all marker types.
     * @param mouseOverWidgets        True when the mouse is currently over an interactive map widget.
     * @param framePadding            The padding used by frame hit-testing.
     * @param mouseX                  The current mouse X coordinate.
     * @param mouseY                  The current mouse Y coordinate.
     */
    public void render(DrawContext context, TextRenderer textRenderer, MapCamera mapCamera, MapFrameRenderer mapFrameRenderer,
                       @Nullable MapLayerRange selectedRange, @Nullable Vec3d focusedLocationPosition,
                       @Nullable String selectedTypeKey, boolean mouseOverWidgets, int framePadding, int mouseX, int mouseY) {

        mouseOverLocation = null;
        mouseOverWaypoint = null;
        updateMarkerSizes(mapCamera);

        renderMarkers(context, textRenderer, mapCamera, mapFrameRenderer, selectedRange, focusedLocationPosition,
                selectedTypeKey, mouseOverWidgets, mouseX, mouseY);
        renderPlayerMarker(context, mapCamera, mapFrameRenderer, selectedRange, framePadding);
        renderWaypoint(context, textRenderer, mapCamera, mapFrameRenderer, framePadding, mouseX, mouseY);
    }

    /**
     * Render points of interest on the map.
     *
     * @param context                 The draw context for the current frame.
     * @param textRenderer            The text renderer used for focused marker labels.
     * @param mapCamera               The active map camera.
     * @param mapFrameRenderer        The frame renderer used for viewport hit-testing.
     * @param selectedRange           The currently selected vertical range, or null when the layer is unranged.
     * @param focusedLocationPosition The location currently displayed in the side panel, or null.
     * @param selectedTypeKey         The selected marker type filter key, or null to render all marker types.
     * @param mouseOverWidgets        True when the mouse is currently over an interactive map widget.
     * @param mouseX                  The current mouse X coordinate.
     * @param mouseY                  The current mouse Y coordinate.
     */
    private void renderMarkers(DrawContext context, TextRenderer textRenderer, MapCamera mapCamera, MapFrameRenderer mapFrameRenderer,
                               @Nullable MapLayerRange selectedRange, @Nullable Vec3d focusedLocationPosition,
                               @Nullable String selectedTypeKey, boolean mouseOverWidgets, int mouseX, int mouseY) {

        var dimensionId = mapCamera.getDimension().getId();
        var locations = getCachedMarkerLocations(dimensionId, selectedTypeKey);

        boolean revealAll = ArdaMapsClient.CONFIG.isMapRevealAll();

        DeferredMarker focused = null;
        mouseOverMarkers.clear();

        for (var location : locations) {

            if (location.getPosition().x == 0 && location.getPosition().z == 0) continue;
            if (!revealAll && !location.isVisible()) continue;

            var landmarkScreenPos = mapCamera.worldToScreenCoordinates(
                    location.getPosition().x, location.getPosition().z);

            int screenX = (int) landmarkScreenPos.x();
            int screenY = (int) landmarkScreenPos.y();

            if (!mapFrameRenderer.coordinatesInFrame(screenX, screenY, -markerBackgroundSize)) continue;

            var xPos = screenX - halfMarkerSize;
            var yPos = screenY - markerBackgroundSize;

            var isMouseOver = mouseX > xPos && mouseX < xPos + markerBackgroundSize
                    && mouseY > yPos && mouseY < yPos + markerBackgroundSize
                    && !mouseOverWidgets;

            var isFocused = Objects.equals(location.getPosition(), focusedLocationPosition);
            var outOfRange = selectedRange != null && !selectedRange.containsY(location.getPosition().y);

            if (mouseOverLocation == null && isMouseOver)
                mouseOverLocation = location;

            if (isMouseOver) mouseOverMarkers.add(new DeferredMarker(xPos, yPos, location));
            else if (isFocused) focused = new DeferredMarker(xPos, yPos, location);
            else renderMarker(context, textRenderer, location, xPos, yPos, false, outOfRange);
        }

        if (focused != null)
            renderMarker(context, textRenderer, focused.location(), focused.x(), focused.y(), true, false);

        for (int idx = 0; idx < mouseOverMarkers.size(); idx++) {

            var mouseOveredMarker = mouseOverMarkers.get(idx);
            var location = mouseOveredMarker.location();

            renderMarker(context,
                    textRenderer,
                    location,
                    mouseOveredMarker.x(),
                    mouseOveredMarker.y(),
                    false,
                    selectedRange != null && !selectedRange.containsY(location.getPosition().y));

            if (idx == mouseOverMarkers.size() - 1) {
                mouseOverLocation = location;

                renderMarker(context,
                        textRenderer,
                        location,
                        mouseOveredMarker.x(),
                        mouseOveredMarker.y(),
                        true,
                        false);
            }
        }
    }

    /**
     * Render the player marker at the centre of the map.
     *
     * @param context          The draw context for the current frame.
     * @param mapCamera        The active map camera.
     * @param mapFrameRenderer The frame renderer used for viewport hit-testing.
     * @param selectedRange    The currently selected vertical range, or null when the layer is unranged.
     * @param framePadding     The padding used by frame hit-testing.
     */
    private void renderPlayerMarker(DrawContext context, MapCamera mapCamera, MapFrameRenderer mapFrameRenderer,
                                    @Nullable MapLayerRange selectedRange, int framePadding) {

        if (!Objects.equals(mapCamera.getDimension(), Client.currentDimension())) return;

        var iconImage = PlayerIcon.getPlayerIcon();
        if (iconImage == null) return;

        var clientPos = Client.playerPosition2d();
        var clientScreenPos = mapCamera.worldToScreenCoordinates(clientPos);

        var iconSize = (int) (PlayerIcon.ICON_SIZE * PLAYER_MARKER_SCALE);
        int halfIconSize = iconSize / 2;

        int screenX = (int) clientScreenPos.x() - halfIconSize;
        int screenZ = (int) clientScreenPos.y() - halfIconSize;

        if (!mapFrameRenderer.coordinatesInFrame(screenX, screenZ, framePadding)) {
            return;
        }

        Double playerY = Client.playerPositionY();
        boolean outOfRange = selectedRange != null && playerY != null && !selectedRange.containsY(playerY);
        int markerBackgroundColor = outOfRange
                ? withOpacity(ModConstants.COLOR_DARK_BROWN)
                : ModConstants.COLOR_DARK_BROWN;

        context.fill(screenX, screenZ, screenX + iconSize, screenZ + iconSize, markerBackgroundColor);

        if (outOfRange) RenderSystem.setShaderColor(1f, 1f, 1f, MARKER_OUT_OF_RANGE_OPACITY);
        context.drawTexture(iconImage,
                screenX,
                screenZ,
                iconSize, iconSize,
                PlayerIcon.ICON_SIZE, PlayerIcon.ICON_SIZE,
                PlayerIcon.ICON_SIZE, PlayerIcon.ICON_SIZE,
                PlayerIcon.ICON_SIZE, PlayerIcon.ICON_SIZE
        );
        if (outOfRange) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Render waypoint markers on the map.
     *
     * @param context          The draw context for the current frame.
     * @param textRenderer     The text renderer used for waypoint tooltips.
     * @param mapCamera        The active map camera.
     * @param mapFrameRenderer The frame renderer used for viewport hit-testing.
     * @param framePadding     The padding used by frame hit-testing.
     * @param mouseX           The current mouse X coordinate.
     * @param mouseY           The current mouse Y coordinate.
     */
    private void renderWaypoint(DrawContext context, TextRenderer textRenderer, MapCamera mapCamera, MapFrameRenderer mapFrameRenderer,
                                int framePadding, int mouseX, int mouseY) {

        var waypoints = ArdaMapsClient.CONFIG.getWaypoints(mapCamera.getDimension().getId());

        for (var waypoint : waypoints) {

            var waypointScreenPos = mapCamera.worldToScreenCoordinates(waypoint.getPosition());

            int halfIconSize = BASE_MARKER_ICON_SIZE / 2;

            int screenX = (int) waypointScreenPos.x() - halfIconSize;
            int screenY = (int) waypointScreenPos.y() - halfIconSize;

            if (mouseOverWaypoint == null
                    && mouseX >= screenX
                    && mouseX <= screenX + BASE_MARKER_ICON_SIZE
                    && mouseY >= screenY
                    && mouseY <= screenY + BASE_MARKER_ICON_SIZE) {

                mouseOverWaypoint = waypoint;
                context.drawTooltip(textRenderer, Text.literal(waypoint.text()), mouseX, mouseY);
            }

            if (mapFrameRenderer.coordinatesInFrame(screenX, screenY, framePadding) && waypoint.icon() != null) {

                var iconIdentifier = ModConstants.id(waypoint.icon());
                var icon = IconSpriteAtlas.retrieveSprite(iconIdentifier);

                RenderSystem.setShaderColor(waypoint.r(), waypoint.g(), waypoint.b(), 1.0f);

                if (icon != null
                        && icon.getContents() != null
                        && !Objects.equals(icon.getContents().getId(), MissingSprite.getMissingSpriteId())) {

                    context.drawSprite(screenX, screenY, 0, BASE_MARKER_ICON_SIZE, BASE_MARKER_ICON_SIZE, icon);

                } else {

                    context.drawTexture(iconIdentifier,
                            screenX,
                            screenY,
                            0,
                            0,
                            BASE_MARKER_ICON_SIZE,
                            BASE_MARKER_ICON_SIZE,
                            BASE_MARKER_ICON_SIZE,
                            BASE_MARKER_ICON_SIZE);
                }

                RenderSystem.setShaderColor(1f, 1f, 1f, 1.0f);
            }
        }
    }

    /**
     * Return the filtered marker locations for the current dimension/type, reusing the list while the backing
     * location list instance is unchanged. Location visibility remains live because the cached list stores objects.
     *
     * @param dimensionId The dimension id used to filter locations.
     * @param typeKey     The marker type key used to filter locations, or null for all types.
     * @return The cached filtered marker list for the current dimension/type tuple.
     */
    private List<LocationClient> getCachedMarkerLocations(String dimensionId, @Nullable String typeKey) {

        var backingLocations = ArdaMapsClient.CONFIG.getLocationConfig().getLocations();
        if (cachedMarkerBackingLocations == backingLocations
                && Objects.equals(cachedMarkerDimensionId, dimensionId)
                && Objects.equals(cachedMarkerTypeKey, typeKey)) {
            return cachedMarkerLocations;
        }

        cachedMarkerBackingLocations = backingLocations;
        cachedMarkerDimensionId = dimensionId;
        cachedMarkerTypeKey = typeKey;
        cachedMarkerLocations = ArdaMapsClient.CONFIG.getLocations(dimensionId, typeKey);
        return cachedMarkerLocations;
    }

    /**
     * Render a single location marker on the map.
     *
     * @param context      The draw context for the current frame.
     * @param textRenderer The text renderer used for focused marker labels.
     * @param location     The location being rendered.
     * @param xPos         The screen-space X position of the marker background.
     * @param yPos         The screen-space Y position of the marker background.
     * @param focused      True when the marker should render its focused highlight and label.
     * @param outOfRange   True when the marker lies outside the currently selected vertical range.
     */
    private void renderMarker(DrawContext context, TextRenderer textRenderer, LocationClient location,
                              int xPos, int yPos, boolean focused, boolean outOfRange) {

        var iconXPos = xPos + markerIconXOffset;
        var iconYPos = yPos + markerIconYOffset;

        Identifier icon = location.getIcon();
        int color = outOfRange ? withOpacity(location.getColor()) : location.getColor();
        int highlightColor = outOfRange ? withOpacity(location.getHighlightColor()) : location.getHighlightColor();
        float markerOpacity = outOfRange ? MARKER_OUT_OF_RANGE_OPACITY : 1f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        if (focused) {

            var screenX = xPos + halfMarkerSize;
            var screenY = yPos + markerBackgroundSize;

            context.fill(xPos + markerInset, yPos + markerInset, xPos + markerBackgroundSize - markerInset,
                    yPos + markerBackgroundSize - markerInset, highlightColor);

            var text = location.getName();
            var textX = screenX - textRenderer.getWidth(text) / 2;
            context.drawText(
                    textRenderer,
                    text,
                    textX,
                    screenY + textRenderer.fontHeight / 2,
                    ModConstants.COLOR_WHITE,
                    false);

        } else {

            context.fill(xPos + markerInset, yPos + markerInset, xPos + markerBackgroundSize - markerInset,
                    yPos + markerBackgroundSize - markerInset, color);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, markerOpacity);

        if (location.isVisited())
            context.drawSprite(xPos,
                    yPos,
                    0,
                    markerBackgroundSize,
                    markerBackgroundSize,
                    IconSpriteAtlas.retrieveSprite(ModConstants.MAP_MARKER_VISITED_ICON));
        else
            context.drawSprite(xPos,
                    yPos,
                    0,
                    markerBackgroundSize,
                    markerBackgroundSize,
                    IconSpriteAtlas.retrieveSprite(ModConstants.MAP_MARKER_ICON));

        context.drawSprite(iconXPos, iconYPos, 0, markerIconSize, markerIconSize, IconSpriteAtlas.retrieveSprite(icon));

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /**
     * Update location marker sizes for the current camera zoom.
     *
     * @param mapCamera The camera whose zoom state controls marker scaling.
     */
    private void updateMarkerSizes(MapCamera mapCamera) {

        float zoomScale = markerZoomScale(mapCamera.identityRelativeScale(), mapCamera.minIdentityRelativeScale());
        markerBackgroundSize = scaled(BASE_MARKER_BACKGROUND_SIZE, zoomScale);
        halfMarkerSize = markerBackgroundSize / 2;
        markerIconSize = scaled(BASE_MARKER_ICON_SIZE, zoomScale);
        markerIconXOffset = scaled(BASE_MARKER_ICON_X_OFFSET, zoomScale);
        markerIconYOffset = scaled(BASE_MARKER_ICON_Y_OFFSET, zoomScale);
        markerInset = scaled(BASE_MARKER_INSET, zoomScale);
    }

    /**
     * Scale a base pixel value while keeping it visible.
     *
     * @param value The base pixel value.
     * @param scale The scale multiplier.
     * @return The scaled pixel value, with a minimum of one pixel.
     */
    private static int scaled(int value, float scale) {

        return Math.max(1, Math.round(value * scale));
    }

    /**
     * Compute the marker scale for a camera zoom relative to identity and minimum zoom.
     *
     * @param identityRelativeScale    The current camera scale relative to identity zoom.
     * @param minIdentityRelativeScale The minimum camera scale relative to identity zoom.
     * @return The marker scale for the current zoom.
     */
    public static float markerZoomScale(double identityRelativeScale, double minIdentityRelativeScale) {

        if (Double.isNaN(identityRelativeScale) || Double.isNaN(minIdentityRelativeScale)
                || identityRelativeScale <= 0 || minIdentityRelativeScale <= 0
                || minIdentityRelativeScale >= 1 || identityRelativeScale >= 1) {
            return 1f;
        }

        if (identityRelativeScale <= minIdentityRelativeScale) {
            return MARKER_MIN_ZOOM_SCALE;
        }

        double zoomProgress = Math.log(identityRelativeScale) / Math.log(minIdentityRelativeScale);
        return (float) (1.0 - zoomProgress * (1.0 - MARKER_MIN_ZOOM_SCALE));
    }

    /**
     * Applies an opacity multiplier to an ARGB colour.
     *
     * @param argb The source ARGB colour.
     * @return The same colour with its alpha scaled by {@link #MARKER_OUT_OF_RANGE_OPACITY}.
     */
    private static int withOpacity(int argb) {

        int alpha = (argb >>> 24) & 0xFF;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(alpha * MapMarkerRenderer.MARKER_OUT_OF_RANGE_OPACITY)));
        return (argb & 0x00FFFFFF) | (adjustedAlpha << 24);
    }

    /**
     * @return The currently hovered location, or null.
     */
    public @Nullable LocationClient getMouseOverLocation() {

        return mouseOverLocation;
    }

    /**
     * @return The currently hovered waypoint, or null.
     */
    public @Nullable Waypoint getMouseOverWaypoint() {

        return mouseOverWaypoint;
    }

    /** Marker render data deferred until after the non-hovered marker pass. */
    private record DeferredMarker(int x, int y, LocationClient location) {
    }
}
