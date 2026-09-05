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
import com.duom.ardamaps.core.data.guide.GuideScreenLink;
import com.duom.ardamaps.core.KeyBinds;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.screens.rendering.ScreenHintsRenderer;
import com.duom.ardamaps.gui.widgets.SearchWidget;
import com.duom.ardamaps.gui.widgets.TopBookmarkButtonType;
import com.duom.ardamaps.gui.widgets.TopBookmarkButtonWidget;
import com.duom.ardamaps.gui.widgets.builders.TopBookmarkButtonBuilder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Function;

/**
 * The ArdaMapsScreen class serves as a base for all screens in the ArdaMaps mod, providing common functionality such as background rendering and static button management.
 * It extends the Minecraft Screen class and includes methods for initializing the screen, rendering the background, and managing the layout of static buttons like the map button, configuration button, and exit button.
 */
public abstract class ArdaMapsScreen extends Screen {

    /** The vertical offset for static buttons (map, configuration, exit) from the top of the content area. */
    private static final int STATIC_BUTTON_OFFSET_Y = 16;

    /** Source-space icon size for top bookmark tabs. */
    private static final int TOP_BOOKMARK_ICON_SIZE = 48;

    /** The BackgroundRenderer instance responsible for rendering the background of the screen. */
    private final BackgroundRenderer guiBackgroundRenderer;

    /** Cached width of the screen, used to determine when to recalculate layouts. */
    private int cachedWidth;

    /** Cached height of the screen, used to determine when to recalculate layouts. */
    private int cachedHeight;

    /** The content area of the screen, calculated based on the background renderer. */
    private BackgroundRenderer.GuiLayout contentArea;

    /** The padded content area of the screen, which includes additional padding around the content area. */
    private BackgroundRenderer.GuiLayout paddedContentArea;

    /** The map button widget, which opens the MapScreen when clicked. */
    private TopBookmarkButtonWidget mapButton;

    /** The configuration button widget, which opens the ConfigurationScreen when clicked. */
    private TopBookmarkButtonWidget configurationButton;

    /** The guide button widget, which opens the GuideScreen when clicked. */
    private TopBookmarkButtonWidget guideButton;

    /** The exit button widget, which closes the current screen and returns to the parent screen when clicked. */
    private TopBookmarkButtonWidget exitButton;

    /** Search overlay hosted by this screen, or null when search is closed. */
    private @Nullable SearchWidget searchOverlay;

    /**
     * Constructs a new ArdaMapsScreen with the specified parent screen and title.
     *
     * @param ignoredParent The parent screen that opened this screen.
     * @param title         The title of the screen, displayed at the top of the GUI.
     */
    protected ArdaMapsScreen(Screen ignoredParent, Component title) {

        super(title);
        guiBackgroundRenderer = new BackgroundRenderer();
    }

    /**
     * Initializes the screen by setting up the background renderer, configuring static buttons, and managing their visibility based on the current screen type.
     * This method is called when the screen is first opened and whenever the screen is resized.
     */
    @Override
    protected void init() {

        super.init();

        invalidateCachedLayouts();

        if (showsNavigationBookmarks()) {
            configureMapButton();
            configureConfigurationButton();
            configureGuideButton();
        }

        configureExitButton();
        updateStaticButtonsPositions();

        manageBookmarkButtons();
    }

    /**
     * Whether the map, guide and configuration bookmarks should be shown on this screen.
     *
     * @return true when the navigation bookmarks should be created
     */
    protected boolean showsNavigationBookmarks() {

        return true;
    }

    /**
     * Changes the toggle states of the buttons depending on the displayed screen
     */
    private void manageBookmarkButtons() {

        if (this instanceof GuideScreen)
            manageButtonFocus(true, false, false);

        if (this instanceof MapScreen)
            manageButtonFocus(false, true, false);

        if (this instanceof ConfigurationScreen)
            manageButtonFocus(false, false, true);
    }

    /**
     * Sets the correct bookmark button focus depending on the displayed screen
     *
     * @param guide         Focused whether the guide button should be focused
     * @param map           Focused whether the map button should be focused
     * @param configuration Focused whether the configuration button should be focused
     */
    private void manageButtonFocus(boolean guide, boolean map, boolean configuration) {

        if (configurationButton != null) configurationButton.setFocused(configuration);
        if (mapButton != null) mapButton.setFocused(map);
        if (guideButton != null) guideButton.setFocused(guide);
    }

