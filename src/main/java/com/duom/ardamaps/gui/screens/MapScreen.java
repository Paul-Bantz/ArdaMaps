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

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.gui.screens.map.*;
import com.duom.ardamaps.gui.screens.rendering.*;
import com.duom.ardamaps.gui.widgets.ContextMenu;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Screen for displaying the world map. Orchestrates rendering, camera management, and user input by
 * delegating all non-trivial responsibilities to focused collaborators:
 * {@link MapControlBar} (buttons and selection state), {@link MapLayerSession} (async load lifecycle),
 * {@link LocationPanelController} (side panel and navigation history), {@link MapContextMenuFactory}
 * (right-click menu construction), {@link MapOverlayRenderer} (HUD overlays), and
 * {@link MapLocationSearch} (search filtering).
 */
@Environment(EnvType.CLIENT)
public class MapScreen extends ArdaMapsScreen implements MapControlBar.Host {

    /** Precalculated click threshold squared for marker interaction (avoids sqrt). */
    private static final double CLICK_THRESHOLD_SQUARED = 4.0;

    /** Padding from the edges of the map frame for map rendering and interactions. */
    private static final int MAP_FRAME_PADDING = 10;

    /** Maximum zoom difference treated as already at the target zoom. */
    private static final double ZOOM_TOLERANCE = .01;

    /** Input hints displayed on the map screen. */
    private static final List<ScreenHintsRenderer.Hint> SCREEN_HINTS = List.of(
            ScreenHintsRenderer.Hint.search(),
            ScreenHintsRenderer.Hint.teleport(),
            ScreenHintsRenderer.Hint.addMarker());

    /** Renderer for the decorative map frame. */
    private final MapFrameRenderer mapFrameRenderer = new MapFrameRenderer();

    /** Renderer for markers, player icon, and waypoints. */
    private final MapMarkerRenderer markerRenderer = new MapMarkerRenderer();

    /** HUD overlay renderer (coordinates, region name, placeholder, debug panel). */
    private final MapOverlayRenderer overlayRenderer = new MapOverlayRenderer();

    /** Renderer for vector region borders. */
    private final RegionBorderRenderer regionBorderRenderer = new RegionBorderRenderer();

    /** Renderer for on-map region labels. */
    private final RegionLabelRenderer regionLabelRenderer = new RegionLabelRenderer();

    /** Async map-layer load lifecycle manager. */
    private final MapLayerSession layerSession = new MapLayerSession();

    /** Location side-panel and navigation history controller. */
    private final LocationPanelController locationPanel = new LocationPanelController(
            this,
            () -> font,   // font is not yet available at field-init time; use a lambda
            layerSession::getCamera,
            this::getPaddedContentArea,
            () -> height);

    /** Bottom-left control bar (buttons and selection state). */
    private final MapControlBar controlBar = new MapControlBar(this);

    /** Location search helper. */
    private final MapLocationSearch locationSearch = new MapLocationSearch();

    /** Smooth camera pan/zoom animation helper. */
    private final com.duom.ardamaps.gui.screens.map.MapCameraAnimation animation
            = new com.duom.ardamaps.gui.screens.map.MapCameraAnimation();

    /** Right-click context menu, or {@code null} when none is open. */
    @Nullable
    private ContextMenu mapContextMenu;

    /** Dimension used by the most recently requested layer load. */
    @Nullable
    private Dimension loadedDimension;

    /** Location to focus once the first map layer finishes loading. */
    @Nullable
    private LocationClient initialFocus;

    /** Flag indicating the left mouse button is held for dragging. */
    private boolean dragging;

    /** Mouse x position when the current left-button press began. */
    private double clickStartX;

    /** Mouse y position when the current left-button press began. */
    private double clickStartY;

    /**
     * Constructor for a new MapScreen instance.
     *
     * @param parent The parent screen to return to when closing.
     */
    public MapScreen(Screen parent) {

        this(parent, null);
    }

