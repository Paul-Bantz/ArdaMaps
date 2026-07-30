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

package com.duom.ardamaps.gui.widgets;

import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.ArdaMapsScreen;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Function;

/**
 * Implementation for a Search Screen Widget. Displays an input box and a live search is performed.
 * This widget is configured with :
 * <ul>
 *     <li>A search function {@link SearchWidget#searchFunction} which given an input string returns a list of results</li>
 *     <li>A result to string function {@link SearchWidget#elementAsString} which translates a result as a String</li>
 *     <li>A tooltip function {@link SearchWidget#elementAsTooltip} which translates a result to a line-wrapped tooltip</li>
 * </ul>
 */
public class SearchWidget extends Screen {

    /** Maximum number of search result buttons rendered at once. */
    private static final int MAX_RESULTS = 6;

    /** Maximum tooltip width before wrapping to multiple lines. */
    private static final int TOOLTIP_MAX_WIDTH = 220;

    /** Maps a result object to the label shown in the results list. */
    private Function<Object, String> elementAsString;

    /** Resolves matching results for the current query text. */
    @Setter
    private Function<String, List<?>> searchFunction;

    /** Callback invoked when a result is selected by the user. */
    @Setter
    private Function<Object, Void> onSearchResultSelected;

    /** Optional tooltip renderer for result payloads. */
    private Function<Object, String> elementAsTooltip;

    /** Editable text field used to enter the search query. */
    private SimpleTextFieldWidget searchField;

    /** Rendered result elements currently attached to this screen. */
    private final List<GuiEventListener> searchResults;

    /** Tooltip text associated with each rendered result button. */
    private final Map<Button, Component> resultTooltips;

    /** Parent screen rendered underneath this overlay. */
    private final ArdaMapsScreen parent;

    /** Last query processed, used to avoid redundant recalculation. */
    private String cachedSearchString;

    /**
     * Creates the search overlay associated with a parent ArdaMaps screen.
     *
     * @param parent screen to return to when the search overlay is closed
     */
    public SearchWidget(ArdaMapsScreen parent) {

        super(Component.translatable("ardamaps.client.generic.search"));

        this.parent = parent;
        this.searchResults = new ArrayList<>();
        this.resultTooltips = new HashMap<>();
    }

    /**
     * Initializes search UI controls and focuses the search text field.
     */
    @Override
    protected void init() {

        super.init();

        var x = (parent.width / 2) - (ModConstants.BUTTON_WIDTH);
        var y = (parent.height / 2) - (ModConstants.BUTTON_HEIGHT / 2);

        this.searchField = new SimpleTextFieldWidget(font, x, y, ModConstants.BUTTON_WIDTH * 2, ModConstants.SMALL_SQUARED_BUTTON_SIZE, Component.empty());
        this.searchField.setHint(Component.translatable("ardamaps.client.generic.search"));
        this.searchField.setResponder(this::onSearchChanged);

        addRenderableWidget(Button.builder(Component.literal("X"),
                        (buttonWidget) -> onClose())
                .size(ModConstants.SMALL_SQUARED_BUTTON_SIZE, ModConstants.SMALL_SQUARED_BUTTON_SIZE)
                .pos(width - ModConstants.SMALL_SQUARED_BUTTON_SIZE - 10, 10)
                .build());

        var title = new StringWidget(Component.translatable("ardamaps.client.generic.search"), font);
        title.setPosition(x, y - ModConstants.BUTTON_HEIGHT / 2);

        addRenderableWidget(title);
        addRenderableWidget(searchField);

        this.setInitialFocus(searchField);
    }

