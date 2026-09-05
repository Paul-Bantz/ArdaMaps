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
import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import com.duom.ardamaps.gui.map.PlayerIcon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Renders map markers, the player marker, and waypoints for {@link com.duom.ardamaps.gui.screens.MapScreen}.
 */
@Environment(EnvType.CLIENT)
public class MapMarkerRenderer {

    /** Rendered player marker scale factor */
    private static final float PLAYER_MARKER_SCALE = .25f;

    /** Rendered map marker scale factor */
    private static final float MARKER_SCALE = .6f;

    /** Base rendered map marker size in pixels at identity zoom and closer. */
    private static final int BASE_MARKER_BACKGROUND_SIZE = (int) (MarkersManager.get().mapMarkerBackgroundSize() * MARKER_SCALE);

    /** Base rendered map marker icon size in pixels at identity zoom and closer. */
    private static final int BASE_MARKER_ICON_SIZE = (int) (MarkersManager.get().mapMarkerIconSize() * MARKER_SCALE);

    /** Fraction of the marker highlight texture covered by its visible disc. */
    private static final float MARKER_HIGHLIGHT_DISC_RATIO = 38f / 48f;

    /** Pixel padding that keeps marker icons inside the visible highlight disc. */
    private static final int MARKER_ICON_DISC_PADDING = 8;

    /** Base rendered marker icon size contained within the highlight disc, at identity zoom. */
    private static final int BASE_MARKER_ICON_DISC_SIZE =
            Math.max(1, Math.round(BASE_MARKER_BACKGROUND_SIZE * MARKER_HIGHLIGHT_DISC_RATIO)
                    - MARKER_ICON_DISC_PADDING);

    /** Opacity applied to markers outside the currently displayed vertical range. */
    private static final float MARKER_OUT_OF_RANGE_OPACITY = 0.25f;

    /** Minimum fraction of their regular size markers shrink to when fully zoomed out. */
    private static final float MARKER_MIN_ZOOM_SCALE = .25f;

    /** Reusable buffer for markers currently under the mouse cursor. */
    private final List<DeferredMarker> mouseOverMarkers = new ArrayList<>();

    /** Current frame marker background size. */
    private int markerBackgroundSize = BASE_MARKER_BACKGROUND_SIZE;

    /** Current frame marker half-size, used for centering. */
    private int halfMarkerSize = markerBackgroundSize / 2;

    /** Current frame marker icon size. */
    private int markerIconSize = BASE_MARKER_ICON_DISC_SIZE;

    /** Current frame marker icon offset from the marker background edge. */
    private int markerIconOffset = (markerBackgroundSize - markerIconSize) / 2;

    /** Backing location list used by the cached marker-filter result. Compared by reference identity. */
    private List<LocationClient> cachedMarkerBackingLocations;

    /** Dimension key used by the cached marker-filter result. */
    private String cachedMarkerDimensionId;

    /** Marker type key set used by the cached marker-filter result. */
    private Set<String> cachedMarkerTypeKeys;

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
     * @param enabledTypeKeys         The enabled marker type filter keys, or null to render all marker types.
     * @param mouseOverWidgets        True when the mouse is currently over an interactive map widget.
     * @param framePadding            The padding used by frame hit-testing.
     * @param mouseX                  The current mouse X coordinate.
     * @param mouseY                  The current mouse Y coordinate.
     */
    public void render(GuiGraphicsExtractor context, Font textRenderer, MapCamera mapCamera, MapFrameRenderer mapFrameRenderer,
                       @Nullable MapLayerRange selectedRange, @Nullable Vec3d focusedLocationPosition,
                       @Nullable Set<String> enabledTypeKeys, boolean mouseOverWidgets, int framePadding, int mouseX, int mouseY) {

        mouseOverLocation = null;
        mouseOverWaypoint = null;

        updateMarkerSizes(mapCamera);

        renderMarkers(context, textRenderer, mapCamera, mapFrameRenderer, selectedRange, focusedLocationPosition,
                enabledTypeKeys, mouseOverWidgets, mouseX, mouseY);
        renderPlayerMarker(context, mapCamera, mapFrameRenderer, selectedRange, framePadding);
        renderWaypoint(context, textRenderer, mapCamera, mapFrameRenderer, framePadding, mouseX, mouseY);
    }