    /**
     * Constructor for a new MapScreen instance.
     *
     * @param parent       The parent screen to return to when closing.
     * @param initialFocus The location to focus after the initial map load, or {@code null}.
     */
    public MapScreen(Screen parent, @Nullable LocationClient initialFocus) {

        super(parent, Component.translatable("ardamaps.client.map.screen.map"));
        this.initialFocus = initialFocus;
    }

    /**
     * Initialises the screen. Rebuilds controls, restores any open side panel, and triggers the
     * initial layer load when no map is loaded yet and no load was started during widget setup.
     */
    @Override
    protected void init() {

        super.init();

        layerSession.activate();

        var previousPanel = locationPanel.snapshotPanel();
        int generationBeforeWidgets = layerSession.generation();

        controlBar.rebuild();

        var camera = layerSession.getCamera();
        if (camera != null) camera.setViewportSize(width, height);

        locationPanel.restorePanel(previousPanel);

        if (layerSession.getRenderable() == null && layerSession.generation() == generationBeforeWidgets)
            triggerReload();
    }

    /**
     * Gets the input hints displayed for this screen.
     *
     * @return The screen input hints.
     */
    @Override
    protected List<ScreenHintsRenderer.Hint> getScreenHints() {

        return SCREEN_HINTS;
    }

    /**
     * Builds the layer loader input and submits a reload to the session.
     */
    private void triggerReload() {

        MapLayerDefinition layer = controlBar.selectedLayer();
        if (layer == null) return;

        Dimension selectedDimension = controlBar.selectedDimension();
        if (selectedDimension == null) return;

        MapCamera camera = layerSession.getCamera();
        Vec2d cameraPosition = reloadStartingPosition(selectedDimension, camera);
        int cx = (int) Math.floor(cameraPosition.x());
        int cy = (int) Math.floor(cameraPosition.y());

        MapLayerLoader.Input input = new MapLayerLoader.Input(
                selectedDimension,
                controlBar.selectedRange(),
                layer,
                layerSession.capturedRenderScale(),
                width,
                height,
                cx,
                cy,
                Client.playerPositionY(),
                font);

        layerSession.reload(input, () -> {

            loadedDimension = selectedDimension;
            controlBar.onMapLoaded();
            applyInitialFocus();
        });
    }

    /**
     * Applies the one-shot focus requested when this screen was opened.
     */
    private void applyInitialFocus() {

        LocationClient focus = initialFocus;
        if (focus == null) return;

        initialFocus = null;
        controlBar.switchToLayerContaining(focus.getPosition().y());
        panAndSelectLocation(focus, true);
    }

    /**
     * Resolves the starting camera position for a layer reload.
     *
     * @param selectedDimension The dimension being loaded.
     * @param camera            The current camera, or {@code null} on first load.
     * @return The world position for the new camera.
     */
    private Vec2d reloadStartingPosition(Dimension selectedDimension, @Nullable MapCamera camera) {

        if (camera != null && Objects.equals(selectedDimension, loadedDimension))
            return new Vec2d(camera.getWorldX(), camera.getWorldZ());

        if (Objects.equals(selectedDimension, Client.currentDimension()) && Client.player() != null)
            return Client.playerPosition2d();

        return new Vec2d(selectedDimension.getCenterX(), selectedDimension.getCenterZ());
    }

    /**
     * Cleans up resources when the screen is closed.
     */
    @Override
    public void removed() {

        layerSession.close();
        super.removed();
    }

    /** {@inheritDoc} */
    @Override
    public void addControl(net.minecraft.client.gui.components.AbstractWidget widget) {

        addRenderableWidget(widget);
    }

    /** {@inheritDoc} */
    @Override
    public void removeControl(net.minecraft.client.gui.components.AbstractWidget widget) {

        removeWidget(widget);
    }