    /**
     * Rebuilds the displayed result list whenever the query text changes.
     *
     * @param searchString current query text entered by the user
     */
    private void onSearchChanged(String searchString) {

        // Do not recompute result list if we already have done so for the current search string
        if (Objects.equals(this.cachedSearchString, searchString))
            return;
        else this.cachedSearchString = searchString;

        // Exit fast if fewer than 2 characters
        if (searchString == null || searchString.length() < 2) {
            this.searchResults.forEach(this::removeWidget);
            this.searchResults.clear();
            this.resultTooltips.clear();
            return;
        }

        List<?> foundElements = this.searchFunction.apply(searchString);
        this.searchResults.forEach(this::removeWidget);
        this.searchResults.clear();
        this.resultTooltips.clear();

        var resultIndex = 1;
        for (Object element : foundElements) {

            if (resultIndex == MAX_RESULTS) break;

            var elementString = elementAsString.apply(element);

            // Estimate max chars that fit in the button width (font is ~6px/char avg)
            int maxChars = (ModConstants.BUTTON_WIDTH * 2) / 6;
            elementString = ellipseAroundMatch(elementString, searchString, maxChars);

            MutableComponent text = buildHighlightedText(elementString, searchString, ModConstants.COLOR_BLUE_EMPHASIZED);

            var resultButton = buildSearchResultButton(text, element, resultIndex);
            if (elementAsTooltip != null) {
                String tooltip = elementAsTooltip.apply(element);
                if (tooltip != null && !tooltip.isBlank()) {
                    resultTooltips.put(resultButton, buildHighlightedText(tooltip, searchString, ModConstants.COLOR_BLUE_EMPHASIZED));
                }
            }
            this.searchResults.add(resultButton);
            addRenderableWidget(resultButton);

            resultIndex++;
        }

        if (foundElements.size() > MAX_RESULTS) {

            var moreElementsTextWidget = new StringWidget(Component.literal("..."), font);

            var xPosition = width / 2 - moreElementsTextWidget.getWidth() / 2;
            var yPosition = searchField.getY() + 5 + resultIndex * ModConstants.SMALL_SQUARED_BUTTON_SIZE;

            moreElementsTextWidget.setPosition(xPosition, yPosition);
            this.searchResults.add(moreElementsTextWidget);
            addRenderableWidget(moreElementsTextWidget);
        }
    }

    /**
     * Returns a substring of {@code source} centered around the first occurrence
     * of {@code searchString} (case-insensitive), fitting within {@code maxChars}
     * characters, with "…" ellipses appended/prepended as needed.
     */
    private static String ellipseAroundMatch(String source, String searchString, int maxChars) {
        if (source == null || searchString == null || searchString.isEmpty()) return source;

        String lowerSource = source.toLowerCase();
        String lowerSearch = searchString.toLowerCase();
        int matchIndex = lowerSource.indexOf(lowerSearch);

        // No match or already fits — return as-is
        if (matchIndex == -1 || source.length() <= maxChars) return source;

        // Try to centre the window around the match
        int windowStart = Math.max(0, matchIndex - (maxChars - searchString.length()) / 2);
        int windowEnd = Math.min(source.length(), windowStart + maxChars);

        // Clamp start if end hit the boundary
        windowStart = Math.max(0, windowEnd - maxChars);

        String prefix = windowStart > 0 ? "…" : "";
        String suffix = windowEnd < source.length() ? "…" : "";

        return prefix + source.substring(windowStart, windowEnd) + suffix;
    }

    /**
     * Builds a clickable button entry for a search result.
     *
     * @param result      rendered label text for the result
     * @param element     backing element represented by this button
     * @param resultIndex vertical slot index for button placement
     * @return configured result button
     */
    private Button buildSearchResultButton(Component result, Object element, int resultIndex) {

        var height = ModConstants.SMALL_SQUARED_BUTTON_SIZE;

        return Button.builder(
                        result,
                        button -> this.resultSelected(element))
                .size(ModConstants.BUTTON_WIDTH * 2, height)
                .pos(searchField.getX(), searchField.getY() + 5 + resultIndex * height)
                .build();
    }

