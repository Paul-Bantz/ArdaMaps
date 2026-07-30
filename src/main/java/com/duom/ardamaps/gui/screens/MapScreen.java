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
import com.duom.ardamaps.core.data.Vec3d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.Waypoint;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.core.data.map.markers.MarkersManager;
import com.duom.ardamaps.core.networking.PacketRegistry;
import com.duom.ardamaps.core.networking.packets.server.PlayerRangedTeleportPacket;
import com.duom.ardamaps.core.networking.packets.server.PlayerTeleportPacket;
import com.duom.ardamaps.gui.GuiTextures;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.map.rendering.MapRenderable;
import com.duom.ardamaps.gui.screens.map.LocationNavigationHistory;
import com.duom.ardamaps.gui.screens.map.MapCameraAnimation;
import com.duom.ardamaps.gui.screens.map.MapLayerLoader;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.screens.rendering.MapFrameRenderer;
import com.duom.ardamaps.gui.screens.rendering.MapMarkerRenderer;
import com.duom.ardamaps.gui.widgets.*;
import com.duom.ardamaps.gui.widgets.builders.MapDropdownBuilder;
import com.duom.ardamaps.gui.widgets.builders.RangeSelectionWidgetBuilder;
import com.duom.ardamaps.gui.widgets.builders.StyledButtonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

    /** Precalculated half size of the Ardacraft logo, used for centering the placeholder */
    private static final int ARDACRAFT_LOGO_HALF_SIZE_WITH_SPACING = ARDACRAFT_LOGO_HALF_SIZE + Client.mc().font.lineHeight;

    /** Precalculated click threshold squared for marker interaction (to avoid sqrt calculations) */
    private static final double CLICK_THRESHOLD_SQUARED = 4.0;

    /** Padding from the edges of the map frame for map rendering and interactions */
    private static final int MAP_FRAME_PADDING = 10;

    /** Renderer for map frame and decorations */
    private final MapFrameRenderer mapFrameRenderer = new MapFrameRenderer();

    /** Renderer for map markers, player marker, and waypoints */
    private final MapMarkerRenderer markerRenderer = new MapMarkerRenderer();

    /** Builds map renderables when the selected layer changes */
    private final MapLayerLoader mapLayerLoader = new MapLayerLoader();

    /** Smooth camera animation helper */
    private final MapCameraAnimation animation = new MapCameraAnimation();

    /** Side-panel location navigation history */
    private final LocationNavigationHistory locationHistory = new LocationNavigationHistory();

    /** Cached text for unknown region tooltip to avoid repeated translations and allocations */
    private final String unknownRegionText = Component.translatable("ardamaps.client.map.screen.unknown.region").getString();

    /** The map layer selection dropdown */
    private MapDropdownWidget<MapLayerDefinition, TextIdentifierPairItem> layerSelectionDropdown;

    /** Dimension selection dropdown */
    private MapDropdownWidget<Dimension, TextIdentifierPairItem> dimensionSelectionDropdown;

    /** Range selection dropdown for ranged map layers */
    private RangeSelectionWidget rangeSelectionWidget;

    /** The currently displayed vertical range, or null when the active layer has no ranges */
    private MapLayerRange selectedRange;

    /** Markers filtering dropdown */
    private MapDropdownWidget<MarkerInfo, TextIdentifierPairItem> markersSelectionDropdown;

    /** Coordinates display button, also used to pan back to player position when clicked */
    private StyledButtonWidget coordinatesButton;

    /** Flag indicating whether the user currently dragging the map */
    private boolean dragging = false;

    /** The currently displayed map renderable */
    private MapRenderable mapRenderer;

    /** Monotonic token used to discard stale asynchronous layer loads. */
    private int layerLoadGeneration;

    /** Whether this screen has been removed and must reject completed layer loads. */
    private boolean removed;

    /** Right click Context menu for the map */
    private ContextMenu mapContextMenu;

    /** Side panel for location context */
    private SidePanelWidget locationContextPanel;

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

    /** Last X coordinate emitted to the coordinates button. */
    private int lastCoordinatesButtonX = Integer.MIN_VALUE;

    /** Last Z coordinate emitted to the coordinates button. */
    private int lastCoordinatesButtonZ = Integer.MIN_VALUE;

    /**
     * The dimension selected in the UI dropdown. Tracks which dimension the user has chosen so that
     * {@link #buildLayerLoaderInput} can pre-build the correct camera. Once a layer is loaded, the authoritative
     * dimension is {@code getCamera().getDimension()} - this field is only the UI-selection staging area.
     */
    private Dimension selectedDimension;

    /**
     * Constructor for a new MapScreen instance
     *
     * @param parent The parent screen to return to when exiting the map screen
     */
    public MapScreen(Screen parent) {

        super(parent, Component.translatable("ardamaps.client.map.screen.map"));
    }

    /**
     * Initialize the map screen
     * Set up the map camera, map renderer, and GUI components. If a map layer is already selected in the config,
     * it will be loaded and displayed. Define the width and height of the map viewport
     */
    @Override
    protected void init() {

        super.init();

        removed = false;
        var previousSidePanel = locationContextPanel;
        int layerGenerationBeforeWidgets = layerLoadGeneration;

        configureCoordinatesButton();
        configureDimensionSelectionDropDown();
        configureMapLayerSelectionDropDown();
        configureRangeSelectionWidget();
        configureMarkersDisplayDropdown();

        var camera = getCamera();

        if (camera != null)
            camera.setViewportSize(width, height);

        if (previousSidePanel != null)
            locationContextPanel = previousSidePanel;

        if (mapRenderer == null && layerLoadGeneration == layerGenerationBeforeWidgets)
            reloadSelectedLayer();
    }

    /**
     * Configure the coordinates display button at the bottom of the screen
     * Displays the current coordinates of the player or mouse cursor when hovering over the map
     * Clicking the button will move the camera to the player's current position
     */
    private void configureCoordinatesButton() {

        if (coordinatesButton != null) removeWidget(coordinatesButton);

        coordinatesButton = StyledButtonBuilder.create()
                .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                .setOnClick(this::panCameraToPlayer)
                .build();
        coordinatesButton.visible = getCamera() != null;
        lastCoordinatesButtonX = Integer.MIN_VALUE;
        lastCoordinatesButtonZ = Integer.MIN_VALUE;

        updateMapButtonPositions();

        addRenderableWidget(coordinatesButton);
    }

    /**
     * Configure the dimension selection dropdown at the top of the screen
     * Allows switching between dimensions if multiple are available in the config
     */
    private void configureDimensionSelectionDropDown() {

        if (dimensionSelectionDropdown != null) removeWidget(dimensionSelectionDropdown);

        List<Dimension> dimensions = ArdaMapsClient.CONFIG != null && ArdaMapsClient.CONFIG.getDimensions() != null
                ? ArdaMapsClient.CONFIG.getDimensions()
                : new ArrayList<>();

        var defaultSelection = selectedDimension != null ? selectedDimension : Client.currentDimension();

        // Default may be null if the dimension was not configured server-side. Handle gracefully.
        if (defaultSelection == null && !dimensions.isEmpty()) defaultSelection = dimensions.getFirst();

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

        addRenderableWidget(dimensionSelectionDropdown);
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

        List<MapLayerDefinition> mapLayers = MapLayerDropdownOptions.forDimension(selectedDimension);

        var provider = ArdaMapsClient.getHttpImageProvider();
        Double playerY = Client.playerPositionY();

        // Preload icons
        for (MapLayerDefinition layer : mapLayers) {
            String icon = layer.effectiveIcon(playerY);
            if (icon != null && !icon.isEmpty()) provider.loadImage(icon);
        }

        MapLayerDefinition previousSelection = null;

        // Preserve selection if not null
        if (layerSelectionDropdown != null)
            previousSelection = mapLayers.contains(layerSelectionDropdown.getSelected()) ? layerSelectionDropdown.getSelected() : null;

        if (layerSelectionDropdown != null) removeWidget(layerSelectionDropdown);

        layerSelectionDropdown = MapDropdownBuilder.<MapLayerDefinition, TextIdentifierPairItem>create()
                .setSize(ModConstants.SMALL_SQUARED_BUTTON_SIZE, ModConstants.SMALL_SQUARED_BUTTON_SIZE)
                .setOptions(mapLayers)
                .setOptionDisplay(item ->
                        item == null ?
                                new TextIdentifierPairItem(Component.translatable("ardamaps.client.map.screen.layer.dropdown.empty"), null) :
                                new TextIdentifierPairItem(item.layer(), provider.getTexture(item.effectiveIcon(Client.playerPositionY())))
                )
                .setOnSelect(this::mapLayerSelectionChanged)
                .setDisplayIcons(true)
                .setDisplayLabels(false)
                .setSelected(previousSelection != null ? previousSelection : mapLayers.getFirst())
                .setDisplayArrows(false)
                .setExpandDirection(DropdownWidget.ExpandDirection.UP_LEFT)
                .build();

        addRenderableWidget(layerSelectionDropdown);

        // Hide if only one layer available, no need to show a dropdown for a single option
        layerSelectionDropdown.visible = mapLayers.size() > 1;

        if (layerSelectionDropdown.getSelected() != null
                && (previousSelection == null
                || !Objects.equals(previousSelection, layerSelectionDropdown.getSelected())))
            mapLayerSelectionChanged(layerSelectionDropdown.getSelected());
    }

    /**
     * Configure the vertical range selection widget for the currently selected ranged layer.
     */
    private void configureRangeSelectionWidget() {

        if (rangeSelectionWidget != null) removeWidget(rangeSelectionWidget);

        rangeSelectionWidget = RangeSelectionWidgetBuilder.create()
                .setSize(100, 15)
                .setLabel(Component.translatable("ardamaps.client.map.screen.range.label"))
                .setItemWidth(15)
                .setOnSelect(this::rangeSelectionChanged)
                .build();

        addRenderableWidget(rangeSelectionWidget);
        refreshRangeSelection();
    }

    /**
     * Refreshes the vertical range selector for the currently selected map layer.
     */
    private void refreshRangeSelection() {

        if (rangeSelectionWidget == null) return;

        MapLayerDefinition selectedLayer = layerSelectionDropdown == null ? null : layerSelectionDropdown.getSelected();
        List<MapLayerRange> ranges = selectedLayer != null && selectedLayer.hasRanges()
                ? new ArrayList<>(selectedLayer.ranges())
                : new ArrayList<>();

        if (!ranges.isEmpty() && (selectedRange == null || !ranges.contains(selectedRange)))
            selectedRange = ranges.getFirst();

        rangeSelectionWidget.setRanges(ranges);
        rangeSelectionWidget.setSelected(selectedRange);
        rangeSelectionWidget.visible = !ranges.isEmpty();
        updateMapButtonPositions();
    }

    /**
     * Configure the markers display dropdown
     */
    private void configureMarkersDisplayDropdown() {

        if (markersSelectionDropdown != null) removeWidget(markersSelectionDropdown);

        var nullValue = new TextIdentifierPairItem(Component.translatable("ardamaps.client.map.screen.all.markers"), null);

        var list = MarkersManager.get().types().entrySet().stream()
                .map(entry -> {
                    var value = entry.getValue();
                    return new MarkerInfo(entry.getKey(), value.name(), ModConstants.id(value.icon()), value.color(), value.highlightColor());
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
                                        ModConstants.id(MarkersManager.get().getMarkerType(item.key).icon())) : nullValue)
                .setDisplayIcons(true)
                .setSelected(null)
                .setAllowNull(true)
                .setDisplayArrows(false)
                .setDisplayAsSprite(true)
                .setMaxVisibleOptions(6)
                .setExpandDirection(DropdownWidget.ExpandDirection.DOWN_RIGHT)
                .setPlaceholderText(Component.translatable("ardamaps.client.map.screen.all.markers"))
                .setPlaceholderIcon(ModConstants.ICON_ALL)
                .build();

        // If there is a dimension available, a map can be displayed, the dropdown is relevant.
        markersSelectionDropdown.visible = selectedDimension != null;

        updateMapButtonPositions();

        addRenderableWidget(markersSelectionDropdown);
    }

    /**
     * Smoothly move the map camera to the player's current position
     */
    private void panCameraToPlayer() {

        if (Client.player() == null) return;

        if (getCamera() == null) return;

        switchToLayerContaining(Client.playerPositionY());
        panCameraToMapCoordinates(Client.playerPosition2d(), getCamera().getPreferredZoom());
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

        selectedRange = mapLayerDefinition.hasRanges() ? defaultRangeForLayer(mapLayerDefinition) : null;
        refreshRangeSelection();
        reloadSelectedLayer();
    }

    /**
     * Selects the range matching the player's current Y, falling back to the first configured range.
     *
     * @param layer The ranged layer to inspect.
     * @return The default range for the layer, or null when the layer has no ranges.
     */
    private @Nullable MapLayerRange defaultRangeForLayer(MapLayerDefinition layer) {

        if (!layer.hasRanges()) return null;

        Double playerY = Client.playerPositionY();
        return playerY == null ? layer.ranges().getFirst() : layer.rangeForY(playerY);
    }

    /**
     * Render the map screen content, main render loop
     *
     * @param context The draw context
     * @param mouseX  The mouse x position
     * @param mouseY  The mouse y position
     * @param delta   The time since last frame
     */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (mapRenderer != null) {

            var mapCamera = getCamera();
            var contentArea = getPaddedContentArea();

            context.enableScissor(
                    contentArea.topLeftX(), contentArea.topLeftY(),
                    contentArea.topLeftX() + contentArea.guiWidth(),
                    contentArea.topLeftY() + contentArea.guiHeight()
            );

            try {
                // Fill the world bounds with a dark background, clipped to content area
                if (mapCamera != null) {

                    // Enforce minimum zoom to prevent seeing outside world bounds when map is smaller than content area
                    mapCamera.computeZoomLevelToFitContentArea(contentArea.guiWidth(), contentArea.guiHeight());

                    // Drive zoom/pan damping every render frame so the animation
                    // is truly frame-rate independent and does not jump on frame skips.
                    if (!animation.isRunning())
                        mapCamera.update(delta, contentArea.topLeftX(), contentArea.topLeftY());

                    // Clear background with dark colour - will display if some areas of the map are not covered by tiles
                    context.fill(contentArea.topLeftX(),
                            contentArea.topLeftY(),
                            contentArea.topLeftX() + contentArea.guiWidth(),
                            contentArea.topLeftY() + contentArea.guiHeight(),
                            ModConstants.COLOR_DARKER_BLUE);
                }

                mapRenderer.render(context);


                var selectedLocationType = markersSelectionDropdown.getSelected();
                var focusedLocationPosition = locationContextPanel == null
                        ? null
                        : locationContextPanel.getDisplayedLocationPosition();
                markerRenderer.render(
                        context,
                        font,
                        mapCamera,
                        mapFrameRenderer,
                        selectedRange,
                        focusedLocationPosition,
                        selectedLocationType != null ? selectedLocationType.key : null,
                        mouseOverMapWidgets(mouseX, mouseY),
                        MAP_FRAME_PADDING,
                        mouseX,
                        mouseY);
            } finally {
                context.disableScissor();
            }

            mapFrameRenderer.render(context, contentArea);

            updateCoordinates(mouseX, mouseY);
            updateRegionUnderMouse(mouseX, mouseY);


        } else {
            renderPlaceholder(context);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);

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
                isMouseOverWidget(mouseX, mouseY, rangeSelectionWidget) ||
                isMouseOverWidget(mouseX, mouseY, markersSelectionDropdown) ||
                isMouseOverWidget(mouseX, mouseY, locationContextPanel);
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

        int x = (int) Math.floor(worldCoordinates.x());
        int z = (int) Math.floor(worldCoordinates.y());
        if (x == lastCoordinatesButtonX && z == lastCoordinatesButtonZ) return;

        lastCoordinatesButtonX = x;
        lastCoordinatesButtonZ = z;
        coordinatesButton.setMessage(Component.literal(String.format("X:%d, Z:%d", x, z)));
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
    private void renderPlaceholder(GuiGraphicsExtractor context) {

        var centerX = width / 2;
        var centerY = height / 2;

        context.blit(RenderPipelines.GUI_TEXTURED, ModConstants.ARDACRAFT_LOGO,
                centerX - ARDACRAFT_LOGO_HALF_SIZE,
                centerY - ARDACRAFT_LOGO_HALF_SIZE,
                0, 0,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE,
                ARDACRAFT_LOGO_SIZE);

        context.centeredText(
                font,
                Component.translatable("ardamaps.client.map.screen.no.map.selected"),
                centerX,
                centerY + ARDACRAFT_LOGO_HALF_SIZE_WITH_SPACING,
                ModConstants.COLOR_WHITE);
    }

    /**
     * Render the region name tooltip at the top of the coordinates button
     *
     * @param context The draw context
     */
    private void renderRegionName(GuiGraphicsExtractor context) {

        if (getCamera() == null) return;

        if (regionNameUnderMouse == null) return;

        var textWidth = font.width(regionNameUnderMouse);
        var labelWidth = textWidth + 32;
        var labelHeight = font.lineHeight + 24;

        var paddedContentArea = getPaddedContentArea();

        var x = paddedContentArea.topLeftX() + 5;
        var y = paddedContentArea.topLeftY() + 5;

        GuiTextures.blitNineSliced(context, ModConstants.MAP_GUI_ELEMENTS,
                x, y, labelWidth, labelHeight,
                16, 16,
                96, 48,
                144, 160,
                ModConstants.LEGACY_TEXTURE_SPACE, ModConstants.LEGACY_TEXTURE_SPACE);

        context.text(
                font,
                Component.literal(regionNameUnderMouse),
                x + labelWidth / 2 - textWidth / 2,
                y + 12,
                ModConstants.COLOR_DARK_BROWN,
                false);
    }

    /**
     * Helper method to check if the mouse is over a specific widget, with null check
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @param widget The widget to check (can be null)
     * @return True if the mouse is over the widget, false if widget is null or mouse is not over it
     */
    private boolean isMouseOverWidget(double mouseX, double mouseY, GuiEventListener widget) {

        return widget != null && widget.isMouseOver(mouseX, mouseY);
    }

    /**
     * Update the map each tick - handles the "animate to player" pan animation.
     * Zoom/pan damping is driven per render-frame in render() for true frame-rate independence.
     */
    @Override
    public void tick() {
        var mapCamera = getCamera();
        if (mapCamera != null) {

            var contentArea = getPaddedContentArea();

            if (animation.isRunning()) {
                mapContextMenu = null;
                animation.apply(mapCamera, contentArea.topLeftX(), contentArea.topLeftY());
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
    public void resize(int width, int height) {

        var mapCamera = getCamera();
        if (mapCamera != null) {

            var selection = layerSelectionDropdown != null ? layerSelectionDropdown.getSelected() : null;
            mapCamera.setViewportSize(width, height);

            super.resize(width, height);

            updateMapButtonPositions();

            if (selection != null && layerSelectionDropdown != null) layerSelectionDropdown.setSelected(selection);

            positionSidePanel();

        } else {

            super.resize(width, height);
        }
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
        int rangeRightX = centerX + ModConstants.BUTTON_WIDTH;

        int bottomLeftY = contentArea.topLeftY() + contentArea.guiHeight();
        int topY = contentArea.topLeftY() - frameOffset - ModConstants.BUTTON_HEIGHT / 2;
        int bottomY = bottomLeftY + frameOffset - ModConstants.BUTTON_HEIGHT / 2;
        int layerDropdownY = bottomLeftY - ModConstants.SMALL_SQUARED_BUTTON_SIZE - 4;

        if (coordinatesButton != null)
            coordinatesButton.setPosition(centerX, bottomY);

        if (markersSelectionDropdown != null)
            markersSelectionDropdown.setPosition(centerX, topY);

        if (layerSelectionDropdown != null) {
            if (rangeSelectionWidget != null) layerDropdownY -= rangeSelectionWidget.getHeight() - 1;
            layerSelectionDropdown.setPosition(rightX, layerDropdownY);
        }

        if (dimensionSelectionDropdown != null)
            dimensionSelectionDropdown.setPosition(leftX, bottomY);

        if (rangeSelectionWidget != null) {
            rangeSelectionWidget.setWidth((contentArea.guiWidth() / 2) - ModConstants.BUTTON_WIDTH / 2);
            rangeSelectionWidget.setPosition(rangeRightX, bottomLeftY - rangeSelectionWidget.getHeight() - 1);
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
     * Handle mouse click for starting map dragging
     *
     * @param mouseX The mouse x position
     * @param mouseY The mouse y position
     * @param button The mouse button
     * @return True if the event was handled
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        var mapCamera = getCamera();
        if (mapCamera == null) return super.mouseClicked(event, doubleClick);
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
            var previousLocation = locationHistory.back();
            if (previousLocation != null) {
                applySidePanel(previousLocation, false);
            } else {
                locationContextPanel = null;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_5) { // Forward
            var nextLocation = locationHistory.forward();
            if (nextLocation != null) {
                applySidePanel(nextLocation, false);
                return true;
            }
            return false;
        }

        // Register drag start
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseInMapArea) {
            dragging = true;
            animation.cancel();
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

        return super.mouseClicked(event, doubleClick);
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
        var focusedZoom = mapCamera.getPreferredZoom();
        var cameraOffsetWorldPos = getSidePanelFocusedCameraWorldOffset(mapCamera, locationPosition, targetZoom);
        var focusedCameraOffsetWorldPosition = getSidePanelFocusedCameraWorldOffset(mapCamera, locationPosition, focusedZoom);

        locationContextPanel = new SidePanelWidget(this,
                font,
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
        var addWaypointEntry = new ContextMenu.Entry(Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint"), () -> {
            ArdaMapsClient.CONFIG.setWaypoint(contextPos.x(), contextPos.y(), mapCamera.getDimension().getId());
            mapContextMenu = null;
        });

        // Teleport to clicked position if explored
        var teleportToEntry = new ContextMenu.Entry(Component.translatable("ardamaps.client.map.screen.context.menu.teleport"), () -> {

            mapContextMenu = null;

            if (selectedRange != null) {
                PacketRegistry.PLAYER_RANGED_TELEPORT_REQUEST.send(new PlayerRangedTeleportPacket(
                        contextPos.x(),
                        contextPos.y(),
                        mapCamera.getDimension().getId(),
                        selectedRange.rangeMinY(),
                        selectedRange.rangeMaxY()), response -> {

                    if (response.success())
                        switchToLayerContaining(response.y());
                });
            } else {
                PacketRegistry.PLAYER_TELEPORT_REQUEST.send(new PlayerTeleportPacket(contextPos.x(), contextPos.y(), mapCamera.getDimension().getId()));
            }
        });

        var itemList = new ArrayList<ContextMenu.Entry>();

        if (!outsideExploredArea) itemList.add(teleportToEntry);

        var mouseOverWaypoint = markerRenderer.getMouseOverWaypoint();
        if (mouseOverWaypoint != null) {

            // Create a deep copy - mouseOverWaypoint is dynamically updated
            var staticWaypoint = Waypoint.copy(mouseOverWaypoint);

            var shareWaypointEntry = new ContextMenu.Entry(Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.share"), () -> {

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

                Client.mc().keyboardHandler.setClipboard("waypoint:" + Waypoint.toJson(sharedWaypoint));
                Client.player().sendSystemMessage(Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.share.message"));

                mapContextMenu = null;
            });

            var removeWaypointEntry = new ContextMenu.Entry(Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.remove"), () -> {
                ArdaMapsClient.CONFIG.removeWaypoint(staticWaypoint);
                mapContextMenu = null;
            });

            itemList.add(shareWaypointEntry);
            itemList.add(removeWaypointEntry);

        } else {

            // Clear all the waypoints
            var clearWaypointEntry = new ContextMenu.Entry(Component.translatable("ardamaps.client.map.screen.context.menu.set.waypoint.clear"), () -> {
                ArdaMapsClient.CONFIG.clearWaypoints(mapCamera.getDimension().getId());
                mapContextMenu = null;
            });

            if (ArdaMapsClient.CONFIG.hasWaypoint(mapCamera.getDimension().getId())) itemList.add(clearWaypointEntry);

            itemList.add(addWaypointEntry);
        }

        mapContextMenu = new ContextMenu(mouseX, mouseY, contextPos.x(), contextPos.y(), itemList);
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

        if (locationPosition.x() == 0 && locationPosition.z() == 0) return null;

        // Centre left part of the viewport on location
        var paddedContentArea = getPaddedContentArea();
        var screenLeftCenterX = paddedContentArea.topLeftX() + (paddedContentArea.guiWidth() - getSidePanelWidth()) / 2;
        var screenLeftCenterY = height / 2;

        var worldLeftCenter = mapCamera.screenToWorldCoordinates(screenLeftCenterX, screenLeftCenterY, zoom);
        var worldViewportCenter = mapCamera.screenToWorldCoordinates(paddedContentArea.topLeftX() + paddedContentArea.guiWidth() / 2f, screenLeftCenterY);

        var translationX = worldLeftCenter.x() - worldViewportCenter.x();
        var translationY = worldLeftCenter.y() - worldViewportCenter.y();

        return new Vec2d(locationPosition.x() - translationX, locationPosition.z() - translationY);
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

        animation.start(worldPos, mapCamera, targetZoom);
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
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // Children must get the release first: the parent dispatch is what drives widget
        // onRelease, and returning early here would swallow it for every left-button release.
        boolean handledByChild = super.mouseReleased(event);

        // The parent dispatch only reaches the hovered element, so a strip drag that ends
        // off the widget would otherwise leave it stuck tracking a press.
        if (!handledByChild && rangeSelectionWidget != null && rangeSelectionWidget.isDragging())
            handledByChild = rangeSelectionWidget.mouseReleased(event);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;

            // Check if mouse barely moved (single click, not drag)
            double distanceSquared = Math.pow(mouseX - clickStartX, 2) + Math.pow(mouseY - clickStartY, 2);

            if (!handledByChild && distanceSquared < CLICK_THRESHOLD_SQUARED) handleMapMarkerClick();

            return true;
        }

        return handledByChild;
    }

    /**
     * Handle clicks on map markers to open location context panel
     */
    private void handleMapMarkerClick() {

        if (Client.player() == null) return;

        var anyLocationClicked = false;

        var mouseOverLocation = markerRenderer.getMouseOverLocation();
        if (mouseOverLocation != null) {

            switchToLayerContaining(mouseOverLocation.getPosition().y());
            panAndSelectLocation(mouseOverLocation, false);

            anyLocationClicked = true;
        }

        if (!anyLocationClicked)
            locationContextPanel = null;
    }

    /**
     * Switch to the layer having the given Y position in range
     *
     * @param y the y position to select the correct layer
     */
    private void switchToLayerContaining(Double y) {

        if (y == null) return;

        var layer = layerSelectionDropdown != null ? layerSelectionDropdown.getSelected() : null;

        if (layer != null && layer.hasRanges()) {

            MapLayerRange target = layer.rangeForY(y);

            if (target != null && !Objects.equals(target, selectedRange)) {

                if (rangeSelectionWidget != null) rangeSelectionWidget.setSelected(target);
                rangeSelectionChanged(target);
            }
        }
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

        locationHistory.push(location);
        applySidePanel(location, focused);
    }

    /**
     * Callback for when the range selection changes.
     *
     * @param range The selected vertical range.
     */
    private void rangeSelectionChanged(@NotNull MapLayerRange range) {

        selectedRange = range;
        reloadSelectedLayer();
    }

    /**
     * Reloads the currently selected map layer using the active range selection.
     */
    private void reloadSelectedLayer() {

        if (layerSelectionDropdown == null || layerSelectionDropdown.getSelected() == null) return;

        final int generation = ++layerLoadGeneration;

        // Capture the current visual pixels-per-block so we can match zoom on the new map
        final double capturedRenderScale = (getCamera() != null) ? getCamera().getVisualPixelsPerBlock() : Double.NaN;
        MapLayerDefinition layer = layerSelectionDropdown.getSelected();
        MapLayerLoader.Input input = buildLayerLoaderInput(layer, capturedRenderScale);

        CompletableFuture.supplyAsync(() -> mapLayerLoader.load(input), ArdaMaps.IO_EXECUTOR)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Failed to load map layer", ex);
                        return;
                    }

                    Minecraft.getInstance().execute(() -> layerLoaded(generation, result));
                });
    }

    /**
     * Captures the screen state needed to build a map layer on the IO executor.
     */
    private MapLayerLoader.Input buildLayerLoaderInput(MapLayerDefinition mapLayerDefinition, double capturedRenderScale) {

        var cameraPosition = Client.playerPosition2d();
        Double playerY = Client.playerPositionY();

        if (!Objects.equals(selectedDimension, Client.currentDimension()))
            cameraPosition = new Vec2d(0, 0);

        int cx = (int) Math.floor(cameraPosition.x());
        int cy = (int) Math.floor(cameraPosition.y());

        if (getCamera() != null) {

            cx = (int) Math.floor(getCamera().getWorldX());
            cy = (int) Math.floor(getCamera().getWorldZ());
        }

        return new MapLayerLoader.Input(
                selectedDimension,
                selectedRange,
                mapLayerDefinition,
                capturedRenderScale,
                width,
                height,
                cx,
                cy,
                playerY,
                font);
    }

    /**
     * Callback for when a new map layer has finished loading. Sets the new map renderable and updates the camera and GUI state accordingly.
     *
     * @param mapRenderable The loaded MapRenderable, or null if loading failed
     */
    private void layerLoaded(int generation, @Nullable MapRenderable mapRenderable) {

        LOGGER.info("Map layer loaded: {}", mapRenderable != null ? "success" : "failed");

        if (removed || generation != layerLoadGeneration) {
            if (mapRenderable != null) mapRenderable.close();
            return;
        }

        if (mapRenderable != null) {

            closeMapRenderer();
            mapRenderer = mapRenderable;
            explorationState = mapRenderable.getExploration();
            updateMapButtonPositions();
            coordinatesButton.visible = true;
        }
    }

    /**
     * Closes the active map renderer if present.
     */
    private void closeMapRenderer() {

        if (mapRenderer != null) {
            mapRenderer.close();
            mapRenderer = null;
        }
    }

    /**
     * Cleans up renderer resources when the screen is removed.
     */
    @Override
    public void removed() {

        removed = true;
        layerLoadGeneration++;
        closeMapRenderer();
        super.removed();
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
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mouseX = event.x();
        double mouseY = event.y();

        var mapCamera = getCamera();
        if (mapCamera != null && dragging && !mouseOverMapWidgets(mouseX, mouseY)) {

            mapCamera.resetZoomAnchor();

            var contentArea = getPaddedContentArea();

            var worldOrigin = mapCamera.screenToWorldCoordinates(0, 0);
            var worldDelta = mapCamera.screenToWorldCoordinates(dx, dy);

            mapCamera.setWorldX(mapCamera.getWorldX() - (worldDelta.x() - worldOrigin.x()), contentArea.topLeftX());
            mapCamera.setWorldZ(mapCamera.getWorldZ() - (worldDelta.y() - worldOrigin.y()), contentArea.topLeftY());

            return true;
        }

        return super.mouseDragged(event, dx, dy);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        if (mouseOverMapWidgets(mouseX, mouseY)) {

            if (locationContextPanel != null && locationContextPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount))
                return true;

            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        var cam = getCamera();
        if (cam != null) {
            animation.cancel();
            cam.setZoom(mouseX, mouseY, width, height, verticalAmount * 0.5);
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
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && locationContextPanel != null) {
            locationContextPanel = null;
            return true;
        }

        return super.keyPressed(event);
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
