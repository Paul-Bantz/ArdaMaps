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
import com.duom.ardamaps.core.data.UnitSystem;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.widgets.CheckboxWidget;
import com.duom.ardamaps.gui.widgets.DropdownWidget;
import com.duom.ardamaps.gui.widgets.StyledButtonWidget;
import com.duom.ardamaps.gui.widgets.TextIdentifierPairItem;
import com.duom.ardamaps.gui.widgets.builders.CheckboxBuilder;
import com.duom.ardamaps.gui.widgets.builders.DropdownBuilder;
import com.duom.ardamaps.gui.widgets.builders.StyledButtonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The configuration screen for the map, containing settings related to map display and behaviour, toposcope rendering
 * and compass rendering.
 */
public class ConfigurationScreen extends ArdaMapsScreen {

    /** Lower bound for render distance sliders when the unit system is metric (in kilometers). */
    public static final int METRIC_LOW_BOUND = 10;

    /** Lower bound for render distance sliders when the unit system is imperial (in miles). */
    public static final int IMPERIAL_LOW_BOUND = 6;

    /** Upper bound for render distance sliders when the unit system is metric (in kilometers). */
    public static final int METRIC_HIGH_BOUND = 100;

    /** Upper bound for render distance sliders when the unit system is imperial (in miles). */
    public static final int IMPERIAL_HIGH_BOUND = 62;

    /** Standard button height. */
    private static final int BUTTON_HEIGHT = 20;

    /** Standard button width. */
    private static final int BUTTON_WIDTH = 120;

    /** Confirmation dialog text for resetting exploration progress. */
    private final Text confirmationResetExplorationDialogText = Text.translatable("ardamaps.client.map.screen.configuration.reset_exploration.confirm");

    /** Confirmation dialog text for revealing all. */
    private final Text confirmationRevealAllDialogText = Text.translatable("ardamaps.client.map.screen.configuration.reveal_all.confirm");

    /** Title for the options section. */
    private final Text titleOptions = Text.translatable("ardamaps.client.map.screen.options");

    private final Text titleMapOptions = Text.translatable("ardamaps.client.map.screen.configuration.title.map");

    private final Text revealAllOptionLabel = Text.translatable("ardamaps.client.map.screen.configuration.reveal_all");

    private final Text resetExplorationProgressLabel = Text.translatable("ardamaps.client.map.screen.configuration.exploration");

    private final Text configDirectoryLabel = Text.translatable("ardamaps.client.map.screen.configuration.directory");

    private final Text unitSystemLabel = Text.translatable("ardamaps.client.map.screen.configuration.unit.system");

    private final Text titleCompassOptions = Text.translatable("ardamaps.client.map.screen.configuration.title.compass");

    private final Text compassOpacityLabel = Text.translatable("ardamaps.client.map.screen.configuration.compass.opacity");

    private final Text compassPoiLabel = Text.translatable("ardamaps.client.map.screen.configuration.compass.poi.distance");

    private final Text titleToposcopeOptions = Text.translatable("ardamaps.client.map.screen.configuration.title.toposcope");

    private final Text toposcopePoiLabel = Text.translatable("ardamaps.client.map.screen.configuration.toposcope.poi.distance");

    /** Margins for the left page. */
    private final Margins leftPageMargins = new Margins(10, 32, 10, 10);

    /** Margins for the right page. */
    private final Margins rightPageMargins = new Margins(32, 10, 10, 10);

    /** State for the display of the reveal all confirmation dialog */
    private boolean displayRevealAllConfirmationDialog = false;

    /** State for the display of the reset exploration confirmation dialog */
    private boolean displayResetProgressConfirmationDialog = false;

    /** Slider for the toposcope render distance. */
    private SliderWidget toposcopeRenderDistanceSlider;

    /** Slider for the compass render distance. */
    private SliderWidget compassRenderDistanceSlider;

    /** Slider for the compass opacity. */
    private SliderWidget compassOpacitySlider;

    /** Checkbox for the reveal all option. */
    private CheckboxWidget revealAllCheckbox;

    /** Open Config directory button */
    private ButtonWidget configDirectoryButton;

    /** Button to reset exploration progress. */
    private ButtonWidget resetExplorationButton;

    /** Confirmation button for resetting exploration progress. */
    private ButtonWidget confirmResetExplorationButton;

    /** Cancellation button for resetting exploration progress. */
    private ButtonWidget cancelResetExplorationButton;

    /** Confirmation button for revealing all. */
    private ButtonWidget confirmRevealAllButton;