    /**
     * Updates per-frame marker dimensions from the camera zoom.
     *
     * @param mapCamera The active map camera.
     */
    private void updateMarkerSizes(MapCamera mapCamera) {

        float scale = markerZoomScale(mapCamera.identityRelativeScale(), mapCamera.minIdentityRelativeScale());
        markerBackgroundSize = scaled(BASE_MARKER_BACKGROUND_SIZE, scale);
        halfMarkerSize = markerBackgroundSize / 2;
        markerIconSize = scaled(BASE_MARKER_ICON_DISC_SIZE, scale);
        markerIconOffset = (markerBackgroundSize - markerIconSize) / 2;
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
     * @param enabledTypeKeys         The enabled marker type filter keys, or null to render all marker types.
     * @param mouseOverWidgets        True when the mouse is currently over an interactive map widget.
     * @param mouseX                  The current mouse X coordinate.
     * @param mouseY                  The current mouse Y coordinate.
     */
    private void renderMarkers(GuiGraphicsExtractor context, Font textRenderer, MapCamera mapCamera, MapFrameRenderer mapFrameRenderer,
                               @Nullable MapLayerRange selectedRange, @Nullable Vec3d focusedLocationPosition,
                               @Nullable Set<String> enabledTypeKeys, boolean mouseOverWidgets, int mouseX, int mouseY) {

        var dimensionId = mapCamera.getDimension().getId();
        var locations = getCachedMarkerLocations(dimensionId, enabledTypeKeys);

        boolean revealAll = ArdaMapsClient.CONFIG.isMapRevealAll();

        DeferredMarker focused = null;
        mouseOverMarkers.clear();

        for (var location : locations) {

            if (location.getPosition().x() == 0 && location.getPosition().z() == 0) continue;
            if (!revealAll && !location.isVisible()) continue;

            var landmarkScreenPos = mapCamera.worldToScreenCoordinates(
                    location.getPosition().x(), location.getPosition().z());

            int screenX = (int) landmarkScreenPos.x();
            int screenY = (int) landmarkScreenPos.y();

            if (!mapFrameRenderer.coordinatesInFrame(screenX, screenY, -markerBackgroundSize)) continue;

            var xPos = screenX - halfMarkerSize;
            var yPos = screenY - markerBackgroundSize;

            var isMouseOver = mouseX > xPos && mouseX < xPos + markerBackgroundSize
                    && mouseY > yPos && mouseY < yPos + markerBackgroundSize
                    && !mouseOverWidgets;

            var isFocused = Objects.equals(location.getPosition(), focusedLocationPosition);
            var outOfRange = selectedRange != null && !selectedRange.containsY(location.getPosition().y());

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
                    selectedRange != null && !selectedRange.containsY(location.getPosition().y()));

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
    private void renderPlayerMarker(GuiGraphicsExtractor context, MapCamera mapCamera, MapFrameRenderer mapFrameRenderer,
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

        context.blit(RenderPipelines.GUI_TEXTURED, iconImage,
                screenX,
                screenZ,
                0, 0,
                iconSize, iconSize,
                PlayerIcon.ICON_SIZE, PlayerIcon.ICON_SIZE,
                PlayerIcon.ICON_SIZE, PlayerIcon.ICON_SIZE,
                outOfRange ? GuiTextures.withAlpha(ModConstants.COLOR_WHITE, MARKER_OUT_OF_RANGE_OPACITY) : ModConstants.COLOR_WHITE
        );
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
    @SuppressWarnings({"ConstantValue"})
    private void renderWaypoint(GuiGraphicsExtractor context, Font textRenderer, MapCamera mapCamera, MapFrameRenderer mapFrameRenderer,
                                int framePadding, int mouseX, int mouseY) {

        var waypoints = ArdaMapsClient.CONFIG.getWaypoints(mapCamera.getDimension().getId());

        for (var waypoint : waypoints) {

            var waypointScreenPos = mapCamera.worldToScreenCoordinates(waypoint.getPosition());

            int waypointIconSize = BASE_MARKER_ICON_SIZE;
            int halfIconSize = waypointIconSize / 2;

            int screenX = (int) waypointScreenPos.x() - halfIconSize;
            int screenY = (int) waypointScreenPos.y() - halfIconSize;

            if (mouseOverWaypoint == null
                    && mouseX >= screenX
                    && mouseX <= screenX + waypointIconSize
                    && mouseY >= screenY
                    && mouseY <= screenY + waypointIconSize) {

                mouseOverWaypoint = waypoint;
                context.setTooltipForNextFrame(textRenderer, Component.literal(waypoint.text()), mouseX, mouseY);
            }

            if (mapFrameRenderer.coordinatesInFrame(screenX, screenY, framePadding) && waypoint.icon() != null) {

                var iconIdentifier = ModConstants.id(waypoint.icon());
                var icon = IconSpriteAtlas.retrieveSprite(iconIdentifier);

                if (icon != null
                        && icon.contents() != null
                        && !Objects.equals(icon.contents().name(), MissingTextureAtlasSprite.getLocation())) {

                    context.blitSprite(RenderPipelines.GUI_TEXTURED, icon, screenX, screenY, waypointIconSize, waypointIconSize,
                            GuiTextures.argb(waypoint.r(), waypoint.g(), waypoint.b(), 1.0f));

                } else {

                    context.blit(RenderPipelines.GUI_TEXTURED, iconIdentifier, screenX, screenY, 0, 0,
                            waypointIconSize, waypointIconSize, waypointIconSize, waypointIconSize,
                            GuiTextures.argb(waypoint.r(), waypoint.g(), waypoint.b(), 1.0f));
                }

            }
        }
    }

    /**
     * Marker size multiplier for the current camera zoom. Full size at identity zoom and closer;
     * when zoomed out, shrinks linearly with zoom level down to {@link #MARKER_MIN_ZOOM_SCALE}
     * at the point where the camera can no longer zoom out.
     *
     * @param identityRelativeScale    Camera scale relative to identity zoom.
     * @param minIdentityRelativeScale Camera scale relative to identity zoom at the zoom-out limit.
     * @return Multiplier in [MARKER_MIN_ZOOM_SCALE, 1].
     */
    public static float markerZoomScale(double identityRelativeScale, double minIdentityRelativeScale) {

        if (Double.isNaN(identityRelativeScale) || identityRelativeScale >= 1.0) return 1f;
        if (Double.isNaN(minIdentityRelativeScale) || minIdentityRelativeScale >= 1.0) return 1f;
        if (identityRelativeScale <= 0) return MARKER_MIN_ZOOM_SCALE;

        double t = Math.log(identityRelativeScale) / Math.log(minIdentityRelativeScale);
        t = Math.min(1.0, Math.max(0.0, t));

        return (float) (1.0 + t * (MARKER_MIN_ZOOM_SCALE - 1.0));
    }

    /**
     * Scales a pixel size while keeping blits and fills from collapsing to zero dimensions.
     *
     * @param base  Base pixel size.
     * @param scale Scale multiplier.
     * @return Scaled pixel size of at least one pixel.
     */
    @SuppressWarnings("SameParameterValue")
    private static int scaled(int base, float scale) {

        return Math.max(1, Math.round(base * scale));
    }

    /**
     * Return the filtered marker locations for the current dimension/type set, reusing the list while the backing
     * location list instance is unchanged. Location visibility remains live because the cached list stores objects.
     *
     * @param dimensionId     The dimension id used to filter locations.
     * @param enabledTypeKeys The marker type keys used to filter locations, or null for all types.
     * @return The cached filtered marker list for the current dimension/type tuple.
     */
    private List<LocationClient> getCachedMarkerLocations(String dimensionId, @Nullable Set<String> enabledTypeKeys) {

        var backingLocations = ArdaMapsClient.CONFIG.getLocationConfig().getLocations();
        Set<String> copiedTypeKeys = enabledTypeKeys == null ? null : new HashSet<>(enabledTypeKeys);
        if (cachedMarkerBackingLocations == backingLocations
                && Objects.equals(cachedMarkerDimensionId, dimensionId)
                && Objects.equals(cachedMarkerTypeKeys, copiedTypeKeys)) {
            return cachedMarkerLocations;
        }

        cachedMarkerBackingLocations = backingLocations;
        cachedMarkerDimensionId = dimensionId;
        cachedMarkerTypeKeys = copiedTypeKeys;
        cachedMarkerLocations = ArdaMapsClient.CONFIG.getLocations(dimensionId, null).stream()
                .filter(location -> copiedTypeKeys == null
                        || copiedTypeKeys.contains(MarkersManager.get().markerTypeKey(location.getTypes())))
                .toList();
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
    private void renderMarker(GuiGraphicsExtractor context, Font textRenderer, LocationClient location,
                              int xPos, int yPos, boolean focused, boolean outOfRange) {

        var iconXPos = xPos + markerIconOffset;
        var iconYPos = yPos + markerIconOffset;

        Identifier icon = location.getIcon();
        int color = outOfRange ? withOpacity(location.getColor()) : location.getColor();
        int highlightColor = outOfRange ? withOpacity(location.getHighlightColor()) : location.getHighlightColor();
        float markerOpacity = outOfRange ? MARKER_OUT_OF_RANGE_OPACITY : 1f;
        int highlightTint = focused ? highlightColor : color;

        context.blitSprite(RenderPipelines.GUI_TEXTURED,
                IconSpriteAtlas.retrieveSprite(ModConstants.MAP_MARKER_HIGHLIGHT_ICON),
                xPos, yPos, markerBackgroundSize, markerBackgroundSize, highlightTint);

        if (focused) {

            var screenX = xPos + halfMarkerSize;
            var screenY = yPos + markerBackgroundSize;

            var text = location.getName();
            var textX = screenX - textRenderer.width(text) / 2;
            context.text(
                    textRenderer,
                    text,
                    textX,
                    screenY + textRenderer.lineHeight / 2,
                    ModConstants.COLOR_WHITE,
                    false);
        }

        int markerColor = GuiTextures.withAlpha(ModConstants.COLOR_WHITE, markerOpacity);
        if (location.isVisited())
            context.blitSprite(RenderPipelines.GUI_TEXTURED, IconSpriteAtlas.retrieveSprite(ModConstants.MAP_MARKER_VISITED_ICON), xPos, yPos, markerBackgroundSize, markerBackgroundSize, markerColor);
        else
            context.blitSprite(RenderPipelines.GUI_TEXTURED, IconSpriteAtlas.retrieveSprite(ModConstants.MAP_MARKER_ICON), xPos, yPos, markerBackgroundSize, markerBackgroundSize, markerColor);

        context.blitSprite(RenderPipelines.GUI_TEXTURED, IconSpriteAtlas.retrieveSprite(icon), iconXPos, iconYPos, markerIconSize, markerIconSize, markerColor);

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
