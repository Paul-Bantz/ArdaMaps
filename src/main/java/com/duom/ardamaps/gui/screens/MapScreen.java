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

package com.duom.ardamaps.gui.screens;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.ExplorationState;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.core.data.map.cameras.*;
import com.duom.ardamaps.core.data.map.markers.MarkersManager;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.server.PlayerTeleportPacket;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import com.duom.ardamaps.gui.map.PlayerIcon;
import com.duom.ardamaps.gui.map.rendering.*;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.screens.rendering.MapFrameRenderer;
import com.duom.ardamaps.gui.widgets.*;
import com.duom.ardamaps.gui.widgets.builders.MapDropdownBuilder;
import com.duom.ardamaps.gui.widgets.builders.StyledButtonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Screen for displaying the world map. This class is responsible for rendering the map, handling user interactions,
 * and managing the map camera : panning, zooming, switching map layers. This is the main orchestrator for map rendering
 */
@Environment(EnvType.CLIENT)
public class MapScreen extends ArdaMapsScreen {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(MapScreen.class);

    /** Rendered player marker scale factor */
    private static final float PLAYER_MARKER_SCALE = .25f;

    /** Rendered map marker scale factor */
    private static final float MARKER_SCALE = .6f;

    /** Rendered map marker size in pixels */
    private static final int MARKER_BACKGROUND_SIZE = (int) (MarkersManager.get().mapMarkerBackgroundSize() * MARKER_SCALE);

    /** Precalculated half size of the marker background, used for centering */
    private static final int HALF_MARKER_SIZE = MARKER_BACKGROUND_SIZE / 2;

    /** Rendered map marker icon size in pixels */
    private static final int MARKER_ICON_SIZE = (int) (MarkersManager.get().mapMarkerIconSize() * MARKER_SCALE);

    /** Precalculated x offset to position the marker icon within the marker background */
    private static final int MARKER_ICON_X_OFFSET = (int) (MarkersManager.get().mapMarkerIconXOffset() * MARKER_SCALE);

    /** Precalculated y offset to position the marker icon within the marker background */
    private static final int MARKER_ICON_Y_OFFSET = (int) (MarkersManager.get().mapMarkerIconYOffset() * MARKER_SCALE);

    /** Precalculated half size of the Ardacraft logo, used for centering the placeholder */
    private static final int ARDACRAFT_LOGO_HALF_SIZE_WITH_SPACING = ARDACRAFT_LOGO_HALF_SIZE + Client.mc().textRenderer.fontHeight;

    /** Precalculated click threshold squared for marker interaction (to avoid sqrt calculations) */
    private static final double CLICK_THRESHOLD_SQUARED = 4.0;

    /** Padding from the edges of the map frame for map rendering and interactions */
    private static final int MAP_FRAME_PADDING = 10;

    /** Total duration of the pan-to-player animation in milliseconds */
    private static final long ANIMATION_DURATION_MS = 1000L;

    /** Renderer for map frame and decorations */
    private final MapFrameRenderer mapFrameRenderer = new MapFrameRenderer();

    /** Cached text for unknown region tooltip to avoid repeated translations and allocations */
    private final String unknownRegionText = Text.translatable("ardamaps.client.map.screen.unknown.region").getString();

    /** Ordered history of locations displayed in the side panel (max 10 entries) */
    private final List<LocationClient> locationHistory = new ArrayList<>();

    /** Flag indicating whether the map is currently panning / animating to the player's position */
    private boolean animatingToPlayer = false;

    /** Animation start time in milliseconds, used for tracking animation progress when panning to player */
    private long animationStartMs;

    /** Starting X coordinate of the map camera when beginning a pan-to-player animation */
    private double animStartX;

    /** Starting Z coordinate of the map camera when beginning a pan-to-player animation */
    private double animStartZ;

    /** Target X coordinate of the map camera when panning to player */
    private double animTargetX;

    /** Target Z coordinate of the map camera when panning to player */
    private double animTargetZ;

    /** Initial zoom level when panning and zooming to player or location */
    private double animStartZoom;

    /** Target zoom level when panning and zooming to player or location */
    private double animTargetZoom;

    /** The map layer selection dropdown */
    private MapDropdownWidget<MapLayerDefinition, TextIdentifierPairItem> layerSelectionDropdown;

    /** Dimension selection dropdown */
    private MapDropdownWidget<Dimension, TextIdentifierPairItem> dimensionSelectionDropdown;

    /** The currently hovered location, used for displaying context menu and side panel */
    private LocationClient mouseOverLocation = null;

    /** The currently hovered waypoint, used for displaying context menu entries */
    private Waypoint mouseOverWaypoint = null;

    /** Markers filtering dropdown */
    private MapDropdownWidget<MarkerInfo, TextIdentifierPairItem> markersSelectionDropdown;

    /** Coordinates display button, also used to pan back to player position when clicked */
    private StyledButtonWidget coordinatesButton;

    /** Flag indicating whether the user currently dragging the map */
    private boolean dragging = false;

    /** The currently displayed map renderable */
    private MapRenderable mapRenderer;

    /** Right click Context menu for the map */
    private ContextMenu mapContextMenu;

    /** Side panel for location context */
    private SidePanelWidget locationContextPanel;

    /** Index into {@link #locationHistory} pointing at the currently displayed entry (-1 = no history) */
    private int historyIndex = -1;

    /** Cached region name under the mouse cursor (null = no region / outside map) */
    @Nullable
    private String regionNameUnderMouse = unknownRegionText;

    /** Last texture-space X used for the region lookup - avoids redundant lookups */
    private int lastRegionTexX = Integer.MIN_VALUE;

    /** Last texture-space Z used for the region lookup - avoids redundant lookups */
    private int lastRegionTexZ = Integer.MIN_VALUE;

    /** Mouse position when click started - used for panning tracking */
    private double clickStartX;

    /** Mouse position when click started - used for panning tracking */
    private double clickStartY;

    /** The exploration state for the given rendered map */
    private PlayerExploration explorationState;

    /**
     * The dimension selected in the UI dropdown. Tracks which dimension the user has chosen so that
     * {@link #loadMapLayer} can pre-build the correct camera. Once a layer is loaded, the authoritative
     * dimension is {@code getCamera().getDimension()} - this field is only the UI-selection staging area.
     */
    private Dimension selectedDimension;

    /**
     * Constructor for a new MapScreen instance
     *
     * @param parent The parent screen to return to when exiting the map screen
     */
    public MapScreen(Screen parent) {

        super(parent, Text.translatable("ardamaps.client.map.screen.map"));
    }