    /** Cancellation button for revealing all. */
    private ButtonWidget cancelRevealAllButton;

    /** Toggle button for the map options section. */
    private StyledButtonWidget mapOptionsToggleButton;

    /** Toggle button for the compass options section. */
    private StyledButtonWidget compassOptionsToggleButton;

    /** Toggle button for the toposcope options section. */
    private StyledButtonWidget toposcopeOptionsToggleButton;

    /** Dropdown for the unit system selection. */
    private DropdownWidget<UnitSystem, TextIdentifierPairItem> unitSystemDropdown;

    /** Enum to represent the currently toggled section in the configuration screen. */
    private ToggledSectionEnum toggledSection = ToggledSectionEnum.MAP_OPTIONS;

    /**
     * Constructor for the configuration screen.
     *
     * @param parent the parent screen, to return to when closing the configuration screen
     */
    public ConfigurationScreen(Screen parent) {

        super(parent, Text.translatable("ardamaps.client.map.screen.configuration"));
    }

    /**
     * Initialize the map screen
     */
    @Override
    protected void init() {

        super.init();

        assert client != null;

        configureUnitSystemDropdown();
        configureCompassOpacitySlider();
        configureExplorationToggle();
        configureOpenConfigurationDirectoryButton();
        configureResetExplorationData();
        configureCompassRenderDistanceSlider();
        configureToposcopeRenderDistanceSlider();
        configureToggleButtons();

        int dialogBtnWidth = Math.max(80, this.width / 8);

        confirmResetExplorationButton = ButtonWidget.builder(
                        Text.translatable("ardamaps.generic.yes"),
                        button -> {

                            // Clear all per-dimension exploration data and re-initialise instances.
                            ArdaMapsClient.CONFIG.getClientProgress().reset(false);
                            ArdaMapsClient.CONFIG_MANAGER.saveProgress();

                            displayResetProgressConfirmationDialog = false;
                        })
                .size(dialogBtnWidth, BUTTON_HEIGHT)
                .build();

        cancelResetExplorationButton = ButtonWidget.builder(
                        Text.translatable("ardamaps.generic.cancel"),
                        button -> displayResetProgressConfirmationDialog = false)
                .size(dialogBtnWidth, BUTTON_HEIGHT)
                .build();

        confirmRevealAllButton = ButtonWidget.builder(
                        Text.translatable("ardamaps.generic.yes"),
                        button -> {
                            ArdaMapsClient.CONFIG.setMapRevealAll(true);
                            ArdaMapsClient.CONFIG_MANAGER.save();
                            displayRevealAllConfirmationDialog = false;
                        })
                .size(dialogBtnWidth, BUTTON_HEIGHT)
                .build();

        cancelRevealAllButton = ButtonWidget.builder(
                        Text.translatable("ardamaps.generic.cancel"),
                        button -> {
                            revealAllCheckbox.setChecked(false);
                            displayRevealAllConfirmationDialog = false;
                        })
                .size(dialogBtnWidth, BUTTON_HEIGHT)
                .build();
    }

    /**
     * Get the search function that is called when searching an element on screen via the search widget.
     * This function should search for a String in a List of elements represented on screen
     *
     * @return  the search function
     */
    @Override
    protected @Nullable Function<String, List<?>> getSearchFunction() {
        /* Configuration Screen does not support searching */
        return null;
    }

    /**
     * Gets the rendering function of a search result. This function takes an element as an input and returns a
     * displayable string
     *
     * @return the search result rendering function
     */
    @Override
    protected @Nullable Function<Object, String> getSearchResultRenderFunction() {
        /* Configuration Screen does not support searching */
        return null;
    }

    /**
     * Gets the function that is called when a search result is selected via the search widget.
     *
     * @return the function called when a search result is selected
     */
    @Override
    protected Function<Object, Void> getOnSearcheResultSelectedFunction() {
        /* Configuration Screen does not support searching */
        return null;
    }

    /**
     * @return true - this screen can be searched
     */
    @Override
    protected boolean isSearchable() {
        return false;
    }