    /** {@inheritDoc} */
    @Override
    public int getScreenWidth() {

        return width;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasCamera() {

        return layerSession.getCamera() != null;
    }

    /** {@inheritDoc} */
    @Override
    public void onLayerSelectionChanged() {

        locationPanel.reset();
        triggerReload();
    }

    /** {@inheritDoc} */
    @Override
    public void onRangeChanged() {

        triggerReload();
    }

    /** {@inheritDoc} */
    @Override
    public void onCloseSidePanel() {

        locationPanel.close();
    }

    /** {@inheritDoc} */
    @Override
    public void onRecentreClicked() {

        var camera = layerSession.getCamera();
        if (camera == null) return;

        Vec2d target = recentreTarget();
        if (target == null) return;

        if (isPlayerTarget()) controlBar.switchToLayerContaining(Client.playerPositionY());

        double tolerance = .5 / camera.getVisualPixelsPerBlock();
        boolean onTarget = animation.isTargeting(target, tolerance) || isCameraAt(camera, target, tolerance);

        if (!onTarget) {

            panCameraToMapCoordinates(target, camera.getZoom());
            return;
        }

        double preferred = camera.clampZoom(camera.getPreferredZoom());
        double current = animation.isRunning() ? animation.targetZoom() : camera.getZoom();

        if (Math.abs(current - preferred) <= ZOOM_TOLERANCE) return;

        panCameraToMapCoordinates(target, preferred);
    }

    /**
     * Gets the relevant recenter target for the currently selected dimension.
     *
     * @return The player position or selected dimension centre, or {@code null} when no dimension is selected.
     */
    @Nullable
    private Vec2d recentreTarget() {

        Dimension selected = controlBar.selectedDimension();
        if (selected == null) return null;

        if (isPlayerTarget()) return Client.playerPosition2d();

        return new Vec2d(selected.getCenterX(), selected.getCenterZ());
    }

    /**
     * Checks whether recentering targets the current player position.
     *
     * @return True when the selected dimension is the player's current dimension.
     */
    private boolean isPlayerTarget() {

        Dimension selected = controlBar.selectedDimension();
        if (selected == null) return false;

        return Objects.equals(selected, Client.currentDimension()) && Client.player() != null;
    }

    /**
     * Checks whether the camera is already at the clamped recenter target.
     *
     * @param camera    The current map camera.
     * @param target    The recenter target before camera clamping.
     * @param tolerance Maximum coordinate difference to treat as equal.
     * @return True when moving to the target would not meaningfully change camera position.
     */
    private boolean isCameraAt(MapCamera camera, Vec2d target, double tolerance) {

        var contentArea = getPaddedContentArea();
        double clampedX = camera.clampWorldX(target.x(), contentArea.topLeftX());
        double clampedZ = camera.clampWorldZ(target.y(), contentArea.topLeftY());

        return Math.abs(camera.getWorldX() - clampedX) <= tolerance
                && Math.abs(camera.getWorldZ() - clampedZ) <= tolerance;
    }

    /**
     * Smoothly pans the camera to the given world position at the given zoom level.
     *
     * @param worldPos   The target world position, or {@code null} to skip.
     * @param targetZoom The target zoom level.
     */
    public void panCameraToMapCoordinates(@Nullable Vec2d worldPos, double targetZoom) {

        if (worldPos == null) return;

        var mapCamera = layerSession.getCamera();
        if (mapCamera == null) return;

        animation.start(worldPos, mapCamera, targetZoom);
    }

    /**
     * Main render loop. Draws the map or the placeholder, then all HUD overlays.
     *
     * @param context The draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     * @param delta   Time since last frame.
     */
    @Override
    protected void extractScreenRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        var mapCamera = layerSession.getCamera();
        var contentArea = getPaddedContentArea();

        if (layerSession.getRenderable() != null && mapCamera != null) {

            context.enableScissor(
                    contentArea.topLeftX(), contentArea.topLeftY(),
                    contentArea.topLeftX() + contentArea.guiWidth(),
                    contentArea.topLeftY() + contentArea.guiHeight());

            try {

                mapCamera.computeZoomLevelToFitContentArea(contentArea.guiWidth(), contentArea.guiHeight());

                if (!animation.isRunning())
                    mapCamera.update(delta, contentArea.topLeftX(), contentArea.topLeftY());

                context.fill(contentArea.topLeftX(), contentArea.topLeftY(),
                        contentArea.topLeftX() + contentArea.guiWidth(),
                        contentArea.topLeftY() + contentArea.guiHeight(),
                        com.duom.ardamaps.gui.ModConstants.COLOR_DARKER_BLUE);

                layerSession.getRenderable().render(context);

                regionBorderRenderer.render(context, mapCamera, layerSession.getExploration());
                regionLabelRenderer.render(context, font, mapCamera, layerSession.getExploration());

                markerRenderer.render(
                        context,
                        font,
                        mapCamera,
                        mapFrameRenderer,
                        controlBar.selectedRange(),
                        locationPanel.displayedLocationPosition(),
                        controlBar.enabledMarkerTypes(),
                        controlBar.isMouseOver(mouseX, mouseY) || locationPanel.isMouseOver(mouseX, mouseY),
                        MAP_FRAME_PADDING,
                        mouseX,
                        mouseY);

                if ((markerRenderer.getMouseOverLocation() != null || markerRenderer.getMouseOverWaypoint() != null)
                        && (mapContextMenu == null || !mapContextMenu.isMouseOver(mouseX, mouseY))) {
                    context.requestCursor(CursorTypes.POINTING_HAND);
                }

            } finally {

                context.disableScissor();
            }

            if (Client.debugOverlayVisible())
                overlayRenderer.renderDebugPanel(context, font, height,
                        layerSession.getRenderable().getDebugLoadingLines().stream().sorted().toList());

            mapFrameRenderer.render(context, contentArea);

            overlayRenderer.update(mouseX, mouseY, mapCamera, layerSession.getExploration(), mapFrameRenderer, MAP_FRAME_PADDING);
            overlayRenderer.renderCoordinates(context, font, contentArea);
            overlayRenderer.renderRegionName(context, font, contentArea);

        } else {

            overlayRenderer.renderPlaceholder(context, font, width, height);
        }

        super.extractScreenRenderState(context, mouseX, mouseY, delta);

        if (mapContextMenu != null) mapContextMenu.render(context, mouseX, mouseY);

        locationPanel.render(context, mouseX, mouseY);

        controlBar.renderOverlays(context, mouseX, mouseY, delta);
    }

