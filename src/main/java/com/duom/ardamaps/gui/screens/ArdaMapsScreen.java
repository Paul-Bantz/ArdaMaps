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
import com.duom.ardamaps.core.data.guide.GuideScreenLink;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.widgets.BookmarkButtonType;
import com.duom.ardamaps.gui.widgets.BookmarkButtonWidget;
import com.duom.ardamaps.gui.widgets.SearchWidget;
import com.duom.ardamaps.gui.widgets.builders.BookmarkButtonBuilder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Function;

/**
 * The ArdaMapsScreen class serves as a base for all screens in the ArdaMaps mod, providing common functionality such as background rendering and static button management.
 * It extends the Minecraft Screen class and includes methods for initializing the screen, rendering the background, and managing the layout of static buttons like the map button, configuration button, and exit button.
 */
public abstract class ArdaMapsScreen extends Screen {

    /** The size of the ArdaCraft logo used in the GUI. */
    protected static final int ARDACRAFT_LOGO_SIZE = 64;

    /** The half size of the ArdaCraft logo, used for centering purposes. */
    protected static final int ARDACRAFT_LOGO_HALF_SIZE = ARDACRAFT_LOGO_SIZE / 2;

    /** The vertical offset for static buttons (map, configuration, exit) from the top of the content area. */
    private static final int STATIC_BUTTON_OFFSET_Y = 12;

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
    private BookmarkButtonWidget mapButton;

    /** The configuration button widget, which opens the ConfigurationScreen when clicked. */
    private BookmarkButtonWidget configurationButton;

    /** The guide button widget, which opens the GuideScreen when clicked. */
    private BookmarkButtonWidget guideButton;

    /** The exit button widget, which closes the current screen and returns to the parent screen when clicked. */
    private BookmarkButtonWidget exitButton;

    /**
     * Constructs a new ArdaMapsScreen with the specified parent screen and title.
     *
     * @param ignoredParent The parent screen that opened this screen.
     * @param title         The title of the screen, displayed at the top of the GUI.
     */
    protected ArdaMapsScreen(Screen ignoredParent, Text title) {

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

        configureMapButton();
        configureConfigurationButton();
        configureGuideButton();
        configureExitButton();
        updateStaticButtonsPositions();

        manageBookmarkButtons();
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

        assert client != null;

        this.exitButton = BookmarkButtonBuilder.create()
                .setButtonStyle(BookmarkButtonType.BOOKMARK_CLOSE)
                .setOnClick(() -> {
                    this.close();
                    client.setScreen(null);
                })
                .setSize(ModConstants.SQUARED_BUTTON_SIZE, ModConstants.SQUARED_BUTTON_SIZE)
                .setPosition(0, 0).build();

        this.exitButton.setTooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.generic.close")));

        addDrawableChild(exitButton);
    }

    /**
     * Configures the map button, which opens the MapScreen when clicked.
     */
    private void configureMapButton() {

        assert client != null;

        this.mapButton = BookmarkButtonBuilder.create()
                .setButtonStyle(BookmarkButtonType.BOOKMARK_MAP)
                .setOnClick(() -> {
                    ArdaMapsClient.CONFIG.setLastPage(GuideScreenLink.GUIDE_MAP);
                    client.setScreen(new MapScreen(this));
                })
                .setSize(ModConstants.SQUARED_BUTTON_SIZE, ModConstants.SQUARED_BUTTON_SIZE)
                .setPosition(0, 0).build();

        this.mapButton.setTooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.map.tooltip")));

        addDrawableChild(mapButton);

    }