    /**
     * Initialize the map screen
     * Set up the map camera, map renderer, and GUI components. If a map layer is already selected in the config,
     * it will be loaded and displayed. Define the width and height of the map viewport
     */
    @Override
    protected void init() {

        super.init();

        var previousSidePanel = locationContextPanel;

        configureCoordinatesButton();
        configureDimensionSelectionDropDown();
        configureMapLayerSelectionDropDown();
        configureMarkersDisplayDropdown();

        var camera = getCamera();

        if (camera != null)
            camera.setViewportSize(width, height);

        if (previousSidePanel != null)
            locationContextPanel = previousSidePanel;
    }

    /**
     * Configure the coordinates display button at the bottom of the screen
     * Displays the current coordinates of the player or mouse cursor when hovering over the map
     * Clicking the button will move the camera to the player's current position
     */
    private void configureCoordinatesButton() {

        coordinatesButton = StyledButtonBuilder.create()
                .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                .setOnClick(this::panCameraToPlayer)
                .build();
        coordinatesButton.visible = getCamera() != null;

        updateMapButtonPositions();

        addDrawableChild(coordinatesButton);
    }

    /**
     * Configure the dimension selection dropdown at the top of the screen
     * Allows switching between dimensions if multiple are available in the config
     */
    private void configureDimensionSelectionDropDown() {

        List<Dimension> dimensions = ArdaMapsClient.CONFIG.getDimensions();

        var defaultSelection = selectedDimension != null ? selectedDimension : Client.currentDimension();

        // Default may be null if the dimension was not configured server-side. Handle gracefully.
        if (defaultSelection == null && !dimensions.isEmpty()) defaultSelection = dimensions.get(0);

        dimensionSelectionDropdown = MapDropdownBuilder.<Dimension, TextIdentifierPairItem>create()
                .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                .setOptions(new ArrayList<>(dimensions))
                .setOptionDisplay(dim -> new TextIdentifierPairItem(
                        dim.getName(),
                        null))
                .setDisplayIcons(false)
                .setSelected(defaultSelection)
                .setAllowNull(false)
                .setDisplayArrows(false)
                .setDisplayAsSprite(true)
                .setMaxVisibleOptions(6)
                .setOnSelect(this::dimensionSelectionChanged)
                .setExpandDirection(DropdownWidget.ExpandDirection.UP_RIGHT)
                .setPlaceholderIcon(ModConstants.ICON_ALL)
                .build();

        selectedDimension = defaultSelection;

        updateMapButtonPositions();

        addDrawableChild(dimensionSelectionDropdown);
    }

    /**
     * Callback for when the dimension selection changes.
     * Updates the currently displayed map layer options based on the selected dimension.
     *
     * @param newDimension The newly selected dimension
     */
    private void dimensionSelectionChanged(Dimension newDimension) {

        selectedDimension = newDimension;
        configureMapLayerSelectionDropDown();
    }

    /**
     * Configure the map layer selection dropdown
     */
    private void configureMapLayerSelectionDropDown() {

        List<MapLayerDefinition> mapLayers = new ArrayList<>();

        /*
         This can happen if the client has not yet received a dimension configuration from the server or the server
         is misconfigured. Provide a bare empty list to avoid crashing the client.
         */
        if (selectedDimension != null)
            mapLayers = selectedDimension.getMapLayers();

        var provider = ArdaMapsClient.getHttpImageProvider();

        // Preload icons
        for (MapLayerDefinition layer : mapLayers)
            provider.loadImage(layer.icon());

        // Provide a default grid layer if no layers are defined for the dimension to ensure the map is minimally functional
        if (mapLayers.isEmpty()) mapLayers.add(MapLayerDefinition.DEFAULT_GRID_LAYER);

        MapLayerDefinition previousSelection = null;

        // Preserve selection if not null
        if (layerSelectionDropdown != null)
            previousSelection = mapLayers.contains(layerSelectionDropdown.getSelected()) ?  layerSelectionDropdown.getSelected() : null;

        layerSelectionDropdown = MapDropdownBuilder.<MapLayerDefinition, TextIdentifierPairItem>create()
                .setSize(ModConstants.SMALL_SQUARED_BUTTON_SIZE, ModConstants.SMALL_SQUARED_BUTTON_SIZE)
                .setOptions(mapLayers)
                .setOptionDisplay(item ->
                        item == null ?
                                new TextIdentifierPairItem(Text.translatable("ardamaps.client.map.screen.layer.dropdown.empty"), null) :
                                new TextIdentifierPairItem(item.layer(), provider.getTexture(item.icon()))
                )
                .setOnSelect(this::mapLayerSelectionChanged)
                .setDisplayIcons(true)
                .setDisplayLabels(false)
                .setSelected(previousSelection != null ? previousSelection : mapLayers.get(0))
                .setDisplayArrows(false)
                .setExpandDirection(DropdownWidget.ExpandDirection.UP_LEFT)
                .build();

        addDrawableChild(layerSelectionDropdown);

        // Hide if only one layer available, no need to show a dropdown for a single option
        layerSelectionDropdown.visible = mapLayers.size() > 1;

        if (layerSelectionDropdown.getSelected() != null
                && (previousSelection == null
                || !Objects.equals(previousSelection, layerSelectionDropdown.getSelected())))
            mapLayerSelectionChanged(layerSelectionDropdown.getSelected());
    }

    /**
     * Configure the markers display dropdown
     */
    private void configureMarkersDisplayDropdown() {

        var nullValue = new TextIdentifierPairItem(Text.translatable("ardamaps.client.map.screen.all.markers"), null);

        var list = MarkersManager.get().types().entrySet().stream()
                .map(entry -> {
                    var value = entry.getValue();
                    return new MarkerInfo(entry.getKey(), value.name(), value.icon(), value.color(), value.highlightColor());
                })
                .sorted(Comparator.comparing(MarkerInfo::displayName))
                .toList();

        markersSelectionDropdown = MapDropdownBuilder.<MarkerInfo, TextIdentifierPairItem>create()
                .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                .setOptions(list)
                .setOptionDisplay(item ->
                        item != null ?
                                new TextIdentifierPairItem(
                                        MarkersManager.get().getMarkerType(item.key).name(),
                                        MarkersManager.get().getMarkerType(item.key).icon()) : nullValue)
                .setDisplayIcons(true)
                .setSelected(null)
                .setAllowNull(true)
                .setDisplayArrows(false)
                .setDisplayAsSprite(true)
                .setMaxVisibleOptions(6)
                .setExpandDirection(DropdownWidget.ExpandDirection.DOWN_RIGHT)
                .setPlaceholderText(Text.translatable("ardamaps.client.map.screen.all.markers"))
                .setPlaceholderIcon(ModConstants.ICON_ALL)
                .build();

        // If there is a dimension available, a map can be displayed, the dropdown is relevant.
        markersSelectionDropdown.visible = selectedDimension != null;

        updateMapButtonPositions();

        addDrawableChild(markersSelectionDropdown);
    }