    /** {@inheritDoc} */
    @Override
    public BackgroundRenderer.GuiLayout getPaddedContentArea() {

        return super.getPaddedContentArea();
    }

    /**
     * Per-tick update: drives camera animation and keeps the context menu pinned to world coordinates.
     */
    @Override
    protected void screenTick() {

        var mapCamera = layerSession.getCamera();
        if (mapCamera == null) return;

        var contentArea = getPaddedContentArea();

        if (animation.isRunning()) {

            mapContextMenu = null;
            animation.apply(mapCamera, contentArea.topLeftX(), contentArea.topLeftY());
        }

        if (mapContextMenu != null) {

            var screenPos = mapCamera.worldToScreenCoordinates(mapContextMenu.getWorldX(), mapContextMenu.getWorldZ());
            mapContextMenu.setPosition((int) screenPos.x(), (int) screenPos.y());
        }
    }

    /**
     * Responds to window resizes by updating the camera viewport and re-laying out controls.
     *
     * @param width  The new screen width.
     * @param height The new screen height.
     */
    @Override
    protected void screenResize(int width, int height) {

        var mapCamera = layerSession.getCamera();
        if (mapCamera != null) mapCamera.setViewportSize(width, height);

        super.screenResize(width, height);

        controlBar.layout();
        locationPanel.layout();
    }