    /**
     * Check if a confirmation dialog is currently being displayed, either for revealing all or for resetting exploration progress.
     */
    private void configureUnitSystemDropdown() {

        assert client != null;

        unitSystemDropdown = DropdownBuilder.<UnitSystem, TextIdentifierPairItem>create()
                .setSize(BUTTON_WIDTH, BUTTON_HEIGHT)
                .setOptions(Arrays.asList(UnitSystem.values()))
                .setAllowNull(false)
                .setOptionDisplay((unitSystem) -> new TextIdentifierPairItem(unitSystem.getDisplayName(), null))
                .setSelected(ArdaMapsClient.CONFIG.getUnitSystem())
                .setOnSelect((unitSystem) -> {
                    ArdaMapsClient.CONFIG.setUnitSystem(unitSystem);
                    ArdaMapsClient.CONFIG_MANAGER.save();
                    configureCompassRenderDistanceSlider();
                    configureToposcopeRenderDistanceSlider();
                })
                .build();
    }

    /**
     * Configure the slider for the compass opacity option, including its initial value, its display message and its onValueChanged behaviour to update the configuration.
     */
    private void configureCompassOpacitySlider() {

        assert client != null;

        var currentOpacity = ArdaMapsClient.CONFIG.getCompassOpacity();
        compassOpacitySlider = new SliderWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, ScreenTexts.EMPTY, currentOpacity) {
            {
                this.updateMessage();
            }

            @Override
            public void setFocused(boolean focused) {
                updateMessage();
                super.setFocused(focused);
            }

            @Override
            protected void updateMessage() {
                var value = (int) Math.round(this.value * 100);
                this.setMessage(Text.literal(String.format("%s%%", value)));
            }

            @Override
            protected void applyValue() {
                ArdaMapsClient.CONFIG.setCompassOpacity((float) value);
                ArdaMapsClient.CONFIG_MANAGER.save();
            }
        };
    }

    /**
     * Configure the toggle button for the "reveal all" option, including its initial state according to the configuration and its onChange behaviour to update the configuration and display the confirmation dialog when toggled on.
     */
    private void configureExplorationToggle() {

        assert client != null;

        revealAllCheckbox = CheckboxBuilder.create()
                .setSize(BUTTON_WIDTH, BUTTON_HEIGHT)
                .setChecked(ArdaMapsClient.CONFIG.isMapRevealAll())
                .setOnChange((checked) -> {
                    if (!checked) {
                        ArdaMapsClient.CONFIG.setMapRevealAll(false);
                        ArdaMapsClient.CONFIG_MANAGER.save();
                    }
                    displayRevealAllConfirmationDialog = checked;
                })
                .build();

        revealAllCheckbox.setTooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.configuration.reveal_all.tooltip")));
    }

    /**
     * Configure the open config directory button
     */
    private void configureOpenConfigurationDirectoryButton() {

        assert client != null;

        configDirectoryButton = ButtonWidget.builder(
                        Text.translatable("ardamaps.client.generic.open"),
                        button -> Util.getOperatingSystem().open(FabricLoader.getInstance().getConfigDir().resolve("arda-maps").toFile()))
                .width(BUTTON_WIDTH)
                .tooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.configuration.open_config_directory.tooltip")))
                .build();
    }

    /**
     * Configure the button for resetting exploration progress, including its onClick behaviour to display the confirmation dialog when clicked.
     */
    private void configureResetExplorationData() {

        assert client != null;

        resetExplorationButton = ButtonWidget.builder(
                        Text.translatable("ardamaps.client.map.screen.configuration.reset_exploration"),
                        button -> displayResetProgressConfirmationDialog = true)
                .width(BUTTON_WIDTH)
                .tooltip(Tooltip.of(Text.translatable("ardamaps.client.map.screen.configuration.reset_exploration.tooltip")))
                .build();
    }

    /**
     * Configure the slider for the compass render distance option, including its initial value, its display message and its onValueChanged behaviour to update the configuration.
     */
    private void configureCompassRenderDistanceSlider() {

        var isMetric = ArdaMapsClient.CONFIG.getUnitSystem() == UnitSystem.METRIC;
        var minValue = isMetric ? 10 : 6;
        var maxValue = isMetric ? 100 : 62;
        var currentDrawDistance = ArdaMapsClient.CONFIG.getCompassDrawDistance();

        compassRenderDistanceSlider = configureGenericRenderDistanceSlider(
                (update) -> {
                    var slider = update.getRight();
                    var value = (int) Math.round(minValue + update.getLeft() * (maxValue - minValue));
                    if (isMetric)
                        slider.setMessage(Text.translatable("ardamaps.client.map.screen.configuration.poi.draw.distance.meters", value));
                    else
                        slider.setMessage(Text.translatable("ardamaps.client.map.screen.configuration.poi.draw.distance.miles", value));
                },
                (update) -> {
                    var value = (int) Math.round(minValue + update.getLeft() * (maxValue - minValue));
                    ArdaMapsClient.CONFIG.setCompassDrawDistance(value);
                    ArdaMapsClient.CONFIG_MANAGER.save();
                }, currentDrawDistance, minValue, maxValue);
    }

    /**
     * Configure the slider for the toposcope render distance option, including its initial value, its display message and its onValueChanged behaviour to update the configuration.
     */
    private void configureToposcopeRenderDistanceSlider() {

        var isMetric = ArdaMapsClient.CONFIG.getUnitSystem() == UnitSystem.METRIC;
        var minValue = isMetric ? METRIC_LOW_BOUND : IMPERIAL_LOW_BOUND;
        var maxValue = isMetric ? METRIC_HIGH_BOUND : IMPERIAL_HIGH_BOUND;
        var currentDrawDistance = ArdaMapsClient.CONFIG.getToposcopeDrawDistance();

        toposcopeRenderDistanceSlider = configureGenericRenderDistanceSlider(
                (update) -> {
                    var slider = update.getRight();
                    var value = (int) Math.round(minValue + update.getLeft() * (maxValue - minValue));
                    if (isMetric)
                        slider.setMessage(Text.translatable("ardamaps.client.map.screen.configuration.poi.draw.distance.meters", value));
                    else
                        slider.setMessage(Text.translatable("ardamaps.client.map.screen.configuration.poi.draw.distance.miles", value));
                },
                (update) -> {
                    var value = (int) Math.round(minValue + update.getLeft() * (maxValue - minValue));
                    ArdaMapsClient.CONFIG.setToposcopeDrawDistance(value);
                    ArdaMapsClient.CONFIG_MANAGER.save();
                }, currentDrawDistance, minValue, maxValue);
    }

    /**
     * Configure the toggle buttons for the different sections of the configuration screen,
     * and their onClick behaviour to set the toggled section and update the toggle states of the buttons.
     */
    private void configureToggleButtons() {

        mapOptionsToggleButton = StyledButtonBuilder.create()
                .setText(titleMapOptions)
                .setSize(90, 32)
                .setOnClick(() -> {
                    toggledSection = ToggledSectionEnum.MAP_OPTIONS;
                    mapOptionsToggleButton.setToggled(true);
                    compassOptionsToggleButton.setToggled(false);
                    toposcopeOptionsToggleButton.setToggled(false);
                })
                .build();
        mapOptionsToggleButton.setToggled(true);

        compassOptionsToggleButton = StyledButtonBuilder.create()
                .setText(titleCompassOptions)
                .setSize(90, 32)
                .setOnClick(() -> {
                    toggledSection = ToggledSectionEnum.COMPASS_OPTIONS;
                    mapOptionsToggleButton.setToggled(false);
                    compassOptionsToggleButton.setToggled(true);
                    toposcopeOptionsToggleButton.setToggled(false);
                })
                .build();

        toposcopeOptionsToggleButton = StyledButtonBuilder.create()
                .setText(titleToposcopeOptions)
                .setSize(90, 32)
                .setOnClick(() -> {
                    toggledSection = ToggledSectionEnum.TOPOSCOPE_OPTIONS;
                    mapOptionsToggleButton.setToggled(false);
                    compassOptionsToggleButton.setToggled(false);
                    toposcopeOptionsToggleButton.setToggled(true);
                })
                .build();
    }

    /**
     * Helper method to configure a render distance slider, used for both the compass and toposcope render distance sliders, to avoid code duplication.
     *
     * @param onUpdateMessage     a consumer that takes the slider value and the slider widget, to update the display message of the slider according to the current unit system
     * @param onUpdateValue       a consumer that takes the slider value and the slider widget, to update the configuration with the new render distance value when the slider value is changed
     * @param currentDrawDistance the current draw distance value in real world units, to set the initial value of the slider
     * @return the configured slider widget
     */
    private SliderWidget configureGenericRenderDistanceSlider(Consumer<Pair<Double, SliderWidget>> onUpdateMessage,
                                                              Consumer<Pair<Double, SliderWidget>> onUpdateValue,
                                                              double currentDrawDistance,
                                                              int minValue,
                                                              int maxValue) {
        assert client != null;

        var baseSliderValue = MathHelper.clamp((currentDrawDistance - minValue) / (maxValue - minValue), 0, 1);

        return new SliderWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, ScreenTexts.EMPTY, baseSliderValue) {
            {
                this.updateMessage();
            }

            @Override
            public void setFocused(boolean focused) {
                updateMessage();
                super.setFocused(focused);
            }

            @Override
            protected void updateMessage() {
                onUpdateMessage.accept(new Pair<>(this.value, this));
            }

            @Override
            protected void applyValue() {
                onUpdateValue.accept(new Pair<>(this.value, this));
            }
        };
    }

    /**
     * Render the configuration screen, including the background, the configuration UI and the confirmation dialog if needed.
     *
     * @param context the draw context
     * @param mouseX  the x position of the mouse cursor
     * @param mouseY  the y position of the mouse cursor
     * @param delta   the time delta since the last render call
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        renderBackground(context);

        renderConfigurationUi(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        renderConfirmationDialog(context, mouseX, mouseY, delta);
    }

    /**
     * Render the configuration UI, including the section titles, the toggle buttons and the options for the currently toggled section.
     *
     * @param context the draw context
     * @param mouseX  the x position of the mouse cursor
     * @param mouseY  the y position of the mouse cursor
     * @param delta   the time delta since the last render call
     */
    private void renderConfigurationUi(DrawContext context, int mouseX, int mouseY, float delta) {

        var contentArea = getPaddedContentArea();
        var leftColX = contentArea.topLeftX() + leftPageMargins.left;
        var rightColumnX = contentArea.topLeftX() + (contentArea.guiWidth() / 2) + rightPageMargins.left;
        var pageWidth = (contentArea.guiWidth() / 2) - (leftPageMargins.left + leftPageMargins.right);

        int leftPageY = contentArea.topLeftY() + leftPageMargins.top;
        int rightPageY = contentArea.topLeftY() + rightPageMargins.top;

        leftPageY = renderSectionTitle(context, leftColX, leftPageY, pageWidth, titleOptions) + ModConstants.ROW_SPACING;

        leftPageY += ScreenRenderingUtils.renderSeparator(context, pageWidth, leftColX, leftPageY) + ModConstants.ROW_SPACING * 2;

        leftPageY = renderRow(context, leftColX, leftPageY, pageWidth, mapOptionsToggleButton, mouseX, mouseY, delta) + ModConstants.ROW_SPACING / 3;
        leftPageY = renderRow(context, leftColX, leftPageY, pageWidth, compassOptionsToggleButton, mouseX, mouseY, delta) + ModConstants.ROW_SPACING / 3;
        renderRow(context, leftColX, leftPageY, pageWidth, toposcopeOptionsToggleButton, mouseX, mouseY, delta);

        switch (toggledSection) {
            case MAP_OPTIONS -> {

                rightPageY = renderSectionTitle(context, rightColumnX, rightPageY, pageWidth, titleMapOptions) + ModConstants.ROW_SPACING;
                rightPageY += ScreenRenderingUtils.renderSeparator(context, pageWidth, rightColumnX, rightPageY) + ModConstants.ROW_SPACING * 2;
                renderMapOptions(context, rightColumnX, rightPageY, pageWidth, mouseX, mouseY, delta);
            }
            case COMPASS_OPTIONS -> {

                rightPageY = renderSectionTitle(context, rightColumnX, rightPageY, pageWidth, titleCompassOptions) + ModConstants.ROW_SPACING;
                rightPageY += ScreenRenderingUtils.renderSeparator(context, pageWidth, rightColumnX, rightPageY) + ModConstants.ROW_SPACING * 2;
                renderCompassOptions(context, rightColumnX, rightPageY, pageWidth, mouseX, mouseY, delta);
            }
            case TOPOSCOPE_OPTIONS -> {
                rightPageY = renderSectionTitle(context, rightColumnX, rightPageY, pageWidth, titleToposcopeOptions) + ModConstants.ROW_SPACING;
                rightPageY += ScreenRenderingUtils.renderSeparator(context, pageWidth, rightColumnX, rightPageY) + ModConstants.ROW_SPACING * 2;
                renderToposcopeOptions(context, rightColumnX, rightPageY, pageWidth, mouseX, mouseY, delta);
            }
        }
    }

    /**
     * Render the options for the map section, including the unit system dropdown, the reveal all checkbox and the reset exploration button.
     *
     * @param context   the draw context
     * @param x         the x position of the left edge of the options area
     * @param y         the y position of the top edge of the options area
     * @param pageWidth the width of the options area
     * @param mouseX    the x position of the mouse cursor
     * @param mouseY    the y position of the mouse cursor
     * @param delta     the time delta since the last render call
     */
    private void renderMapOptions(DrawContext context, int x, int y, int pageWidth, int mouseX, int mouseY, float delta) {

        y = renderRow(context, x, y, pageWidth, unitSystemLabel,
                unitSystemDropdown, mouseX, mouseY, delta) + ModConstants.ROW_SPACING;

        y = renderRow(context, x, y, pageWidth, revealAllOptionLabel,
                revealAllCheckbox, mouseX, mouseY, delta) + ModConstants.ROW_SPACING;

        y = renderRow(context, x, y, pageWidth, resetExplorationProgressLabel,
                resetExplorationButton, mouseX, mouseY, delta) + ModConstants.ROW_SPACING;

        renderRow(context, x, y, pageWidth, configDirectoryLabel,
                configDirectoryButton, mouseX, mouseY, delta);
    }

    /**
     * Render the options for the toposcope section, including the toposcope render distance slider.
     *
     * @param context   the draw context
     * @param x         the x position of the left edge of the options area
     * @param y         the y position of the top edge of the options area
     * @param pageWidth the width of the options area
     * @param mouseX    the x position of the mouse cursor
     * @param mouseY    the y position of the mouse cursor
     * @param delta     the time delta since the last render call
     */
    private void renderToposcopeOptions(DrawContext context, int x, int y, int pageWidth, int mouseX, int mouseY, float delta) {

        renderRow(context, x, y, pageWidth, toposcopePoiLabel,
                toposcopeRenderDistanceSlider, mouseX, mouseY, delta);
    }

    /**
     * Render the options for the compass section, including the compass render distance slider and the compass opacity slider.
     *
     * @param context   the draw context
     * @param x         the x position of the left edge of the options area
     * @param y         the y position of the top edge of the options area
     * @param pageWidth the width of the options area
     * @param mouseX    the x position of the mouse cursor
     * @param mouseY    the y position of the mouse cursor
     * @param delta     the time delta since the last render call
     */
    private void renderCompassOptions(DrawContext context, int x, int y, int pageWidth, int mouseX, int mouseY, float delta) {

        y = renderRow(context, x, y, pageWidth, compassOpacityLabel,
                compassOpacitySlider, mouseX, mouseY, delta) + ModConstants.ROW_SPACING;

        renderRow(context, x, y, pageWidth, compassPoiLabel,
                compassRenderDistanceSlider, mouseX, mouseY, delta);

    }

    /**
     * Render a single row in the configuration screen, consisting of a label and a widget, with the widget vertically centred with the label.
     *
     * @param context the draw context
     * @param x       the x position of the left edge of the row
     * @param y       the y position of the top edge of the row
     * @param widget  the widget to display on the right side of the row
     * @param mouseX  the x position of the mouse cursor
     * @param mouseY  the y position of the mouse cursor
     * @param delta   the time delta since the last render call
     * @return the y position of the bottom edge of the row, to be used for rendering subsequent rows
     */
    private int renderRow(DrawContext context, int x, int y, int pageWidth, ClickableWidget widget, int mouseX, int mouseY, float delta) {

        var xPos = x + pageWidth / 2 - widget.getWidth() / 2;
        var yPos = y - widget.getHeight() / 2;

        widget.setX(xPos);
        widget.setY(yPos);

        var mousePosX = mouseX;
        var mousePosY = mouseY;

        if (isDisplayingConfirmationDialog()) {
            mousePosX = -1;
            mousePosY = -1;
        }

        widget.render(context, mousePosX, mousePosY, delta);

        return y + widget.getHeight();
    }

    /**
     * Overloaded method to render a row with a label on the left and a widget on the right, with the widget vertically centred with the label.
     *
     * @param context the draw context
     * @param x       the x position of the left edge of the row
     * @param y       the y position of the top edge of the row
     * @param width   the width of the row
     * @param label   the label to display on the left side of the row
     * @param widget  the widget to display on the right side of the row
     * @param mouseX  the x position of the mouse cursor
     * @param mouseY  the y position of the mouse cursor
     * @param delta   the time delta since the last render call
     * @return the y position of the bottom edge of the row, to be used for rendering subsequent rows
     */
    private int renderRow(DrawContext context, int x, int y, int width,
                          Text label, ClickableWidget widget, int mouseX, int mouseY, float delta) {

        var halfPageWidth = width / 2;

        widget.setWidth(Math.min(halfPageWidth, widget.getWidth()));

        int lineH = textRenderer.fontHeight / 2;

        int rightX = x + halfPageWidth;
        int labelY = y - lineH;

        context.drawText(textRenderer, label, x, labelY, ModConstants.COLOR_DARK_BROWN, false);

        // Widget - vertically centred in the row
        int widgetYPosition = y - BUTTON_HEIGHT / 2;
        widget.setX(rightX);
        widget.setY(widgetYPosition);

        var mousePosX = mouseX;
        var mousePosY = mouseY;

        // Workaround to prevent hover state from being triggered when hovering the dropdown list,
        // hovered state is set in ClickableWidget#render according to mouse position.
        boolean dropdownHovered = !widget.equals(unitSystemDropdown) && unitSystemDropdown.isExpanded() && unitSystemDropdown.isHovered();

        if (dropdownHovered || isDisplayingConfirmationDialog()) {
            mousePosX = -1;
            mousePosY = -1;
        }

        widget.render(context, mousePosX, mousePosY, delta);

        return widgetYPosition + BUTTON_HEIGHT;
    }

    /**
     * Render a section title, centred horizontally in the page and with a larger font size than the rest of the text.
     *
     * @param context   the draw context
     * @param x         the x position of the left edge of the page
     * @param y         the y position of the top edge of the page
     * @param pageWidth the width of the page
     * @param title     the title text to render
     * @return the y position of the bottom edge of the title, to be used for rendering subsequent elements in the section
     */
    private int renderSectionTitle(DrawContext context, int x, int y, int pageWidth, Text title) {

        float scale = 1.4f;
        int textW = textRenderer.getWidth(title);
        int xOffset = (int) (pageWidth / 2f - (textW * scale / 2f));

        context.getMatrices().push();
        context.getMatrices().translate(x + xOffset, y, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.drawText(textRenderer, title, 0, 0, ModConstants.COLOR_DARK_BROWN, false);
        context.getMatrices().pop();

        return (int) (y + textRenderer.fontHeight * scale);
    }

    /**
     * Render the confirmation dialog for revealing all or resetting exploration progress, with a semi-transparent background and a paper texture for the dialog.
     * The dialog contains the confirmation text and two buttons for confirming or cancelling the action.
     *
     * @param context the draw context
     * @param mouseX  the x position of the mouse cursor
     * @param mouseY  the y position of the mouse cursor
     * @param delta   the time delta since the last render call
     */
    private void renderConfirmationDialog(DrawContext context, int mouseX, int mouseY, float delta) {

        if (!isDisplayingConfirmationDialog()) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);

        context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);

        var dialogWidth = this.width / 3;
        var dialogHeight = this.height / 3;

        var dialogX = (this.width - dialogWidth) / 2;
        var dialogY = (this.height - dialogHeight) / 2;
        var dialogPadding = (int) (dialogWidth * .1);

        context.drawNineSlicedTexture(ModConstants.PAPER_TEXTURE,
                dialogX, dialogY,
                dialogWidth, dialogHeight,
                64,
                64,
                64,
                64,
                256,
                256,
                0, 0);

        var text = displayResetProgressConfirmationDialog ? confirmationResetExplorationDialogText : confirmationRevealAllDialogText;
        var okButton = displayResetProgressConfirmationDialog ? confirmResetExplorationButton : confirmRevealAllButton;
        var cancelBtn = displayResetProgressConfirmationDialog ? cancelResetExplorationButton : cancelRevealAllButton;

        int x = dialogX + dialogPadding;
        int y = dialogY + dialogPadding;

        List<OrderedText> multilinePrompt = textRenderer.wrapLines(text, dialogWidth - dialogPadding * 2);
        var lineHeight = textRenderer.fontHeight;

        for (OrderedText line : multilinePrompt) {

            context.drawText(textRenderer, line, x, y, ModConstants.COLOR_DARK_BROWN, false);
            y += lineHeight;
        }

        var buttonWidth = (dialogWidth - 2 * dialogPadding - 8) / 2;

        okButton.setPosition(x, dialogY + dialogHeight - dialogPadding - BUTTON_HEIGHT);
        okButton.setWidth(buttonWidth);
        okButton.render(context, mouseX, mouseY, delta);

        cancelBtn.setPosition(x + buttonWidth + 8, dialogY + dialogHeight - dialogPadding - BUTTON_HEIGHT);
        cancelBtn.setWidth(buttonWidth);
        cancelBtn.render(context, mouseX, mouseY, delta);

        context.getMatrices().pop();
    }

    /**
     * Handle mouse clicks for the configuration screen, including clicks on the options widgets and the confirmation dialog buttons.
     * When a confirmation dialog is displayed, only the buttons in the dialog are clickable.
     *
     * @param mouseX the x position of the mouse cursor
     * @param mouseY the y position of the mouse cursor
     * @param button the mouse button that was clicked
     * @return true if the click was handled by the configuration screen, false otherwise
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (isDisplayingConfirmationDialog()) {

            confirmResetExplorationButton.mouseClicked(mouseX, mouseY, button);
            confirmRevealAllButton.mouseClicked(mouseX, mouseY, button);

            cancelResetExplorationButton.mouseClicked(mouseX, mouseY, button);
            cancelRevealAllButton.mouseClicked(mouseX, mouseY, button);

            return true;
        }

        var consumed = false;

        if (mapOptionsToggleButton.isToggled()) {

            consumed |= unitSystemDropdown.mouseClicked(mouseX, mouseY, button);

            if (!consumed) {

                consumed = revealAllCheckbox.mouseClicked(mouseX, mouseY, button);
                consumed |= resetExplorationButton.mouseClicked(mouseX, mouseY, button);
                consumed |= configDirectoryButton.mouseClicked(mouseX, mouseY, button);
            }

        } else if (compassOptionsToggleButton.isToggled()) {

            consumed |= compassRenderDistanceSlider.mouseClicked(mouseX, mouseY, button);
            consumed |= compassOpacitySlider.mouseClicked(mouseX, mouseY, button);

        } else if (toposcopeOptionsToggleButton.isToggled()) {

            consumed |= toposcopeRenderDistanceSlider.mouseClicked(mouseX, mouseY, button);

        }

        consumed |= mapOptionsToggleButton.mouseClicked(mouseX, mouseY, button);
        consumed |= compassOptionsToggleButton.mouseClicked(mouseX, mouseY, button);
        consumed |= toposcopeOptionsToggleButton.mouseClicked(mouseX, mouseY, button);

        return consumed || super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Check if either the reveal all confirmation dialog or the reset exploration confirmation dialog is currently being displayed.
     *
     * @return true if a confirmation dialog is being displayed, false otherwise
     */
    private boolean isDisplayingConfirmationDialog() {
        return displayRevealAllConfirmationDialog || displayResetProgressConfirmationDialog;
    }

    /**
     * Handle mouse drags for the configuration screen, including drags on the sliders in the options sections.
     * When a confirmation dialog is displayed, dragging is disabled for all widgets.
     *
     * @param mouseX the x position of the mouse cursor
     * @param mouseY the y position of the mouse cursor
     * @param button the mouse button that is being dragged
     * @param deltaX the change in x position since the last call
     * @param deltaY the change in y position since the last call
     * @return true if the drag was handled by the configuration screen, false otherwise
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {

        if (isDisplayingConfirmationDialog()) return true;

        var consumed = false;

        if (compassOptionsToggleButton.isToggled()) {

            if (compassRenderDistanceSlider.isMouseOver(mouseX, mouseY))
                consumed |= compassRenderDistanceSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

            if (compassOpacitySlider.isMouseOver(mouseX, mouseY))
                consumed |= compassOpacitySlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        } else if (toposcopeOptionsToggleButton.isToggled()) {

            if (toposcopeRenderDistanceSlider.isMouseOver(mouseX, mouseY))
                consumed |= toposcopeRenderDistanceSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        return consumed || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /**
     * Save the configuration when closing the screen, to ensure that any changes made by the user are persisted.
     */
    @Override
    public void close() {
        ArdaMapsClient.CONFIG_MANAGER.save();
        super.close();
    }

    /**
     * Override the getContentPadding method to return 0, as padding is handled manually in the rendering of the configuration UI to allow for different padding values for the left and right pages.
     *
     * @return 0, as padding is handled manually in the rendering of the configuration UI
     */
    @Override
    public int getContentPadding() {

        return 0;
    }

    /** Enum to represent the currently toggled section in the configuration screen. */
    private enum ToggledSectionEnum {
        MAP_OPTIONS,
        COMPASS_OPTIONS,
        TOPOSCOPE_OPTIONS
    }

    /**
     * Simple record to hold margin values for the left and right pages of the configuration screen.
     *
     * @param left   left margin
     * @param right  right margin
     * @param top    top margin
     * @param bottom bottom margin
     */
    @SuppressWarnings("SameParameterValue")
    private record Margins(int left, int right, int top, int bottom) {
    }
}