    /**
     * Configures the exit button, which closes the current screen and returns to the parent screen when clicked.
     */
    private void configureExitButton() {

        this.exitButton = TopBookmarkButtonBuilder.create()
                .setButtonStyle(TopBookmarkButtonType.BOOKMARK_CLOSE)
                .setOnClick(this::onExitButtonPressed)
                .setIconSize(TOP_BOOKMARK_ICON_SIZE)
                .setWidth(ModConstants.SQUARED_BUTTON_SIZE)
                .setPosition(0, 0).build();

        this.exitButton.setTooltip(Tooltip.create(Component.translatable("ardamaps.client.map.screen.generic.close")));

        addRenderableWidget(exitButton);
    }

    /**
     * Handles the exit bookmark, closing the ArdaMaps GUI entirely and returning to the game.
     */
    protected void onExitButtonPressed() {

        this.onClose();
        minecraft.setScreen(null);
    }

    /**
     * Configures the map button, which opens the MapScreen when clicked.
     */
    private void configureMapButton() {

        this.mapButton = TopBookmarkButtonBuilder.create()
                .setButtonStyle(TopBookmarkButtonType.BOOKMARK_MAP)
                .setOnClick(() -> {
                    ArdaMapsClient.CONFIG.setLastPage(GuideScreenLink.GUIDE_MAP);
                    minecraft.setScreen(new MapScreen(this));
                })
                .setIconSize(TOP_BOOKMARK_ICON_SIZE)
                .setWidth(ModConstants.SQUARED_BUTTON_SIZE)
                .setPosition(0, 0).build();

        this.mapButton.setTooltip(Tooltip.create(Component.translatable("ardamaps.client.map.screen.map.tooltip")));

        addRenderableWidget(mapButton);

    }

    /**
     * Configures the map button, which opens the MapScreen when clicked.
     */
    private void configureConfigurationButton() {

        this.configurationButton = TopBookmarkButtonBuilder.create()
                .setButtonStyle(TopBookmarkButtonType.BOOKMARK_CONFIGURATION)
                .setOnClick(() -> {
                    ArdaMapsClient.CONFIG.setLastPage(GuideScreenLink.GUIDE_CONFIG);
                    minecraft.setScreen(new ConfigurationScreen(this));
                })
                .setIconSize(TOP_BOOKMARK_ICON_SIZE)
                .setWidth(ModConstants.SQUARED_BUTTON_SIZE)
                .setPosition(0, 0).build();

        this.configurationButton.setTooltip(Tooltip.create(Component.translatable("ardamaps.client.map.screen.configuration.tooltip")));

        addRenderableWidget(configurationButton);
    }

    /**
     * Configures the guide button, opens ArdaCraft in game guide restoring the last sub-page.
     */
    private void configureGuideButton() {

        this.guideButton = TopBookmarkButtonBuilder.create()
                .setButtonStyle(TopBookmarkButtonType.BOOKMARK_GUIDE)
                .setOnClick(() -> minecraft.setScreen(new GuideScreen(this, ArdaMapsClient.CONFIG.getLastPage())))
                .setIconSize(TOP_BOOKMARK_ICON_SIZE)
                .setWidth(ModConstants.SQUARED_BUTTON_SIZE)
                .setPosition(0, 0).build();

        this.guideButton.setTooltip(Tooltip.create(Component.translatable("ardamaps.client.map.screen.guide.tooltip")));

        addRenderableWidget(guideButton);
    }

    /**
     * Called when this screen is closed or replaced by another screen.
     * Persists the client configuration (including the last-page deep-link) to disk.
     */
    @Override
    public void removed() {
        closeSearchOverlay();
        super.removed();
        ArdaMapsClient.CONFIG_MANAGER.save();
    }

    public void closeSearchOverlay() {
        searchOverlay = null;
    }