    /**
     * Smoothly move the map camera to the player's current position
     */
    private void panCameraToPlayer() {

        if (Client.player() == null) return;

        if (getCamera() == null) return;

        panCameraToMapCoordinates(Client.playerPosition2d(), getCamera().getIdentityZoom());
    }

    /**
     * Callback for when the map layer selection changes
     *
     * @param mapLayerDefinition The selected map layer definition (can be null if no selection)
     */
    private void mapLayerSelectionChanged(@NotNull MapLayerDefinition mapLayerDefinition) {

        // Reset side-panel state when switching layers/dimensions
        locationContextPanel = null;
        locationHistory.clear();
        historyIndex = -1;

        // Capture the current visual pixels-per-block so we can match zoom on the new map
        final double capturedRenderScale = (getCamera() != null) ? getCamera().getVisualPixelsPerBlock() : Double.NaN;

        CompletableFuture.supplyAsync(() -> loadMapLayer(mapLayerDefinition, capturedRenderScale), ArdaMaps.IO_EXECUTOR)
                .thenAcceptAsync(this::layerLoaded)
                .whenComplete((result, ex) -> {
                    if (ex != null)
                        LOGGER.error("Failed to load map layer", ex);
                });
    }

    /**
     * Callback for when a new map layer has finished loading. Sets the new map renderable and updates the camera and GUI state accordingly.
     *
     * @param mapRenderable The loaded MapRenderable, or null if loading failed
     */
    private void layerLoaded(@Nullable MapRenderable mapRenderable) {

        LOGGER.info("Map layer loaded: {}", mapRenderable != null ? "success" : "failed");

        if (mapRenderable != null) {

            mapRenderer = mapRenderable;
            explorationState = ArdaMapsClient.CONFIG.getClientProgress().getExplorationState(mapRenderable.getCamera().getDimension().getId(), true);

            updateMapButtonPositions();
            coordinatesButton.visible = true;
        }
    }

    /**
     * Load the map renderable for the selected map layer definition. This runs in a background thread to avoid blocking the UI.
     *
     * @param mapLayerDefinition  The map layer definition to load
     * @param capturedRenderScale The visual pixels-per-block of the previous map, used to set the zoom level of the new map for a seamless transition
     * @return The loaded MapRenderable, or null if loading failed
     */
    private @Nullable MapRenderable loadMapLayer(@NonNull MapLayerDefinition mapLayerDefinition, double capturedRenderScale) {

        var cameraPosition = Client.playerPosition2d();
        MapRenderable mapRenderable = null;

        if (!Objects.equals(selectedDimension, Client.currentDimension()))
            cameraPosition = new Vec2d(0, 0);

        int cx = (int) cameraPosition.x();
        int cy = (int) cameraPosition.y();

        if (getCamera() != null) {

            cx = (int) getCamera().getWorldX();
            cy = (int) getCamera().getWorldZ();
        }

        switch (mapLayerDefinition.type()) {

            case BLUEMAP -> {
                var camera = new BlueMapCamera(width, height, cx, cy);
                camera.setDimension(selectedDimension);
                mapRenderable = new BlueMapRenderer(camera, textRenderer);
                mapRenderable.configure(mapLayerDefinition, capturedRenderScale);
            }
            case PMTILES -> {
                var camera = new PmTilesMapCamera(width, height, cx, cy);
                camera.setDimension(selectedDimension);
                mapRenderable = new PmTilesRenderer(camera, textRenderer);
                mapRenderable.configure(mapLayerDefinition, capturedRenderScale);
            }
            case WEBP -> {
                var camera = new FlatMapCamera(width, height, cx, cy);
                camera.setDimension(selectedDimension);
                mapRenderable = new WebpRenderer(camera, textRenderer);
                mapRenderable.configure(mapLayerDefinition, capturedRenderScale);
            }
            case GRID -> {
                var camera = new GridCamera(width, height, cx, cy);
                camera.setDimension(selectedDimension);
                mapRenderable = new GridRenderer(camera, textRenderer);
                mapRenderable.configure(mapLayerDefinition, capturedRenderScale);
            }
        }

        return mapRenderable;
    }

    /**
     * Render the map screen, main render loop
     *
     * @param context The draw context
     * @param mouseX  The mouse x position
     * @param mouseY  The mouse y position
     * @param delta   The time since last frame
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        // Client should not be null here
        assert client != null;

        // Reset marker mouseover
        mouseOverLocation = null;

        renderBackground(context);

        if (mapRenderer != null) {

            var mapCamera = getCamera();
            var contentArea = getPaddedContentArea();

            context.enableScissor(
                    contentArea.topLeftX(), contentArea.topLeftY(),
                    contentArea.topLeftX() + contentArea.guiWidth(),
                    contentArea.topLeftY() + contentArea.guiHeight()
            );

            // Fill the world bounds with a dark background, clipped to content area
            if (mapCamera != null) {

                // Enforce minimum zoom to prevent seeing outside world bounds when map is smaller than content area
                mapCamera.computeZoomLevelToFitContentArea(contentArea.guiWidth(), contentArea.guiHeight());

                // Drive zoom/pan damping every render frame so the animation
                // is truly frame-rate independent and does not jump on frame skips.
                if (!animatingToPlayer)
                    mapCamera.update(client.getLastFrameDuration(), contentArea.topLeftX(), contentArea.topLeftY());

                // Clear background with dark colour - will display if some areas of the map are not covered by tiles
                context.fill(contentArea.topLeftX(),
                        contentArea.topLeftY(),
                        contentArea.topLeftX() + contentArea.guiWidth(),
                        contentArea.topLeftY() + contentArea.guiHeight(),
                        ModConstants.COLOR_DARKER_BLUE);
            }

            mapRenderer.render(context);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            renderMarkers(context, mouseX, mouseY);
            renderPlayerMarker(context);
            renderWaypoint(context, mouseX, mouseY);

            RenderSystem.disableScissor();

            mapFrameRenderer.render(context, contentArea);

            updateCoordinates(mouseX, mouseY);
            updateRegionUnderMouse(mouseX, mouseY);

            RenderSystem.disableBlend();

        } else {
            renderPlaceholder(context);
        }

        super.render(context, mouseX, mouseY, delta);

        // Render context menu if opened
        if (mapContextMenu != null)
            mapContextMenu.render(context, mouseX, mouseY);

        // Render side panel if opened
        if (locationContextPanel != null)
            locationContextPanel.render(context, mouseX, mouseY);

        // Render region name tooltip if hovering over a region
        if (regionNameUnderMouse != null)
            renderRegionName(context);
    }

    /**
     * Convenience accessor to retrieve the camera from the currently loaded map renderer.
     * Using this instead of a dedicated field eliminates a redundant reference that could drift out of sync.
     *
     * @return The {@link MapCamera} of the active renderer, or {@code null} if no map is loaded yet.
     */
    @Nullable
    private MapCamera getCamera() {
        return mapRenderer != null ? mapRenderer.getCamera() : null;
    }