    /**
     * Handles mouse clicks: routes to the context menu, side panel, history navigation, map dragging,
     * and right-click context-menu creation.
     *
     * @param event       The initiating mouse event.
     * @param doubleClick {@code true} if this is a double click.
     * @return {@code true} if the event was consumed.
     */
    @Override
    protected boolean screenMouseClicked(MouseButtonEvent event, boolean doubleClick) {

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        var mapCamera = layerSession.getCamera();
        if (mapCamera == null) return super.screenMouseClicked(event, doubleClick);

        var mouseInMapArea = mapFrameRenderer.coordinatesInFrame(mouseX, mouseY, MAP_FRAME_PADDING);
        boolean overBookLabel = controlBar.closeBehavioursNotAt(mouseX, mouseY);

        if (mapContextMenu != null) {

            var reopen = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && mouseInMapArea;
            if (!reopen) {

                if (mapContextMenu.isMouseOver(mouseX, mouseY)) {

                    return mapContextMenu.mouseClicked(mouseX, mouseY, button);
                }

                mapContextMenu = null;
                return true;
            }
        }

        if (locationPanel.isMouseOver(mouseX, mouseY)) {

            return locationPanel.mouseClicked(mouseX, mouseY, button);
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {

            locationPanel.navigateBack(this::panCameraToMapCoordinates);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_5) {

            return locationPanel.navigateForward(this::panCameraToMapCoordinates);
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseInMapArea && !overBookLabel) {

            int modifiers = event.modifiers();
            var pos = mapCamera.screenToWorldCoordinates(mouseX, mouseY);

            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {

                if (!isOutsideExploredArea(pos.x(), pos.y())) {

                    locationPanel.close();
                    MapContextMenuFactory.teleportTo(
                            mapCamera,
                            controlBar.selectedRange(),
                            pos.x(),
                            pos.y(),
                            controlBar::switchToLayerContaining);
                }

                return true;
            }

            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {

                MapContextMenuFactory.setWaypointAt(mapCamera, pos.x(), pos.y());
                return true;
            }

            dragging = true;
            animation.cancel();
            clickStartX = mouseX;
            clickStartY = mouseY;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && mouseInMapArea && !overBookLabel) {

            var pos = mapCamera.screenToWorldCoordinates(mouseX, mouseY);
            boolean outsideExplored = isOutsideExploredArea(pos.x(), pos.y());

            mapContextMenu = MapContextMenuFactory.create(
                    (int) mouseX, (int) mouseY, width, height,
                    mapCamera,
                    controlBar.selectedRange(),
                    markerRenderer.getMouseOverWaypoint(),
                    outsideExplored,
                    controlBar::switchToLayerContaining,
                    () -> mapContextMenu = null,
                    locationPanel::close);

            return true;
        }

        return super.screenMouseClicked(event, doubleClick);
    }

    /**
     * Checks whether a map position is outside the explored area.
     *
     * @param worldX The world X coordinate.
     * @param worldZ The world Z coordinate.
     * @return True when reveal-all is disabled and the position is unexplored.
     */
    private boolean isOutsideExploredArea(double worldX, double worldZ) {

        if (ArdaMapsClient.CONFIG.isMapRevealAll()) return false;

        var exploration = layerSession.getExploration();
        return exploration != null && !exploration.isWorldPosExplored(worldX, worldZ, 0);
    }

    /**
     * Handles mouse releases: ends dragging and dispatches marker-click handling.
     *
     * @param event The mouse-button release event.
     * @return {@code true} if the event was consumed.
     */
    @Override
    protected boolean screenMouseReleased(MouseButtonEvent event) {

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        boolean handledByChild = super.screenMouseReleased(event);

        if (!handledByChild) handledByChild = controlBar.releaseRangeDrag(event);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {

            dragging = false;

            double distanceSquared = Math.pow(mouseX - clickStartX, 2) + Math.pow(mouseY - clickStartY, 2);
            if (!handledByChild && distanceSquared < CLICK_THRESHOLD_SQUARED) handleMapMarkerClick();

            return true;
        }

        return handledByChild;
    }

    /**
     * Handles a click on a map marker: opens the side panel for the clicked location,
     * or closes it when nothing was clicked.
     */
    private void handleMapMarkerClick() {

        if (Client.player() == null) return;

        var mouseOverLocation = markerRenderer.getMouseOverLocation();
        if (mouseOverLocation != null) {

            controlBar.switchToLayerContaining(mouseOverLocation.getPosition().y());
            panAndSelectLocation(mouseOverLocation, false);

        } else {

            locationPanel.close();
        }
    }

    /**
     * Pans the camera to the given location, opens the side panel, and pushes the entry onto the history.
     *
     * @param location The location to display.
     * @param focused  If {@code true}, zoom to the preferred identity zoom.
     */
    public void panAndSelectLocation(LocationClient location, boolean focused) {

        if (location == null) return;

        locationPanel.selectAndPan(location, focused, this::panCameraToMapCoordinates);
    }

    /**
     * Handles mouse dragging for panning the map.
     *
     * @param event The initiating mouse event.
     * @param dx    Change in x position.
     * @param dy    Change in y position.
     * @return {@code true} if the event was consumed.
     */
    @Override
    protected boolean screenMouseDragged(MouseButtonEvent event, double dx, double dy) {

        double mouseX = event.x();
        double mouseY = event.y();

        var mapCamera = layerSession.getCamera();
        if (mapCamera == null || !dragging || controlBar.isMouseOver(mouseX, mouseY) || locationPanel.isMouseOver(mouseX, mouseY))
            return super.screenMouseDragged(event, dx, dy);

        mapCamera.resetZoomAnchor();

        var contentArea = getPaddedContentArea();
        var worldOrigin = mapCamera.screenToWorldCoordinates(0, 0);
        var worldDelta = mapCamera.screenToWorldCoordinates(dx, dy);

        mapCamera.setWorldX(mapCamera.getWorldX() - (worldDelta.x() - worldOrigin.x()), contentArea.topLeftX());
        mapCamera.setWorldZ(mapCamera.getWorldZ() - (worldDelta.y() - worldOrigin.y()), contentArea.topLeftY());

        return true;
    }

    /**
     * Handles scroll events for zooming the map.
     *
     * @param mouseX           Mouse x position.
     * @param mouseY           Mouse y position.
     * @param horizontalAmount Horizontal scroll amount.
     * @param verticalAmount   Vertical scroll amount.
     * @return {@code true} if the event was consumed.
     */
    @Override
    protected boolean screenMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        if (controlBar.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;

        if (controlBar.isMouseOver(mouseX, mouseY) || locationPanel.isMouseOver(mouseX, mouseY)) {

            if (locationPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;

            return super.screenMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        var cam = layerSession.getCamera();
        if (cam != null) {

            animation.cancel();
            cam.setZoom(mouseX, mouseY, width, height, verticalAmount * 0.5);
        }

        return true;
    }

    /**
     * Handles key presses. ESC closes the side panel if one is open.
     *
     * @param event The key event.
     * @return {@code true} if the event was consumed.
     */
    @Override
    protected boolean screenKeyPressed(KeyEvent event) {

        if (controlBar.keyPressed(event)) return true;

        if (event.key() == GLFW.GLFW_KEY_ESCAPE && locationPanel.isOpen()) {

            locationPanel.close();
            return true;
        }

        return super.screenKeyPressed(event);
    }

    /** {@inheritDoc} */
    @Override
    public int getContentPadding() {

        return MAP_FRAME_PADDING;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean isSearchable() {

        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected @Nullable Function<String, List<?>> getSearchFunction() {

        return query -> {

            var camera = layerSession.getCamera();
            if (camera == null) return List.of();

            var dimensionId = camera.getDimension().getId();
            return locationSearch.search(dimensionId, controlBar.enabledMarkerTypes(), query);
        };
    }

    /** {@inheritDoc} */
    @Override
    protected Function<Object, Void> getOnSearcheResultSelectedFunction() {

        return element -> {

            if (element instanceof LocationClient location) panAndSelectLocation(location, true);
            return null;
        };
    }

    /** {@inheritDoc} */
    @Override
    protected @Nullable Function<Object, String> getSearchResultRenderFunction() {

        return obj -> {

            if (obj instanceof LocationClient location) return location.getName();
            return Objects.toString(obj, "");
        };
    }
}