    /**
     * Renders the background of the screen using the BackgroundRenderer.
     * This method is called every frame to draw the background before any other elements are rendered on top.
     *
     * @param context The DrawContext used for rendering the background.
     */
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        super.extractBackground(context, mouseX, mouseY, delta);
        extractModBackground(context);
    }

    /**
     * Draws only the ArdaMaps panel background, without triggering the vanilla screen background or blur.
     *
     * @param context The DrawContext used for rendering the background.
     */
    public void extractModBackground(GuiGraphicsExtractor context) {
        guiBackgroundRenderer.render(context, width, height);
    }

    /**
     * Returns the padded content area of the screen, which includes additional padding around the content area.
     * This method ensures that the cached layouts are invalidated and recalculated if necessary before returning the padded content area.
     *
     * @return The padded content area of the screen.
     */
    public BackgroundRenderer.GuiLayout getPaddedContentArea() {

        invalidateCachedLayouts();

        return paddedContentArea;
    }

    /**
     * Returns the content area of the screen, which is calculated based on the background renderer.
     * This method ensures that the cached layouts are invalidated and recalculated if necessary before returning the content area.
     *
     * @return The content area of the screen.
     */
    public BackgroundRenderer.GuiLayout getContentArea() {

        invalidateCachedLayouts();

        return contentArea;
    }

    /**
     * Invalidates the cached layouts if the screen dimensions have changed or if the content areas are null.
     * This method checks if the current width and height of the screen differ from the cached values, and if so,
     * it updates the cached values and recalculates the content area and padded content area using the BackgroundRenderer.
     * It also updates the positions of the static buttons to ensure they are correctly aligned with the new layout.
     */
    private void invalidateCachedLayouts() {

        if (contentArea == null || paddedContentArea == null || cachedWidth != width || cachedHeight != height) {

            cachedWidth = width;
            cachedHeight = height;

            guiBackgroundRenderer.invalidate(width, height);

            contentArea = guiBackgroundRenderer.getGuiContentArea();
            paddedContentArea = guiBackgroundRenderer.getGuiContentArea(getContentPadding());

            updateStaticButtonsPositions();
        }
    }

    /**
     * Updates the positions of the static buttons (map button, configuration button, exit button) based on the current
     * content area of the screen. This method calculates the appropriate x and y coordinates for each button to ensure
     * they are aligned with the top right corner of the content area, taking into account the defined vertical offset for static buttons.
     */
    private void updateStaticButtonsPositions() {

        var contentArea = getContentArea();
        var rightX = contentArea.topLeftX() + contentArea.guiWidth();
        var y = contentArea.topLeftY();

        if (exitButton != null)
            this.exitButton.setPosition(rightX - ModConstants.SQUARED_BUTTON_SIZE, y - STATIC_BUTTON_OFFSET_Y);

        if (configurationButton != null)
            this.configurationButton.setPosition(rightX - ModConstants.SQUARED_BUTTON_SIZE * 2, y - STATIC_BUTTON_OFFSET_Y);

        if (guideButton != null)
            this.guideButton.setPosition(contentArea.topLeftX(), y - STATIC_BUTTON_OFFSET_Y);

        if (mapButton != null) {
            var offsetX = guideButton != null ? ModConstants.SQUARED_BUTTON_SIZE : 0;
            this.mapButton.setPosition(contentArea.topLeftX() + offsetX, y - STATIC_BUTTON_OFFSET_Y);
        }
    }

    /**
     * Extracts this screen's render state, then renders the hosted search overlay when present.
     *
     * @param context the draw context
     * @param mouseX  the x position of the mouse cursor
     * @param mouseY  the y position of the mouse cursor
     * @param delta   the time since last frame
     */
    @Override
    public final void extractRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (searchOverlay != null) {
            extractScreenRenderState(context, -1, -1, delta);
            searchOverlay.extractRenderState(context, mouseX, mouseY, delta);
        } else {
            extractScreenRenderState(context, mouseX, mouseY, delta);
            ScreenHintsRenderer.render(context, font, width, height, getScreenHints());
        }
    }

    /**
     * Gets the input hints displayed for this screen.
     *
     * @return The screen input hints.
     */
    protected List<ScreenHintsRenderer.Hint> getScreenHints() {

        return List.of();
    }

    protected void extractScreenRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public final boolean keyPressed(@NonNull KeyEvent event) {
        if (searchOverlay != null) return searchOverlay.keyPressed(event);
        return screenKeyPressed(event);
    }

    protected boolean screenKeyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();

        if (KeyBinds.OPEN_MAP.matches(event) && !isTextInputFocused()) {
            onClose();
            return true;
        }

        // Detect Ctrl+F (or Cmd+F on macOS)
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0 && isSearchable()) {
            openSearchOverlay();
            return true;
        }

        return super.keyPressed(event);
    }

    /**
     * Checks whether keyboard input is currently owned by a text field.
     *
     * @return True when the focused child is a text input.
     */
    private boolean isTextInputFocused() {

        return getFocused() instanceof EditBox;
    }

    /**
     * @return true if this screen is searchable false otherwise
     */
    protected abstract boolean isSearchable();

    private void openSearchOverlay() {
        SearchWidget searchWidget = new SearchWidget(this);
        searchWidget.setSearchFunction(getSearchFunction());
        searchWidget.setResultDisplayFunction(getSearchResultRenderFunction());
        searchWidget.setResultTooltipFunction(getSearchResultTooltipFunction());
        searchWidget.setOnSearchResultSelected(getOnSearcheResultSelectedFunction());

        searchOverlay = searchWidget;
        searchOverlay.init(width, height);
    }

    /**
     * Gets the search function that is called when searching an element on screen via the search widget.
     * This function should search for a String in a List of elements represented on screen
     *
     * @return the search function
     */
    protected abstract Function<String, List<?>> getSearchFunction();

    /**
     * Gets the rendering function of a search result. This function takes an element as an input and returns a
     * displayable string
     *
     * @return the search result rendering function
     */
    protected abstract Function<Object, String> getSearchResultRenderFunction();

    /**
     * Gets an optional tooltip renderer for search results.
     *
     * @return tooltip mapping function, or {@code null} for no tooltip
     */
    protected @Nullable Function<Object, String> getSearchResultTooltipFunction() {
        return null;
    }

    /**
     * Gets the function that is called when a search result is selected via the search widget.
     *
     * @return the function called when a search result is selected
     */
    protected abstract Function<Object, Void> getOnSearcheResultSelectedFunction();

    @Override
    public final boolean keyReleased(@NonNull KeyEvent event) {
        if (searchOverlay != null) return searchOverlay.keyReleased(event);
        return screenKeyReleased(event);
    }

    protected boolean screenKeyReleased(KeyEvent event) {
        return super.keyReleased(event);
    }

    @Override
    public final boolean charTyped(@NonNull CharacterEvent event) {
        if (searchOverlay != null) return searchOverlay.charTyped(event);
        return screenCharTyped(event);
    }

    protected boolean screenCharTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public final boolean preeditUpdated(PreeditEvent event) {
        if (searchOverlay != null) return searchOverlay.preeditUpdated(event);
        return screenPreeditUpdated(event);
    }

    protected boolean screenPreeditUpdated(PreeditEvent event) {
        return super.preeditUpdated(event);
    }

    @Override
    public final boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (searchOverlay != null) return searchOverlay.mouseClicked(event, doubleClick);
        return screenMouseClicked(event, doubleClick);
    }

    protected boolean screenMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public final boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (searchOverlay != null) return searchOverlay.mouseReleased(event);
        return screenMouseReleased(event);
    }

    protected boolean screenMouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event);
    }

    @Override
    public final boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (searchOverlay != null) return searchOverlay.mouseDragged(event, dx, dy);
        return screenMouseDragged(event, dx, dy);
    }

    protected boolean screenMouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (searchOverlay != null) return searchOverlay.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        return screenMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    protected boolean screenMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public final void mouseMoved(double mouseX, double mouseY) {
        if (searchOverlay != null) {
            searchOverlay.mouseMoved(mouseX, mouseY);
        } else {
            screenMouseMoved(mouseX, mouseY);
        }
    }

    protected void screenMouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public final void tick() {
        screenTick();
        if (searchOverlay != null) searchOverlay.tick();
    }

    protected void screenTick() {
        super.tick();
    }

    @Override
    public final void resize(int width, int height) {
        screenResize(width, height);
        if (searchOverlay != null) searchOverlay.init(width, height);
    }

    protected void screenResize(int width, int height) {
        super.resize(width, height);
    }

    /**
     * Returns the amount of padding to apply around the content area when calculating the padded content area.
     * This method can be overridden by subclasses to provide a specific padding value based on the requirements
     * of the individual screen. By default, it returns a fixed padding value defined in the GuiConstants class.
     *
     * @return The amount of padding to apply around the content area.
     */
    public abstract int getContentPadding();
}