    /**
     * Render points of interest on the map
     *
     * @param context The draw context
     * @param mouseX  The mouse x position
     * @param mouseY  The mouse y position
     */
    private void renderMarkers(DrawContext context, int mouseX, int mouseY) {

        var mapCamera = getCamera();
        if (mapCamera == null) return;

        var selectedLocationType = markersSelectionDropdown.getSelected();
        var locations = ArdaMapsClient.CONFIG.getLocations(
                mapCamera.getDimension().getId(),
                selectedLocationType != null ? selectedLocationType.key : null);

        boolean revealAll = ArdaMapsClient.CONFIG.isMapRevealAll();

        Pair<Vec2d, LocationClient> focused = null;
        List<Pair<Vec2d, LocationClient>> mouseOver = new ArrayList<>();

        for (var location : locations) {

            // Early exit for invalid positions
            if (location.getPosition().x == 0 && location.getPosition().z == 0) continue;

            // Skip locations that are not explored
            if (!revealAll && !location.isVisible()) continue;

            var landmarkScreenPos = mapCamera.worldToScreenCoordinates(
                    location.getPosition().x, location.getPosition().z);

            int screenX = (int) landmarkScreenPos.x();
            int screenY = (int) landmarkScreenPos.y();

            // Skip if outside viewport
            if (!mapFrameRenderer.coordinatesInFrame(screenX, screenY, -MARKER_BACKGROUND_SIZE)) continue;

            var xPos = screenX - HALF_MARKER_SIZE;
            var yPos = screenY - MARKER_BACKGROUND_SIZE;

            var isMouseOver = mouseX > xPos && mouseX < xPos + MARKER_BACKGROUND_SIZE
                    && mouseY > yPos && mouseY < yPos + MARKER_BACKGROUND_SIZE
                    && !mouseOverMapWidgets(mouseX, mouseY);

            var isFocused = locationContextPanel != null
                    && Objects.equals(location.getPosition(), locationContextPanel.getDisplayedLocationPosition());

            if (mouseOverLocation == null && isMouseOver)
                    mouseOverLocation = location;

            // Defer rendering of mouse over and focused markers to avoid overlaps
            if (isMouseOver)    mouseOver.add(new Pair<>(new Vec2d(xPos, yPos), location));
            else if (isFocused) focused    = new Pair<>(new Vec2d(xPos, yPos), location);
            else renderMarker(context, location, xPos, yPos, false);
        }

        // Render mouseover marker on top of focused marker, if any
        if (focused != null)
            renderMarker(context, focused.getRight(), (int) focused.getLeft().x(), (int) focused.getLeft().y(), true);

        // Multiple locations can be mouse-overed at the same time - last one wins
        for (int idx = 0; idx < mouseOver.size(); idx++) {

            var mouseOveredLocation = mouseOver.get(idx);
            var location = mouseOveredLocation.getRight();
            var pos = mouseOveredLocation.getLeft();

            renderMarker(context,
                    location,
                    (int) pos.x(),
                    (int) pos.y(),
                    false);

            if (idx == mouseOver.size() - 1) {
                mouseOverLocation = location;

                renderMarker(context,
                        location,
                        (int) pos.x(),
                        (int) pos.y(),
                        true);
            }
        }
    }

    /**
     * Render the player marker at the centre of the map
     *
     * @param context The draw context
     */
    private void renderPlayerMarker(DrawContext context) {

        var mapCamera = getCamera();
        if (mapCamera == null) return;

        if (!Objects.equals(mapCamera.getDimension(), Client.currentDimension())) return;

        var iconImage = PlayerIcon.getPlayerIcon();
        if (iconImage == null) return;

        var clientPos = Client.playerPosition2d();
        var clientScreenPos = mapCamera.worldToScreenCoordinates(clientPos);

        var iconSize = (int) (PlayerIcon.ICON_SIZE * PLAYER_MARKER_SCALE);
        int halfIconSize = iconSize / 2;

        int screenX = (int) clientScreenPos.x() - halfIconSize;
        int screenZ = (int) clientScreenPos.y() - halfIconSize;

        // Do not render if outside of viewport
        if (!mapFrameRenderer.coordinatesInFrame(screenX, screenZ, MAP_FRAME_PADDING)) {
            return;
        }

        context.fill(screenX, screenZ, screenX + iconSize, screenZ + iconSize, ModConstants.COLOR_DARK_BROWN);
        context.drawTexture(iconImage,
                screenX,
                screenZ,
                iconSize, iconSize,
                PlayerIcon.ICON_SIZE, PlayerIcon.ICON_SIZE,
                PlayerIcon.ICON_SIZE, PlayerIcon.ICON_SIZE,
                PlayerIcon.ICON_SIZE, PlayerIcon.ICON_SIZE
        );
    }

    /**
     * Render the waypoint marker on the map
     *
     * @param context The draw context
     * @param mouseX  The mouse x position
     * @param mouseY  The mouse y position
     */
    private void renderWaypoint(DrawContext context, int mouseX, int mouseY) {

        var mapCamera = getCamera();
        if (mapCamera == null) return;

        mouseOverWaypoint = null;

        var waypoints = ArdaMapsClient.CONFIG.getWaypoints(mapCamera.getDimension().getId());

        for (var waypoint : waypoints) {

            var waypointScreenPos = mapCamera.worldToScreenCoordinates(waypoint.getPosition());

            int halfIconSize = MARKER_ICON_SIZE / 2;

            int screenX = (int) waypointScreenPos.x() - halfIconSize;
            int screenY = (int) waypointScreenPos.y() - halfIconSize;

            if (mouseOverWaypoint == null
                    && mouseX >= screenX
                    && mouseX <= screenX + MARKER_ICON_SIZE
                    && mouseY >= screenY
                    && mouseY <= screenY + MARKER_ICON_SIZE) {

                mouseOverWaypoint = waypoint;
                context.drawTooltip(textRenderer, Text.literal(waypoint.text()), mouseX, mouseY);
            }

            // Do not render if outside of viewport
            if (mapFrameRenderer.coordinatesInFrame(screenX, screenY, MAP_FRAME_PADDING) && waypoint.icon() != null) {

                var icon = IconSpriteAtlas.retrieveSprite(waypoint.icon());

                RenderSystem.setShaderColor(waypoint.r(), waypoint.g(), waypoint.b(), 1.0f);

                if (icon != null
                        && icon.getContents() != null
                        && !Objects.equals(icon.getContents().getId(), MissingSprite.getMissingSpriteId())) {

                    context.drawSprite(screenX, screenY, 0, MARKER_ICON_SIZE, MARKER_ICON_SIZE, icon);

                } else {

                    context.drawTexture(waypoint.icon(), screenX, screenY, 0, 0, MARKER_ICON_SIZE, MARKER_ICON_SIZE, MARKER_ICON_SIZE, MARKER_ICON_SIZE);
                }

                RenderSystem.setShaderColor(1f, 1f, 1f, 1.0f);
            }
        }
    }