    /**
     * Handles result selection and closes the search overlay.
     *
     * @param selectedElement selected result payload
     */
    private void resultSelected(Object selectedElement) {

        if (onSearchResultSelected != null)
            onSearchResultSelected.apply(selectedElement);

        onClose();
    }

    /**
     * Handles key press events for the side panel. Handle ENTER key press when results are displayed. First element is
     * selected.
     *
     * @param keyCode   The code of the key that was pressed
     * @param scanCode  The scan code of the key that was pressed
     * @param modifiers Any modifier keys that were held during the key press
     * @return True if event was consumed
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        if (keyCode == GLFW.GLFW_KEY_ENTER
                && !searchResults.isEmpty()
                && searchResults.get(0) instanceof Button buttonWidget) {

            buttonWidget.onPress(event);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        var clickOverButton = children().stream().anyMatch(child -> child.isMouseOver(mouseX, mouseY));

        if (clickOverButton)
            return super.mouseClicked(event, doubleClick);

        // Player clicked outside any button, close the search overlay
        onClose();
        return true;
    }

    /**
     * Closes this overlay and restores the parent screen.
     */
    @Override
    public void onClose() {

        assert this.minecraft != null;
        this.minecraft.setScreen(parent);
    }

    /**
     * Resizes the parent and overlay screens when the window size changes.
     *
     * @param client current Minecraft client instance
     * @param width  new window width
     * @param height new window height
     */
    @Override
    public void resize(int width, int height) {

        if (parent != null) parent.resize(width, height);

        super.resize(width, height);
    }

    /**
     * Renders this widget as an overlay on top of the parent screen.
     *
     * @param context draw context
     * @param mouseX  mouse x position
     * @param mouseY  mouse y position
     * @param delta   frame delta time
     */
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        // Render this screen as an overlay - force mouse position to -1 so that mouse events don't interact
        if (parent != null)
            parent.extractRenderState(context, -1, -1, delta);

        context.fill(0, 0, this.width, this.height, 0xAA000000);

        super.extractRenderState(context, mouseX, mouseY, delta);

        for (var tooltipEntry : resultTooltips.entrySet()) {
            if (tooltipEntry.getKey().isMouseOver(mouseX, mouseY)) {
                List<FormattedCharSequence> wrappedTooltip = font.split(tooltipEntry.getValue(), TOOLTIP_MAX_WIDTH);
                context.setTooltipForNextFrame(font, wrappedTooltip, DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, false);
                break;
            }
        }
    }

    /**
     * Sets the function that converts result objects to display strings.
     *
     * @param elementAsString display mapping function
     */
    public void setResultDisplayFunction(Function<Object, String> elementAsString) {
        this.elementAsString = elementAsString;
    }

    /**
     * Sets the optional function used to render result tooltips.
     *
     * @param elementAsTooltip tooltip mapping function
     */
    public void setResultTooltipFunction(Function<Object, String> elementAsTooltip) {
        this.elementAsTooltip = elementAsTooltip;
    }

    /**
     * Builds text where each occurrence of {@code searchString} in {@code source}
     * is highlighted with the provided colour.
     */
    @SuppressWarnings("SameParameterValue")
    private static MutableComponent buildHighlightedText(String source, String searchString, int highlightColor) {
        String safeSource = source == null ? "" : source;
        if (searchString == null || searchString.isEmpty()) {
            return Component.literal(safeSource);
        }

        String lowerSource = safeSource.toLowerCase();
        String lowerSearch = searchString.toLowerCase();

        MutableComponent text = Component.empty();
        int start = 0;
        int index;

        while ((index = lowerSource.indexOf(lowerSearch, start)) != -1) {
            text.append(Component.literal(safeSource.substring(start, index)));
            text.append(Component.literal(safeSource.substring(index, index + searchString.length()))
                    .withStyle(style -> style.withColor(highlightColor)));
            start = index + searchString.length();
        }

        text.append(Component.literal(safeSource.substring(start)));
        return text;
    }

}
