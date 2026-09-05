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

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.core.data.config.MapLayerDefinition;
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.widgets.*;
import com.duom.ardamaps.gui.widgets.builders.BookLabelButtonBuilder;
import com.duom.ardamaps.gui.widgets.builders.RangeSelectionWidgetBuilder;
import com.duom.ardamaps.gui.widgets.popup.MarkersPopupContent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Owns and manages the bottom-left book-label control stack on the map screen:
 * the dimension, layer and tools buttons and the range-selection widget.
 * Also holds the associated selection state — {@link #selectedDimension},
 * {@link #selectedLayer}, {@link #selectedRange} and the {@link MarkerTypeFilter}.
 * Communicates back to the map screen through the {@link Host} interface.
 */
@Environment(EnvType.CLIENT)
public class MapControlBar {

    /** Gap between the recentre label bottom edge and the bottom button anchor. */
    private static final int RECENTRE_LABEL_BOTTOM_GAP = -15;

    /** Frame offset for bottom-row button baseline alignment. */
    private static final int FRAME_OFFSET = 2;

    /** Horizontal layout unit the range-selection widget is offset and sized against. */
    private static final int RANGE_LAYOUT_UNIT = 90;

    /** The owning screen host. */
    private final Host host;

    /** Session-only marker type filter. */
    private final MarkerTypeFilter markerFilter = new MarkerTypeFilter();

    /** The dimension selected in the UI dropdown. */
    @Nullable
    private Dimension selectedDimension;

    /** The map layer selected in the UI dropdown. */
    @Nullable
    private MapLayerDefinition selectedLayer;

    /** The vertical range selected in the UI, or {@code null} when the layer has no ranges. */
    @Nullable
    private MapLayerRange selectedRange;

    /** Dimension label button. */
    @Nullable
    private BookLabelButtonWidget dimensionButton;

    /** Layer label button. */
    @Nullable
    private BookLabelButtonWidget layerButton;

    /** Tools label button. */
    @Nullable
    private BookLabelButtonWidget toolsButton;

    /** Range selection widget for ranged map layers. */
    @Nullable
    private RangeSelectionWidget rangeSelectionWidget;

    /**
     * Constructs the control bar backed by the given host.
     *
     * @param host The owning map screen.
     */
    public MapControlBar(Host host) {

        this.host = host;
    }

    /**
     * Returns the currently selected dimension.
     *
     * @return the selected dimension, or {@code null} when none is selected.
     */
    @Nullable
    public Dimension selectedDimension() {

        return selectedDimension;
    }

    /**
     * Returns the currently selected map layer.
     *
     * @return the selected map layer, or {@code null} when none is selected.
     */
    @Nullable
    public MapLayerDefinition selectedLayer() {

        return selectedLayer;
    }

    /**
     * Returns the currently selected vertical range.
     *
     * @return the selected range, or {@code null} when the layer has no ranges.
     */
    @Nullable
    public MapLayerRange selectedRange() {

        return selectedRange;
    }

    /**
     * Returns the set of enabled marker-type keys, or {@code null} when all are enabled.
     *
     * @return enabled marker-type keys, or {@code null} for all.
     */
    @Nullable
    public Set<String> enabledMarkerTypes() {

        return markerFilter.isAllEnabled() ? null : markerFilter.enabledKeys();
    }

    /**
     * Rebuilds all widgets for a screen (re-)init. Should be called from {@code MapScreen.init()}.
     * May trigger a layer reload via {@link Host#onLayerSelectionChanged()} if the resolved layer differs
     * from the previously selected one.
     */
    public void rebuild() {

        configureDimensionButton();
        configureLayerButton();
        configureRangeSelectionWidget();
        configureToolsButton();
    }

    /**
     * Makes the buttons visible after a map layer has been loaded successfully.
     */
    public void onMapLoaded() {

        if (dimensionButton != null) dimensionButton.visible = true;
        if (toolsButton != null) toolsButton.visible = selectedDimension != null;
        if (layerButton != null)
            layerButton.visible = MapLayerDropdownOptions.forDimension(selectedDimension).size() > 1 && host.hasCamera();
        layout();
    }

    /**
     * Updates the panel-position of all controls to reflect the current content area.
     */
    @SuppressWarnings("ConstantValue")
    public void layout() {

        BackgroundRenderer.GuiLayout contentArea = host.getPaddedContentArea();

        int centerX = host.getScreenWidth() / 2 - RANGE_LAYOUT_UNIT / 2;
        int rangeRightX = centerX + RANGE_LAYOUT_UNIT;

        int bottomLeftY = contentArea.topLeftY() + contentArea.guiHeight();
        int bottomY = bottomLeftY + FRAME_OFFSET - Button.DEFAULT_HEIGHT / 2;

        int recentreY = bottomY - RECENTRE_LABEL_BOTTOM_GAP - ModConstants.BOOK_LABEL_BUTTON_HEIGHT;
        int labelPitch = ModConstants.BOOK_LABEL_BUTTON_HEIGHT;
        int labelX = contentArea.topLeftX() - (BookLabelButtonWidget.foldedWidth(labelPitch)
                - BookLabelButtonWidget.capRightWidth(labelPitch)) + 4;
        int slot = 0;

        if (dimensionButton != null && dimensionButton.visible)
            dimensionButton.setPosition(labelX, recentreY - slot++ * labelPitch);

        if (layerButton != null && layerButton.visible)
            layerButton.setPosition(labelX, recentreY - slot++ * labelPitch);

        if (toolsButton != null && toolsButton.visible)
            toolsButton.setPosition(labelX, recentreY - slot * labelPitch);

        if (rangeSelectionWidget != null) {

            rangeSelectionWidget.setWidth((contentArea.guiWidth() / 2) - RANGE_LAYOUT_UNIT / 2);
            rangeSelectionWidget.setPosition(rangeRightX, bottomLeftY - rangeSelectionWidget.getHeight() - 1);
        }
    }

    /**
     * Closes dropdowns/popups that are not at the cursor position (called from mouse-click handling).
     * Returns {@code true} when the cursor is over any book-label button.
     *
     * @param mouseX Mouse x position.
     * @param mouseY Mouse y position.
     * @return whether the cursor is over a book-label button.
     */
    public boolean closeBehavioursNotAt(double mouseX, double mouseY) {

        boolean overDimension = dimensionButton != null && dimensionButton.isMouseOver(mouseX, mouseY);
        boolean overLayer = layerButton != null && layerButton.isMouseOver(mouseX, mouseY);
        boolean overTools = toolsButton != null && toolsButton.isMouseOver(mouseX, mouseY);

        if (!overDimension && dimensionButton != null) dimensionButton.closeBehaviour();
        if (!overLayer && layerButton != null) layerButton.closeBehaviour();
        if (!overTools && toolsButton != null) toolsButton.closeBehaviour();

        return overDimension || overLayer || overTools;
    }

    /**
     * Forwards a mouse-release to the range widget to recover from a stuck drag.
     * Must be called after the regular parent dispatch.
     *
     * @param event The mouse-button release event.
     * @return whether the range widget consumed the event.
     */
    public boolean releaseRangeDrag(MouseButtonEvent event) {

        if (rangeSelectionWidget != null && rangeSelectionWidget.isDragging()) {

            return rangeSelectionWidget.mouseReleased(event);
        }

        return false;
    }

    /**
     * Routes key presses to any open book-label behaviour.
     *
     * @param event The key event.
     * @return True if a behaviour consumed the key.
     */
    public boolean keyPressed(KeyEvent event) {

        return isOpenAndHandles(dimensionButton, event)
                || isOpenAndHandles(layerButton, event)
                || isOpenAndHandles(toolsButton, event);
    }

    /**
     * Routes scroll input to any open book-label behaviour.
     *
     * @param mouseX           Mouse x position.
     * @param mouseY           Mouse y position.
     * @param horizontalAmount Horizontal scroll amount.
     * @param verticalAmount   Vertical scroll amount.
     * @return True if a behaviour consumed the scroll.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        return isOpenAndScrolls(dimensionButton, mouseX, mouseY, horizontalAmount, verticalAmount)
                || isOpenAndScrolls(layerButton, mouseX, mouseY, horizontalAmount, verticalAmount)
                || isOpenAndScrolls(toolsButton, mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Returns whether an open button behaviour handles the key event.
     *
     * @param button The button to test, or {@code null}.
     * @param event  The key event.
     * @return True if the button consumed the key.
     */
    private static boolean isOpenAndHandles(@Nullable BookLabelButtonWidget button, KeyEvent event) {

        return button != null && button.keyPressed(event);
    }

    /**
     * Returns whether an open button behaviour handles the scroll event.
     *
     * @param button           The button to test, or {@code null}.
     * @param mouseX           Mouse x position.
     * @param mouseY           Mouse y position.
     * @param horizontalAmount Horizontal scroll amount.
     * @param verticalAmount   Vertical scroll amount.
     * @return True if the button consumed the scroll.
     */
    private static boolean isOpenAndScrolls(@Nullable BookLabelButtonWidget button, double mouseX, double mouseY,
                                            double horizontalAmount, double verticalAmount) {

        return button != null
                && button.isBehaviourOpen()
                && button.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Returns {@code true} when the mouse is over any of the managed controls.
     *
     * @param mouseX Mouse x position.
     * @param mouseY Mouse y position.
     * @return whether the mouse is over a control.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {

        return isOver(mouseX, mouseY, rangeSelectionWidget)
                || isOver(mouseX, mouseY, toolsButton)
                || isOver(mouseX, mouseY, dimensionButton)
                || isOver(mouseX, mouseY, layerButton);
    }

    /**
     * Returns {@code true} when the mouse is over the given widget (null-safe).
     *
     * @param mouseX Mouse x position.
     * @param mouseY Mouse y position.
     * @param widget The widget to test, or {@code null}.
     * @return whether the mouse is over the widget.
     */
    private static boolean isOver(double mouseX, double mouseY,
                                  @Nullable net.minecraft.client.gui.components.events.GuiEventListener widget) {

        return widget != null && widget.isMouseOver(mouseX, mouseY);
    }

    /**
     * Renders open dropdowns and popups above all other screen content.
     *
     * @param context The draw context.
     * @param mouseX  Mouse x position.
     * @param mouseY  Mouse y position.
     * @param delta   The frame delta.
     */
    public void renderOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (dimensionButton != null) dimensionButton.renderOverlay(context, mouseX, mouseY, delta);
        if (layerButton != null) layerButton.renderOverlay(context, mouseX, mouseY, delta);
        if (toolsButton != null) toolsButton.renderOverlay(context, mouseX, mouseY, delta);
    }

    /**
     * Switches the selected range to the one containing the given Y coordinate.
     * No-ops when the current layer has no ranges or when the resolved range is already selected.
     *
     * @param y The Y coordinate to match against configured ranges, or {@code null} to skip.
     */
    public void switchToLayerContaining(@Nullable Double y) {

        if (y == null || selectedLayer == null || !selectedLayer.hasRanges()) return;

        MapLayerRange target = selectedLayer.rangeForY(y);

        if (target != null && !Objects.equals(target, selectedRange)) {

            if (rangeSelectionWidget != null) rangeSelectionWidget.setSelected(target);
            rangeSelectionChanged(target);
        }
    }

    /**
     * Rebuilds the dimension button and registers it with the host.
     */
    private void configureDimensionButton() {

        if (dimensionButton != null) host.removeControl(dimensionButton);

        List<Dimension> dimensions = ArdaMapsClient.CONFIG != null && ArdaMapsClient.CONFIG.getDimensions() != null
                ? ArdaMapsClient.CONFIG.getDimensions()
                : new ArrayList<>();

        var defaultSelection = selectedDimension != null ? selectedDimension : Client.currentDimension();

        if (defaultSelection == null && !dimensions.isEmpty()) defaultSelection = dimensions.getFirst();

        selectedDimension = defaultSelection;
        refreshMarkerFilter(true);

        List<Dimension> sortedDimensions = new ArrayList<>(dimensions);
        sortedDimensions.sort(Comparator.comparing(Dimension::getName, String.CASE_INSENSITIVE_ORDER));

        var entries = sortedDimensions.stream()
                .map(dim -> new ContextMenu.Entry(Component.literal(dim.getName()), () -> selectDimension(dim)))
                .toList();

        dimensionButton = BookLabelButtonBuilder.create()
                .setHeight(ModConstants.BOOK_LABEL_BUTTON_HEIGHT)
                .setType(BookLabelButtonType.BLUE)
                .setBehaviour(new BookLabelDropdown(() -> entries, () -> sortedDimensions.indexOf(selectedDimension)))
                .setDirectSelection(true)
                .setText(Component.translatable("ardamaps.client.map.screen.world.label"))
                .setIcon(ModConstants.WORLD_ICON)
                .setIconSize(16)
                .build();
        installHoverGate(dimensionButton);
        dimensionButton.visible = host.hasCamera();

        layout();

        host.addControl(dimensionButton);
    }

    /**
     * Rebuilds the layer button and registers it with the host.
     * May trigger {@link Host#onLayerSelectionChanged()} if the resolved layer differs from the previous.
     */
    private void configureLayerButton() {

        List<MapLayerDefinition> mapLayers = MapLayerDropdownOptions.forDimension(selectedDimension);

        MapLayerDefinition previous = mapLayers.contains(selectedLayer) ? selectedLayer : null;

        String rememberedLayerName = ArdaMapsClient.CONFIG == null ? null
                : ArdaMapsClient.CONFIG.getLastMapLayer(selectedDimension);
        MapLayerDefinition remembered = rememberedLayerName == null ? null : mapLayers.stream()
                .filter(layer -> Objects.equals(layer.layer(), rememberedLayerName))
                .findFirst()
                .orElse(null);

        MapLayerDefinition resolved = previous != null
                ? previous
                : remembered != null
                ? remembered
                : mapLayers.isEmpty() ? null : mapLayers.getFirst();

        if (layerButton != null) host.removeControl(layerButton);

        List<MapLayerDefinition> sortedLayers = new ArrayList<>(mapLayers);
        sortedLayers.sort(Comparator.comparing(MapLayerDefinition::layer, String.CASE_INSENSITIVE_ORDER));

        var entries = sortedLayers.stream()
                .map(layer -> new ContextMenu.Entry(Component.literal(layer.layer()), () -> selectLayer(layer)))
                .toList();

        layerButton = BookLabelButtonBuilder.create()
                .setHeight(ModConstants.BOOK_LABEL_BUTTON_HEIGHT)
                .setType(BookLabelButtonType.BLUE)
                .setBehaviour(new BookLabelDropdown(() -> entries, () -> sortedLayers.indexOf(selectedLayer)))
                .setDirectSelection(true)
                .setText(Component.translatable("ardamaps.client.map.screen.layer.label"))
                .setIcon(layerIcon(resolved))
                .build();
        installHoverGate(layerButton);
        layerButton.visible = mapLayers.size() > 1 && host.hasCamera();

        layout();

        host.addControl(layerButton);

        selectedLayer = resolved;
        if (resolved != null && !Objects.equals(previous, resolved)) layerSelectionChanged(resolved);
    }

    /**
     * Rebuilds the range selection widget and registers it with the host.
     */
    private void configureRangeSelectionWidget() {

        if (rangeSelectionWidget != null) host.removeControl(rangeSelectionWidget);

        rangeSelectionWidget = RangeSelectionWidgetBuilder.create()
                .setSize(100, 15)
                .setLabel(Component.translatable("ardamaps.client.map.screen.range.label"))
                .setItemWidth(15)
                .setOnSelect(this::rangeSelectionChanged)
                .build();

        host.addControl(rangeSelectionWidget);
        refreshRangeSelection();
    }

    /**
     * Reacts to a vertical-range change: updates the selection and notifies the host to reload.
     *
     * @param range The newly selected range.
     */
    private void rangeSelectionChanged(MapLayerRange range) {

        selectedRange = range;
        host.onRangeChanged();
    }

    /**
     * Rebuilds the tools button and registers it with the host.
     */
    private void configureToolsButton() {

        if (toolsButton != null) host.removeControl(toolsButton);

        refreshMarkerFilter(false);

        BookLabelMenu[] menu = new BookLabelMenu[1];
        menu[0] = new BookLabelMenu(
                () -> List.of(
                        new ContextMenu.Entry(markersButtonText(), () -> {

                            host.onCloseSidePanel();
                            menu[0].openPopup();
                        }),
                        new ContextMenu.Entry(regionBordersText(), this::toggleRegionBorders),
                        new ContextMenu.Entry(
                                Component.translatable("ardamaps.client.map.screen.recentre"),
                                host::onRecentreClicked)),
                () -> new MarkersPopupContent(
                        host::getPaddedContentArea,
                        markerFilter,
                        this::markersPopupChanged));

        toolsButton = BookLabelButtonBuilder.create()
                .setHeight(ModConstants.BOOK_LABEL_BUTTON_HEIGHT)
                .setType(BookLabelButtonType.BLUE)
                .setBehaviour(menu[0])
                .setText(Component.translatable("ardamaps.client.map.screen.tools.label"))
                .setIcon(ModConstants.EYE_ICON)
                .setIconSize(16)
                .build();
        installHoverGate(toolsButton);
        toolsButton.visible = selectedDimension != null;

        layout();

        host.addControl(toolsButton);
    }

    /**
     * Gets the label for the region border toggle entry.
     *
     * @return The region border entry label.
     */
    private Component regionBordersText() {

        return Component.translatable(ArdaMapsClient.CONFIG.isShowRegionBorders()
                ? "ardamaps.client.map.screen.regions.hide"
                : "ardamaps.client.map.screen.regions.show");
    }

    /**
     * Toggles region borders and persists the client configuration.
     */
    private void toggleRegionBorders() {

        ArdaMapsClient.CONFIG.setShowRegionBorders(!ArdaMapsClient.CONFIG.isShowRegionBorders());
        ArdaMapsClient.CONFIG_MANAGER.save();
    }

    /**
     * Persists marker popup option changes.
     */
    private void markersPopupChanged() {

        ArdaMapsClient.CONFIG_MANAGER.save();
    }

    /**
     * Handles a dimension selection from the dropdown.
     *
     * @param newDimension The newly selected dimension.
     */
    private void selectDimension(Dimension newDimension) {

        selectedDimension = newDimension;
        dimensionSelectionChanged(newDimension);
    }

    /**
     * Reacts to a dimension change: resets the marker filter and rebuilds the layer button.
     *
     * @param newDimension The newly selected dimension.
     */
    private void dimensionSelectionChanged(Dimension newDimension) {

        selectedDimension = newDimension;
        refreshMarkerFilter(true);
        host.onCloseSidePanel();
        configureLayerButton();
    }

    /**
     * Handles a layer selection from the dropdown.
     *
     * @param layer The newly selected map layer.
     */
    private void selectLayer(MapLayerDefinition layer) {

        selectedLayer = layer;
        layerSelectionChanged(layer);
    }

    /**
     * Reacts to a layer selection change: persists the choice, updates the range, and notifies the host.
     *
     * @param mapLayerDefinition The newly selected map layer.
     */
    private void layerSelectionChanged(MapLayerDefinition mapLayerDefinition) {

        selectedLayer = mapLayerDefinition;
        selectedRange = mapLayerDefinition.hasRanges() ? defaultRangeForLayer(mapLayerDefinition) : null;
        if (layerButton != null) layerButton.setIcon(layerIcon(mapLayerDefinition));

        if (ArdaMapsClient.CONFIG != null)
            ArdaMapsClient.CONFIG.setLastMapLayer(selectedDimension, mapLayerDefinition.layer());

        refreshRangeSelection();
        host.onLayerSelectionChanged();
    }

    /**
     * Returns the icon for the given layer or the generic layer-selection icon.
     *
     * @param layer The layer whose icon should be shown.
     * @return an atlas sprite path or absolute image URL.
     */
    private static String layerIcon(@Nullable MapLayerDefinition layer) {

        return layer == null || layer.icon() == null || layer.icon().isBlank()
                ? ModConstants.LAYERS_ICON.toString()
                : layer.icon();
    }

    /**
     * Selects the range matching the player's current Y, falling back to the first configured range.
     *
     * @param layer The ranged layer to inspect.
     * @return the default range, or {@code null} when the layer has no ranges.
     */
    @Nullable
    private MapLayerRange defaultRangeForLayer(MapLayerDefinition layer) {

        if (!layer.hasRanges()) return null;

        Double playerY = Client.playerPositionY();
        return playerY == null ? layer.ranges().getFirst() : layer.rangeForY(playerY);
    }

    /**
     * Refreshes the range-selection widget to reflect the current layer's ranges.
     */
    private void refreshRangeSelection() {

        if (rangeSelectionWidget == null) return;

        List<MapLayerRange> ranges = selectedLayer != null && selectedLayer.hasRanges()
                ? new ArrayList<>(selectedLayer.ranges())
                : new ArrayList<>();

        if (!ranges.isEmpty() && (selectedRange == null || !ranges.contains(selectedRange)))
            selectedRange = ranges.getFirst();

        rangeSelectionWidget.setRanges(ranges);
        rangeSelectionWidget.setSelected(selectedRange);
        rangeSelectionWidget.visible = !ranges.isEmpty();
        layout();
    }

    /**
     * Rebuilds the marker filter for the currently selected dimension.
     *
     * @param enableAll {@code true} to reset the filter to all available types.
     */
    private void refreshMarkerFilter(boolean enableAll) {

        markerFilter.refresh(selectedDimension != null ? selectedDimension.getId() : null);
        if (enableAll) markerFilter.enableAll();
    }

    /**
     * Installs the shared hover gate on a book-label button.
     *
     * @param button The button to configure.
     */
    private void installHoverGate(BookLabelButtonWidget button) {

        button.setHoverGate((candidate, mouseX, mouseY) -> !isBlockedByOtherContent(candidate, mouseX, mouseY));
        button.setOpenListener(this::collapseOthers);
    }

    /**
     * Collapses every managed book label except the one opening now.
     *
     * @param opening The button that is about to open.
     */
    private void collapseOthers(BookLabelButtonWidget opening) {

        collapseIfOther(dimensionButton, opening);
        collapseIfOther(layerButton, opening);
        collapseIfOther(toolsButton, opening);
    }

    /**
     * Collapses a visible button when it is not the opening button.
     *
     * @param button  The button to inspect, or {@code null}.
     * @param opening The button that is about to open.
     */
    private static void collapseIfOther(@Nullable BookLabelButtonWidget button, BookLabelButtonWidget opening) {

        if (button != null && button != opening && button.visible) button.collapse();
    }

    /**
     * Checks whether another visible book-label's content covers the cursor.
     *
     * @param candidate The button considering a hover-open.
     * @param mouseX    Mouse x position.
     * @param mouseY    Mouse y position.
     * @return True if another open menu is under the cursor.
     */
    private boolean isBlockedByOtherContent(BookLabelButtonWidget candidate, double mouseX, double mouseY) {

        return isOtherContentMouseOver(candidate, dimensionButton, mouseX, mouseY)
                || isOtherContentMouseOver(candidate, layerButton, mouseX, mouseY)
                || isOtherContentMouseOver(candidate, toolsButton, mouseX, mouseY);
    }

    /**
     * Checks whether a different button's open content is under the cursor.
     *
     * @param candidate The button considering a hover-open.
     * @param other     The other button to inspect.
     * @param mouseX    Mouse x position.
     * @param mouseY    Mouse y position.
     * @return True if the other button's content is under the cursor.
     */
    private static boolean isOtherContentMouseOver(BookLabelButtonWidget candidate, @Nullable BookLabelButtonWidget other,
                                                   double mouseX, double mouseY) {

        return other != null && other != candidate && other.visible && other.isContentMouseOver(mouseX, mouseY);
    }

    /**
     * Returns the markers button label component for the current filter state.
     *
     * @return the markers button label.
     */
    private Component markersButtonText() {

        if (markerFilter.isAllEnabled()) return Component.translatable("ardamaps.client.map.screen.all.markers");
        if (markerFilter.isNoneEnabled()) return Component.translatable("ardamaps.client.map.screen.no.markers");

        return Component.translatable("ardamaps.client.map.screen.some.markers", markerFilter.enabledKeys().size());
    }

    /** Callback interface the map screen must implement to host this control bar. */
    public interface Host {

        /**
         * Registers a widget so it is rendered and receives input.
         *
         * @param widget The widget to register.
         */
        void addControl(net.minecraft.client.gui.components.AbstractWidget widget);

        /**
         * Removes a previously registered widget.
         *
         * @param widget The widget to remove.
         */
        void removeControl(net.minecraft.client.gui.components.AbstractWidget widget);

        /**
         * Returns the padded content area used for layout and popup bounds.
         *
         * @return the padded content area.
         */
        BackgroundRenderer.GuiLayout getPaddedContentArea();

        /**
         * Returns the full screen width.
         *
         * @return the screen width in pixels.
         */
        int getScreenWidth();

        /**
         * Returns {@code true} when a map layer has been loaded and a camera is available.
         *
         * @return whether a camera is present.
         */
        boolean hasCamera();

        /** Called when the layer selection changed — the screen should reset the panel and reload the layer. */
        void onLayerSelectionChanged();

        /** Called when only the range changed — the screen should reload the layer without resetting the panel. */
        void onRangeChanged();

        /** Called when an overlay or navigation action should close the side panel without clearing history. */
        void onCloseSidePanel();

        /** Called when the user clicks the recentre button. */
        void onRecentreClicked();
    }
}