    /**
     * Update the coordinates display based on the current mouse position
     * If hovering over the map, show the world coordinates under the cursor, otherwise show player's current coordinates
     *
     * @param mouseX The current mouse x position
     * @param mouseY The current mouse y position
     */
    private void updateCoordinates(int mouseX, int mouseY) {

        var mapCamera = getCamera();
        if (mapCamera == null) return;

        Vec2d worldCoordinates = Client.playerPosition2d();

        if (mapFrameRenderer.coordinatesInFrame(mouseX, mouseY, MAP_FRAME_PADDING))
            worldCoordinates = mapCamera.screenToWorldCoordinates(mouseX, mouseY);

        var coordinatesText = String.format("X:%d, Z:%d", (int) worldCoordinates.x(), (int) worldCoordinates.y());

        coordinatesButton.setMessage(Text.literal(coordinatesText));
    }

    /**
     * Updates {@link #regionNameUnderMouse} based on the current mouse position.
     * Uses dirty-tracking on texture-space coordinates so the actual lookup (array read)
     * only runs when the mouse moves to a different pixel in the lookup texture - O(1) and
     * allocation-free every frame.
     *
     * @param mouseX The current mouse x position (screen space)
     * @param mouseY The current mouse y position (screen space)
     */
    private void updateRegionUnderMouse(int mouseX, int mouseY) {

        var mapCamera = getCamera();
        if (mapCamera == null || !mapFrameRenderer.coordinatesInFrame(mouseX, mouseY, MAP_FRAME_PADDING)) {

            regionNameUnderMouse = null;
            lastRegionTexX = Integer.MIN_VALUE;
            lastRegionTexZ = Integer.MIN_VALUE;
            return;
        }

        var lookup = ArdaMapsClient.CONFIG.getRegionLookupTexture();
        if (lookup == null || lookup.texWidth() == 0 || !Objects.equals(lookup.dimensionId(), mapCamera.getDimension().getId())) {

            regionNameUnderMouse = null;
            return;
        }

        Vec2d world = mapCamera.screenToWorldCoordinates(mouseX, mouseY);

        var mouseOverExploration = explorationState.stateAtWorldPos(world.x(), world.y());

        if (mouseOverExploration.ordinal() < ExplorationState.VISIBLE.ordinal() && !ArdaMapsClient.CONFIG.isMapRevealAll()) {

            regionNameUnderMouse = unknownRegionText;
            lastRegionTexX = Integer.MIN_VALUE;
            lastRegionTexZ = Integer.MIN_VALUE;
            return;
        }

        // Map world coords to texture pixel coords (same formula as getRegionAt, inlined to check dirty)
        int texX = (int) ((world.x() - mapCamera.getDimension().getXMin())
                / (double) mapCamera.getDimension().getWidth() * lookup.texWidth());
        int texZ = (int) ((world.y() - mapCamera.getDimension().getZMin())
                / (double) mapCamera.getDimension().getHeight() * lookup.texHeight());

        // Only re-query when the mouse has moved to a different texel
        if (texX == lastRegionTexX && texZ == lastRegionTexZ) return;

        lastRegionTexX = texX;
        lastRegionTexZ = texZ;
        regionNameUnderMouse = lookup.getRegionAt(mapCamera.getDimension(), world.x(), world.y());
    }