    /**
     * Configures the map button, which opens the MapScreen when clicked.
     */
    private void configureConfigurationButton() {

        assert client != null;

        this.configurationButton = BookmarkButtonBuilder.create()
                .setButtonStyle(BookmarkButtonType.BOOKMARK_CONFIGURATION)
                .setOnClick(() -> {
                    ArdaMapsClient.CONFIG.setLastPage(GuideScreenLink.GUIDE_CONFIG);
                    client.setScreen(new ConfigurationScreen(this));
                })
                .setSize(ModConstants.SQUARED_BUTTON_SIZE, ModConstants.SQUARED_BUTTON_SIZE)
                .setPosition(0, 0).build();

        this.configurationButton.setTooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.configuration.tooltip")));

        addDrawableChild(configurationButton);
    }

    /**
     * Configures the guide button, opens ArdaCraft in game guide restoring the last sub-page.
     */
    private void configureGuideButton() {

        assert client != null;

        this.guideButton = BookmarkButtonBuilder.create()
                .setButtonStyle(BookmarkButtonType.BOOKMARK_GUIDE)
                .setOnClick(() -> client.setScreen(new GuideScreen(this, ArdaMapsClient.CONFIG.getLastPage())))
                .setSize(ModConstants.SQUARED_BUTTON_SIZE, ModConstants.SQUARED_BUTTON_SIZE)
                .setPosition(0, 0).build();

        this.guideButton.setTooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.guide.tooltip")));

        addDrawableChild(guideButton);
    }

    /**
     * Called when this screen is closed or replaced by another screen.
     * Persists the client configuration (including the last-page deep-link) to disk.
     */
    @Override
    public void removed() {
        super.removed();
        ArdaMapsClient.CONFIG_MANAGER.save();
    }

    /**
     * Renders the background of the screen using the BackgroundRenderer.
     * This method is called every frame to draw the background before any other elements are rendered on top.
     *
     * @param context The DrawContext used for rendering the background.
     */
    @Override
    public void renderBackground(DrawContext context) {

        super.renderBackground(context);

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
            this.exitButton.setPosition(rightX - ModConstants.BUTTON_HEIGHT, y - STATIC_BUTTON_OFFSET_Y);

        if (configurationButton != null)
            this.configurationButton.setPosition(rightX - ModConstants.BUTTON_HEIGHT * 2, y - STATIC_BUTTON_OFFSET_Y);

        if (guideButton != null)
            this.guideButton.setPosition(contentArea.topLeftX(), y - STATIC_BUTTON_OFFSET_Y);

        if (mapButton != null) {
            var offsetX = guideButton != null ? ModConstants.BUTTON_HEIGHT : 0;
            this.mapButton.setPosition(contentArea.topLeftX() + offsetX, y - STATIC_BUTTON_OFFSET_Y);
        }
    }

    /**
     * Key press handling
     *
     * @param keyCode   the code of the key that was pressed
     * @param scanCode  the scan code of the key that was pressed
     * @param modifiers the modifiers
     * @return true if the event was consumed, false otherwise
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        // Detect Ctrl+F (or Cmd+F on macOS)
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown() && isSearchable()) {

            SearchWidget searchWidget = new SearchWidget(this);
            searchWidget.setSearchFunction(getSearchFunction());
            searchWidget.setResultDisplayFunction(getSearchResultRenderFunction());
            searchWidget.setResultTooltipFunction(getSearchResultTooltipFunction());
            searchWidget.setOnSearchResultSelected(getOnSearcheResultSelectedFunction());

            Client.mc().setScreen(searchWidget);

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Gets the function that is called when a search result is selected via the search widget.
     *
     * @return the function called when a search result is selected
     */
    protected abstract Function<Object, Void> getOnSearcheResultSelectedFunction();

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
     * @return true if this screen is searchable false otherwise
     */
    protected abstract boolean isSearchable();

    /**
     * Returns the amount of padding to apply around the content area when calculating the padded content area.
     * This method can be overridden by subclasses to provide a specific padding value based on the requirements
     * of the individual screen. By default, it returns a fixed padding value defined in the GuiConstants class.
     *
     * @return The amount of padding to apply around the content area.
     */
    public abstract int getContentPadding();
}