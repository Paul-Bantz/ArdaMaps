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

package com.duom.ardamaps.gui.screens.map;

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.Vec3d;
import com.duom.ardamaps.core.data.location.LocationClient;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.widgets.SidePanelWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Controls the map screen's location side panel: opening, closing, positioning, history navigation,
 * and forwarding render and input events. Owns the {@link LocationNavigationHistory}.
 */
@Environment(EnvType.CLIENT)
public class LocationPanelController {

    /** The screen that hosts the side panel (passed as {@code parent} to {@link SidePanelWidget}). */
    private final Screen hostScreen;

    /** Supplies the font used to construct new side-panel widgets. */
    private final Supplier<Font> fontSupplier;

    /** Provides the current camera for coordinate transforms and zoom queries. */
    private final Supplier<MapCamera> cameraSupplier;

    /** Provides the padded content area for layout calculations. */
    private final Supplier<BackgroundRenderer.GuiLayout> contentAreaSupplier;

    /** Provides the full screen height for layout calculations. */
    private final Supplier<Integer> screenHeightSupplier;

    /** Navigation history for the location side panel. */
    private final LocationNavigationHistory history = new LocationNavigationHistory();

    /** The currently open side panel, or {@code null} when none is shown. */
    @Nullable
    private SidePanelWidget panel;

    /**
     * Creates a new controller.
     *
     * @param hostScreen           The screen that owns the panel widget.
     * @param fontSupplier         Supplies the font for the panel's text.
     * @param cameraSupplier       Supplies the active camera (may return {@code null}).
     * @param contentAreaSupplier  Supplies the padded content area for panel positioning.
     * @param screenHeightSupplier Supplies the full screen height.
     */
    public LocationPanelController(Screen hostScreen, Supplier<Font> fontSupplier,
                                   Supplier<MapCamera> cameraSupplier,
                                   Supplier<BackgroundRenderer.GuiLayout> contentAreaSupplier,
                                   Supplier<Integer> screenHeightSupplier) {

        this.hostScreen = hostScreen;
        this.fontSupplier = fontSupplier;
        this.cameraSupplier = cameraSupplier;
        this.contentAreaSupplier = contentAreaSupplier;
        this.screenHeightSupplier = screenHeightSupplier;
    }

    /**
     * Opens the panel for the given location, pans the camera, and pushes the entry onto the history.
     *
     * @param location  The location to display.
     * @param focused   If {@code true}, zoom to the preferred identity zoom level.
     * @param panCamera Callback that pans the camera to given world coordinates at given zoom.
     */
    public void selectAndPan(LocationClient location, boolean focused, PanCallback panCamera) {

        if (location == null) return;

        history.push(location);
        applyPanel(location, focused, panCamera);
    }

    /**
     * Opens the side panel for {@code location}, positioning and animating the camera.
     *
     * @param location  The location to display.
     * @param focused   Whether to zoom to the preferred zoom level.
     * @param panCamera Callback to animate the camera.
     */
    private void applyPanel(LocationClient location, boolean focused, PanCallback panCamera) {

        var mapCamera = cameraSupplier.get();
        if (mapCamera == null || location == null) return;

        Vec3d locationPosition = location.getPosition();
        var targetZoom = mapCamera.getZoom();
        var focusedZoom = mapCamera.getPreferredZoom();

        var cameraOffset = computePanelCameraOffset(mapCamera, locationPosition, targetZoom);
        var focusedCameraOffset = computePanelCameraOffset(mapCamera, locationPosition, focusedZoom);

        panel = new SidePanelWidget(
                hostScreen,
                fontSupplier.get(),
                location,
                focusedCameraOffset,
                focusedZoom,
                this::close);

        layout();

        Vec2d target = focused ? focusedCameraOffset : cameraOffset;
        double zoom = focused ? focusedZoom : targetZoom;
        panCamera.pan(target, zoom);
    }

    /**
     * Computes the camera world position that centres the left part of the viewport on the location.
     *
     * @param mapCamera        The camera to compute against.
     * @param locationPosition The position of the location.
     * @param zoom             The zoom level for the calculation.
     * @return The offset camera world position, or {@code null} when the location is at the origin.
     */
    @Nullable
    private Vec2d computePanelCameraOffset(MapCamera mapCamera, Vec3d locationPosition, double zoom) {

        if (locationPosition.x() == 0 && locationPosition.z() == 0) return null;

        var contentArea = contentAreaSupplier.get();
        int screenHeight = screenHeightSupplier.get();
        int panelWidth = sidePanelWidth(contentArea);

        var screenLeftCenterX = contentArea.topLeftX() + (contentArea.guiWidth() - panelWidth) / 2;
        var screenLeftCenterY = screenHeight / 2;

        var worldLeftCenter = mapCamera.screenToWorldCoordinates(screenLeftCenterX, screenLeftCenterY, zoom);
        var worldViewportCenter = mapCamera.screenToWorldCoordinates(
                contentArea.topLeftX() + contentArea.guiWidth() / 2f, screenLeftCenterY);

        var translationX = worldLeftCenter.x() - worldViewportCenter.x();
        var translationY = worldLeftCenter.y() - worldViewportCenter.y();

        return new Vec2d(locationPosition.x() - translationX, locationPosition.z() - translationY);
    }