    /**
     * Render placeholder content when no map is selected
     * Ardacraft logo and text
     *
     * @param context The draw context
     */
    private void renderPlaceholder(DrawContext context) {

        var centerX = width / 2;
        var centerY = height / 2;

        context.drawTexture(ModConstants.ARDACRAFT_LOGO,
                centerX - ARDACRAFT_LOGO_HALF_SIZE,
                centerY - ARDACRAFT_LOGO_HALF_SIZE,
                0, 0,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE);

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("ardamaps.client.map.screen.no.map.selected"),
                centerX,
                centerY + ARDACRAFT_LOGO_HALF_SIZE_WITH_SPACING,
                ModConstants.COLOR_WHITE);
    }

    /**
     * Render the region name tooltip at the top of the coordinates button
     *
     * @param context The draw context
     */
    private void renderRegionName(DrawContext context) {

        if (getCamera() == null) return;

        var textWidth = textRenderer.getWidth(regionNameUnderMouse);
        var labelWidth = textWidth + 32;
        var labelHeight = textRenderer.fontHeight + 24;

        var paddedContentArea = getPaddedContentArea();

        var x = paddedContentArea.topLeftX() + 5;
        var y = paddedContentArea.topLeftY() + 5;

        context.drawNineSlicedTexture(ModConstants.MAP_GUI_ELEMENTS,
                x, y,
                labelWidth, labelHeight,
                16,
                16,
                16,
                16,
                96,
                48,
                144, 160);

        context.drawText(
                textRenderer,
                Text.literal(regionNameUnderMouse),
                x + labelWidth / 2 - textWidth / 2,
                y + 12,
                ModConstants.COLOR_DARK_BROWN,
                false);
    }

    /**
     * Render a single location marker on the map
     *
     * @param context  The draw context
     * @param location The location to render
     * @param xPos  The screen x position
     * @param yPos  The screen y position
     */
    private void renderMarker(DrawContext context, LocationClient location,
                              int xPos, int yPos, boolean focused) {

        var iconXPos = xPos + MARKER_ICON_X_OFFSET;
        var iconYPos = yPos + MARKER_ICON_Y_OFFSET;

        Identifier icon = location.getIcon();
        int color = location.getColor();
        int highlightColor = location.getHighlightColor();

        // Enable blend once
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        if (focused) {

            var screenX = xPos + HALF_MARKER_SIZE;
            var screenY = yPos + MARKER_BACKGROUND_SIZE;

            context.fill(xPos + 4, yPos + 4, xPos + MARKER_BACKGROUND_SIZE - 4, yPos + MARKER_BACKGROUND_SIZE - 4, highlightColor);

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

            context.fill(xPos + 4, yPos + 4, xPos + MARKER_BACKGROUND_SIZE - 4, yPos + MARKER_BACKGROUND_SIZE - 4, color);
        }

        if (location.isVisited())
            context.drawSprite(xPos, yPos, 0, MARKER_BACKGROUND_SIZE, MARKER_BACKGROUND_SIZE, IconSpriteAtlas.retrieveSprite(ModConstants.MAP_MARKER_VISITED_ICON));
        else
            context.drawSprite(xPos, yPos, 0, MARKER_BACKGROUND_SIZE, MARKER_BACKGROUND_SIZE, IconSpriteAtlas.retrieveSprite(ModConstants.MAP_MARKER_ICON));

        context.drawSprite(iconXPos, iconYPos, 0, MARKER_ICON_SIZE, MARKER_ICON_SIZE, IconSpriteAtlas.retrieveSprite(icon));

        RenderSystem.disableBlend();
    }

    /**
     * Check if the mouse is over any GUI elements
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @return True if the mouse is over GUI elements
     */
    private boolean mouseOverMapWidgets(double mouseX, double mouseY) {

        return isMouseOverWidget(mouseX, mouseY, locationContextPanel) ||
                isMouseOverWidget(mouseX, mouseY, layerSelectionDropdown) ||
                isMouseOverWidget(mouseX, mouseY, dimensionSelectionDropdown) ||
                isMouseOverWidget(mouseX, mouseY, markersSelectionDropdown) ||
                isMouseOverWidget(mouseX, mouseY, locationContextPanel);
    }

    /**
     * Helper method to check if the mouse is over a specific widget, with null check
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @param widget The widget to check (can be null)
     * @return True if the mouse is over the widget, false if widget is null or mouse is not over it
     */
    private boolean isMouseOverWidget(double mouseX, double mouseY, Element widget) {

        return widget != null && widget.isMouseOver(mouseX, mouseY);
    }

    /**
     * Update the map each tick - handles the "animate to player" pan animation.
     * Zoom/pan damping is driven per render-frame in render() for true frame-rate independence.
     */
    @Override
    public void tick() {
        var mapCamera = getCamera();
        if (client != null && mapCamera != null) {

            var contentArea = getPaddedContentArea();

            if (animatingToPlayer) {

                mapCamera.resetZoomAnchor();

                // Hide context menu
                mapContextMenu = null;

                long elapsed = System.currentTimeMillis() - animationStartMs;
                float time = Math.min(1f, (float) elapsed / ANIMATION_DURATION_MS);

                float ease = (float) (1 - Math.pow(1 - time, 5));

                double currentX = animStartX + (animTargetX - animStartX) * ease;
                double currentZ = animStartZ + (animTargetZ - animStartZ) * ease;

                double currentZoom = animStartZoom + (animTargetZoom - animStartZoom) * ease;

                mapCamera.updateZoom(currentZoom);
                mapCamera.setWorldX(currentX, contentArea.topLeftX());
                mapCamera.setWorldZ(currentZ, contentArea.topLeftY());

                if (time >= 1f) animatingToPlayer = false;
            }

            // Update context menu position if open
            if (mapContextMenu != null) {
                var screenPos = mapCamera.worldToScreenCoordinates(mapContextMenu.getWorldX(), mapContextMenu.getWorldZ());
                mapContextMenu.setX((int) screenPos.x());
                mapContextMenu.setY((int) screenPos.y());
            }
        }
    }

    /**
     * Handle screen resizing, update map camera viewport and re-center coordinates button
     *
     * @param client the Minecraft client instance
     * @param width  the new width of the screen
     * @param height the new height of the screen
     */
    @Override
    public void resize(MinecraftClient client, int width, int height) {

        var mapCamera = getCamera();
        if (mapCamera != null) {

            var selection = layerSelectionDropdown.getSelected();
            mapCamera.setViewportSize(width, height);

            super.resize(client, width, height);

            updateMapButtonPositions();

            if (selection != null) layerSelectionDropdown.setSelected(selection);

            positionSidePanel();

        } else {

            super.resize(client, width, height);
        }
    }

    /**
     * Repositions the current side panel on screen
     */
    private void positionSidePanel() {

        if (locationContextPanel == null) return;

        var contentArea = getContentArea();

        var sidePanelWidth = getSidePanelWidth();
        var sidePanelHeight = contentArea.guiHeight() - 16;

        var xPos = contentArea.topLeftX() + contentArea.guiWidth() - sidePanelWidth;
        var yPos = contentArea.topLeftY() + 8;

        locationContextPanel.setSize(sidePanelWidth, sidePanelHeight);
        locationContextPanel.setPosition(xPos, yPos);
    }

    /**
     * @return the side panel width when displayed
     */
    private int getSidePanelWidth() {

        var contentArea = getContentArea();
        return (contentArea.guiWidth() / 3) + 32;
    }

    /**
     * Update the position of the coordinates button to be centered at the bottom of the screen
     */
    private void updateMapButtonPositions() {

        BackgroundRenderer.GuiLayout contentArea = getPaddedContentArea();

        int frameOffset = 2;
        int centerX = width / 2 - ModConstants.BUTTON_WIDTH / 2;
        int rightX = contentArea.topLeftX() + contentArea.guiWidth() - ModConstants.SMALL_SQUARED_BUTTON_SIZE - 4;
        int leftX = contentArea.topLeftX() + 4;

        int topY = contentArea.topLeftY() - frameOffset - ModConstants.BUTTON_HEIGHT / 2;
        int bottomY = contentArea.topLeftY() + contentArea.guiHeight() + frameOffset - ModConstants.BUTTON_HEIGHT / 2;
        int frameBottomY = contentArea.topLeftY() + contentArea.guiHeight() - ModConstants.SMALL_SQUARED_BUTTON_SIZE - 4;

        if (coordinatesButton != null)
            coordinatesButton.setPosition(centerX, bottomY);

        if (markersSelectionDropdown != null)
            markersSelectionDropdown.setPosition(centerX, topY);

        if (layerSelectionDropdown != null)
            layerSelectionDropdown.setPosition(rightX, frameBottomY);

        if (dimensionSelectionDropdown != null)
            dimensionSelectionDropdown.setPosition(leftX, bottomY);
    }

    /**
     * Handle mouse click for starting map dragging
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @param button The mouse button
     * @return True if the event was handled
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        var mapCamera = getCamera();
        if (mapCamera == null) return super.mouseClicked(mouseX, mouseY, button);
        var mouseInMapArea = mapFrameRenderer.coordinatesInFrame(mouseX, mouseY, MAP_FRAME_PADDING);

        // Context menu click handling
        if (mapContextMenu != null) {
            if (mapContextMenu.isMouseOver(mouseX, mouseY)) {
                return mapContextMenu.mouseClicked(mouseX, mouseY, button);
            }
            mapContextMenu = null;
            return true;
        }

        if (locationContextPanel != null && locationContextPanel.isMouseOver(mouseX, mouseY)) {
            return locationContextPanel.mouseClicked(mouseX, mouseY, button);
        }

        // Mouse side-button navigation through location history
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) { // Back
            if (historyIndex > 0) {
                historyIndex--;
                applySidePanel(locationHistory.get(historyIndex), false);
            } else {
                locationContextPanel = null;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_5) { // Forward
            if (historyIndex < locationHistory.size() - 1) {
                historyIndex++;
                applySidePanel(locationHistory.get(historyIndex), false);
                return true;
            }
            return false;
        }

        // Register drag start
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseInMapArea) {
            dragging = true;
            animatingToPlayer = false;
            clickStartX = mouseX;
            clickStartY = mouseY;
        }

        // Click on usable map area with right mouse button
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && mouseInMapArea) {

            var outsideExploredArea = false;

            if (!ArdaMapsClient.CONFIG.isMapRevealAll()) {

                var pos = mapCamera.screenToWorldCoordinates(mouseX, mouseY);
                outsideExploredArea = (!explorationState.isWorldPosExplored(pos.x(), pos.y(), 0));
            }

            openMapContextMenu((int) mouseX, (int) mouseY, outsideExploredArea);

            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Opens the side panel for the given location and pans the camera to it,
     * <em>without</em> modifying the navigation history.
     *
     * @param location the location to display
     * @param focused  if true zooms to identity on the given location
     */
    private void applySidePanel(LocationClient location, boolean focused) {

        var mapCamera = getCamera();

        if (mapCamera == null || location == null) return;

        Vec3d locationPosition = location.getPosition();

        var targetZoom = mapCamera.getZoom();
        var focusedZoom = mapCamera.getIdentityZoom();
        var cameraOffsetWorldPos = getSidePanelFocusedCameraWorldOffset(mapCamera, locationPosition, targetZoom);
        var focusedCameraOffsetWorldPosition = getSidePanelFocusedCameraWorldOffset(mapCamera, locationPosition, focusedZoom);

        locationContextPanel = new SidePanelWidget(this,
                textRenderer,
                location,
                focusedCameraOffsetWorldPosition,
                focusedZoom);

        positionSidePanel();
        panCameraToMapCoordinates(
                focused ? focusedCameraOffsetWorldPosition : cameraOffsetWorldPos,
                focused ? focusedZoom : targetZoom
        );
    }

    /**
     * Returns the camera position so that the left part of the screen is centered on locationPosition when a side
     * panel is displayed.
     *
     * @param mapCamera        the underlying camera
     * @param locationPosition the location's position
     * @param zoom             the zoom level at which to calculate the offset
     * @return the camera offset
     */
    private @Nullable Vec2d getSidePanelFocusedCameraWorldOffset(MapCamera mapCamera, Vec3d locationPosition, double zoom) {

        if (locationPosition.x == 0 && locationPosition.z == 0) return null;

        // Centre left part of the viewport on location
        var paddedContentArea = getPaddedContentArea();
        var screenLeftCenterX = paddedContentArea.topLeftX() + (paddedContentArea.guiWidth() - getSidePanelWidth()) / 2;
        var screenLeftCenterY = height / 2;

        var worldLeftCenter = mapCamera.screenToWorldCoordinates(screenLeftCenterX, screenLeftCenterY, zoom);
        var worldViewportCenter = mapCamera.screenToWorldCoordinates(paddedContentArea.topLeftX() + paddedContentArea.guiWidth() / 2f, screenLeftCenterY);

        var translationX = worldLeftCenter.x() - worldViewportCenter.x();
        var translationY = worldLeftCenter.y() - worldViewportCenter.y();

        return new Vec2d(locationPosition.x - translationX, locationPosition.z - translationY);
    }

    /**
     * Open the context menu at the given mouse position
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     */
    private void openMapContextMenu(int mouseX, int mouseY, boolean outsideExploredArea) {

        var mapCamera = getCamera();
        if (mapCamera == null) return;

        var contextPos = mapCamera.screenToWorldCoordinates(mouseX, mouseY);

        // Add / Replace waypoint at clicked position
        var addWaypointEntry = new ContextMenu.Entry(Text.translatable("ardamaps.client.map.screen.context.menu.set.waypoint"), () -> {
            ArdaMapsClient.CONFIG.setWaypoint(contextPos.x(), contextPos.y(), mapCamera.getDimension().getId());
            mapContextMenu = null;
        });

        // Teleport to clicked position if explored
        var teleportToEntry = new ContextMenu.Entry(Text.translatable("ardamaps.client.map.screen.context.menu.teleport"), () -> {

            mapContextMenu = null;

            PacketRegistry.PLAYER_TELEPORT_REQUEST.send(new PlayerTeleportPacket(contextPos.x(), contextPos.y(), mapCamera.getDimension().getId()));
        });

        var itemList = new ArrayList<ContextMenu.Entry>();

        if (!outsideExploredArea) itemList.add(teleportToEntry);

        if (mouseOverWaypoint != null) {

            // Create a deep copy - mouseOverWaypoint is dynamically updated
            var staticWaypoint = Waypoint.copy(mouseOverWaypoint);

            var shareWaypointEntry = new ContextMenu.Entry(Text.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.share"), () -> {

                assert Client.player() != null;

                var playerName = Client.player().getName().getString();
                var sharedWaypoint = new Waypoint(
                        staticWaypoint.x(),
                        staticWaypoint.z(),
                        String.format("%s [%d,%d]", playerName, staticWaypoint.x(), staticWaypoint.z()),
                        0.5882f, 0f, 1f,
                        playerName,
                        staticWaypoint.dimension()
                );

                Client.mc().keyboard.setClipboard("waypoint:" + Waypoint.toJson(sharedWaypoint));
                Client.player().sendMessage(Text.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.share.message"), true);

                mapContextMenu = null;
            });

            var removeWaypointEntry = new ContextMenu.Entry(Text.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.remove"), () -> {
                ArdaMapsClient.CONFIG.removeWaypoint(staticWaypoint);
                mapContextMenu = null;
            });

            itemList.add(shareWaypointEntry);
            itemList.add(removeWaypointEntry);

        } else {

            // Clear all the waypoints
            var clearWaypointEntry = new ContextMenu.Entry(Text.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.clear"), () -> {
                ArdaMapsClient.CONFIG.clearWaypoints(mapCamera.getDimension().getId());
                mapContextMenu = null;
            });

            if (ArdaMapsClient.CONFIG.hasWaypoint(mapCamera.getDimension().getId())) itemList.add(clearWaypointEntry);

            itemList.add(addWaypointEntry);
        }

        mapContextMenu = new ContextMenu(mouseX, mouseY, contextPos.x(), contextPos.y(), itemList);
    }

    /**
     * Smoothly move the map camera to the specified world coordinates
     *
     * @param worldPos The target world coordinates to pan to
     */
    public void panCameraToMapCoordinates(Vec2d worldPos, double targetZoom) {

        // Don't pan locations that resolve to 0,0,0 (for eg: regions)
        if (worldPos == null) return;

        var mapCamera = getCamera();
        if (mapCamera == null) return;

        animTargetX = worldPos.x();
        animTargetZ = worldPos.y();
        animStartX = mapCamera.getWorldX();
        animStartZ = mapCamera.getWorldZ();
        animStartZoom = mapCamera.getZoom();
        animTargetZoom = targetZoom;

        animationStartMs = System.currentTimeMillis();
        animatingToPlayer = true;
    }

    /**
     * Handle mouse release for stopping map dragging
     * Also handle single clicks on the map
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @param button The mouse button
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;

            // Check if mouse barely moved (single click, not drag)
            double distanceSquared = Math.pow(mouseX - clickStartX, 2) + Math.pow(mouseY - clickStartY, 2);

            if (distanceSquared < CLICK_THRESHOLD_SQUARED) handleMapMarkerClick();

            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Handle clicks on map markers to open location context panel
     */
    private void handleMapMarkerClick() {

        if (Client.player() == null) return;

        var anyLocationClicked = false;

        if (mouseOverLocation != null) {

            panAndSelectLocation(mouseOverLocation, false);

            anyLocationClicked = true;
        }

        if (!anyLocationClicked)
            locationContextPanel = null;
    }

    /**
     * Pans the camera to the given location, opens the side panel, and pushes the entry onto the
     * navigation history (truncating any forward entries first, then capping at 10).
     *
     * @param location the location to display
     * @param focused  if true zooms to identity at the location
     */
    public void panAndSelectLocation(LocationClient location, boolean focused) {

        if (location == null) return;

        // Truncate any forward history beyond the current cursor
        if (historyIndex < locationHistory.size() - 1)
            locationHistory.subList(historyIndex + 1, locationHistory.size()).clear();

        // Cap at 10 entries by evicting the oldest
        if (locationHistory.size() >= 10)
            locationHistory.remove(0);

        locationHistory.add(location);
        historyIndex = locationHistory.size() - 1;

        applySidePanel(location, focused);
    }

    /**
     * Handle mouse dragging for panning the map
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @param button The mouse button
     * @param dx     The change in x position
     * @param dy     The change in y position
     * @return True if the event was handled
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {

        var mapCamera = getCamera();
        if (mapCamera != null && dragging) {

            mapCamera.resetZoomAnchor();

            var contentArea = getPaddedContentArea();

            var worldOrigin = mapCamera.screenToWorldCoordinates(0, 0);
            var worldDelta = mapCamera.screenToWorldCoordinates(dx, dy);

            mapCamera.setWorldX(mapCamera.getWorldX() - (worldDelta.x() - worldOrigin.x()), contentArea.topLeftX());
            mapCamera.setWorldZ(mapCamera.getWorldZ() - (worldDelta.y() - worldOrigin.y()), contentArea.topLeftY());

            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    /**
     * Handle mouse scroll for zooming
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @param amount The scroll amount
     * @return True if the event was handled
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {

        if (mouseOverMapWidgets(mouseX, mouseY)) {

            if (locationContextPanel != null && locationContextPanel.mouseScrolled(mouseX, mouseY, amount))
                return true;

            return super.mouseScrolled(mouseX, mouseY, amount);
        }

        var cam = getCamera();
        if (cam != null) {
            animatingToPlayer = false;
            cam.setZoom(mouseX, mouseY, width, height, amount * 0.5);
        }

        return true;
    }

    /**
     * Handle key press events.
     * <ul>
     *   <li>ESCAPE - if a side panel is open, close it; otherwise fall through to the default
     *       screen-close behaviour.</li>
     * </ul>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && locationContextPanel != null) {
            locationContextPanel = null;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Get the padding inside the map content area for GUI elements
     *
     * @return The padding in pixels
     */
    @Override
    public int getContentPadding() {

        return MAP_FRAME_PADDING;
    }

    /**
     * Get the search function that is called when searching an element on screen via the search widget.
     * This function should search for a String in a List of elements represented on screen
     *
     * @return the search function
     */
    @Override
    protected @Nullable Function<String, List<?>> getSearchFunction() {

        return (String input) -> {

            var mapCamera = getCamera();
            if (mapCamera == null) return null;

            var results = new ArrayList<>();
            var selectedLocationType = markersSelectionDropdown.getSelected();

            var locations = ArdaMapsClient.CONFIG.getLocations(
                    mapCamera.getDimension().getId(),
                    selectedLocationType != null ? selectedLocationType.key : null);

            locations.stream()
                    .filter(locationClient -> Objects.equals(locationClient.getWorld(), selectedDimension.getId()))
                    .filter(LocationClient::isRevealed)
                    .filter(locationClient -> locationClient.getName().toLowerCase().contains(input.toLowerCase()))
                    .forEach(results::add);

            return results;
        };
    }

    /**
     * Gets the function that is called when a search result is selected via the search widget.
     *
     * @return the function called when a search result is selected
     */
    @Override
    protected Function<Object, Void> getOnSearcheResultSelectedFunction() {

        return (element) -> {

            if (element instanceof LocationClient location)
                panAndSelectLocation(location, true);

            return null;
        };
    }

    /**
     * @return true - this screen can be searched
     */
    @Override
    protected boolean isSearchable() {
        return true;
    }

    /**
     * Gets the rendering function of a search result. This function takes an element as an input and returns a
     * displayable string
     *
     * @return the search result rendering function
     */
    @Override
    protected @Nullable Function<Object, String> getSearchResultRenderFunction() {

        return (obj) -> {

            if (obj instanceof LocationClient location)
                return location.getName();

            return Objects.toString(obj, "");
        };
    }

    /**
     * Internal record to hold marker information for rendering and interaction
     *
     * @param key            The unique key of the marker type, used for filtering and lookup
     * @param displayName    The display name of the marker type, shown in the dropdown and tooltips
     * @param icon           The icon identifier for the marker type, used for rendering the marker on the map
     * @param color          The colour associated with the marker type, used for rendering the marker background or tint
     * @param highlightColor The colour used for highlighting the marker (e.g., on hover), used for rendering effects when the marker is interacted with
     */
    private record MarkerInfo(String key, String displayName, Identifier icon, int color, int highlightColor) {
    }
}