    /**
     * Recalculates and applies the panel's position and size to match the current content area.
     */
    public void layout() {

        if (panel == null) return;

        var contentArea = contentAreaSupplier.get();
        int panelWidth = sidePanelWidth(contentArea);
        int panelHeight = contentArea.guiHeight() - 16;
        int xPos = contentArea.topLeftX() + contentArea.guiWidth() - panelWidth - 8;
        int yPos = contentArea.topLeftY() + 8;

        panel.setSize(panelWidth, panelHeight);
        panel.setPosition(xPos, yPos);
    }

    /**
     * Returns the width of the side panel for the given content area.
     *
     * @param contentArea The padded content area.
     * @return the panel width in pixels.
     */
    private static int sidePanelWidth(BackgroundRenderer.GuiLayout contentArea) {

        return (contentArea.guiWidth() / 3) + 32;
    }

    /**
     * Navigates backwards in the history. Closes the panel when history is exhausted.
     *
     * @param panCamera Callback that pans the camera.
     */
    public void navigateBack(PanCallback panCamera) {

        var previous = history.back();
        if (previous != null) {

            applyPanel(previous, false, panCamera);
        } else {

            panel = null;
        }
    }

    /**
     * Navigates forward in the history.
     *
     * @param panCamera Callback that pans the camera.
     * @return {@code true} if there was a forward entry to navigate to.
     */
    public boolean navigateForward(PanCallback panCamera) {

        var next = history.forward();
        if (next != null) {

            applyPanel(next, false, panCamera);
            return true;
        }

        return false;
    }

    /**
     * Closes the panel without clearing the history.
     */
    public void close() {

        panel = null;
    }

    /**
     * Clears the history and closes the panel (used when switching layers or dimensions).
     */
    public void reset() {

        history.clear();
        panel = null;
    }

    /**
     * Preserves the existing panel across a screen re-init by re-assigning it.
     * Called at the end of {@code MapScreen.init()} when the panel was open before the re-init.
     *
     * @param preserved The panel to keep, or {@code null} to close.
     */
    public void restorePanel(@Nullable SidePanelWidget preserved) {

        this.panel = preserved;
    }

    /**
     * Snapshots the current panel so the caller can later call {@link #restorePanel} after re-init.
     *
     * @return The current panel, or {@code null} when none is open.
     */
    @Nullable
    public SidePanelWidget snapshotPanel() {

        return panel;
    }

    /**
     * Returns {@code true} when a side panel is currently open.
     *
     * @return whether the panel is visible.
     */
    public boolean isOpen() {

        return panel != null;
    }

    /**
     * Returns the world position of the focused location, or {@code null} when none is open.
     *
     * @return the displayed location position, or {@code null}.
     */
    @Nullable
    public Vec3d displayedLocationPosition() {

        return panel != null ? panel.getDisplayedLocationPosition() : null;
    }

    /**
     * Renders the side panel if one is open.
     *
     * @param context The draw context.
     * @param mouseX  Mouse x position.
     * @param mouseY  Mouse y position.
     */
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        if (panel != null) panel.render(context, mouseX, mouseY);
    }

    /**
     * Returns {@code true} when the mouse is over the side panel.
     *
     * @param mouseX Mouse x position.
     * @param mouseY Mouse y position.
     * @return whether the panel is hovered.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {

        return panel != null && panel.isMouseOver(mouseX, mouseY);
    }

    /**
     * Forwards a mouse-click to the panel.
     *
     * @param mouseX Mouse x position.
     * @param mouseY Mouse y position.
     * @param button The clicked button index.
     * @return whether the panel consumed the event.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        return panel != null && panel.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Forwards a mouse-scroll event to the panel.
     *
     * @param mouseX           Mouse x position.
     * @param mouseY           Mouse y position.
     * @param horizontalAmount Horizontal scroll amount.
     * @param verticalAmount   Vertical scroll amount.
     * @return whether the panel consumed the event.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        return panel != null && panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Callback interface for panning the camera to a world position at a target zoom.
     */
    @FunctionalInterface
    public interface PanCallback {

        /**
         * Pans the camera to the given world position at the given zoom.
         *
         * @param worldPos   The target world position, or {@code null} to skip the pan.
         * @param targetZoom The target zoom level.
         */
        void pan(@Nullable Vec2d worldPos, double targetZoom);
    }
}
