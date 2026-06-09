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

import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A custom dropdown widget for the map GUI that supports displaying options with both text and icons.
 * This widget extends the base DropdownWidget and adds functionality to render options as square icon buttons when configured.
 * It also handles rendering the expanded dropdown list with appropriate styling based on the configuration.
 *
 * @param <T> The type of the options in the dropdown.
 * @param <E> The type of the items returned by the optionDisplay function, which must extend TextIdentifierPairItem.
 */
public class MapDropdownWidget<T, E extends TextIdentifierPairItem> extends DropdownWidget<T, E> {

    /** Margin between label and icon. */
    public static final int LABEL_MARGIN = 5;

    /** Whether the displayed buttons are simple squares (ie no label same width and height) */
    public final boolean isSquareIconButton;

    /**
     * Constructs a new MapDropdownWidget with the specified parameters, allowing for extensive customization of its appearance and behaviour.
     *
     * @param x                 The x-coordinate of the top-left corner of the dropdown widget on the screen.
     * @param y                 The y-coordinate of the top-left corner of the dropdown widget on the screen.
     * @param width             The width of the dropdown widget.
     * @param height            The height of the dropdown widget.
     * @param title             The title text displayed on the dropdown widget when no option is selected.
     * @param nullValueText     The text displayed when the selected value is null.
     * @param placeholderIcon   The icon displayed when the selected value is null.
     * @param options           The list of options available in the dropdown, each of type T.
     * @param optionDisplay     A function that takes an option of type T and returns an item of type E, which contains the text and icon to be displayed for that option.
     * @param selected          The currently selected option of type T, which can be null if no option is selected.
     * @param onSelect          A consumer function that is called when an option is selected, taking the selected option of type T as its parameter.
     * @param allowNull         Whether to allow a null selection, which would display the nullValueText and placeholderIcon.
     * @param expanded          Whether the dropdown should be initially expanded to show the options.
     * @param displayAsSprite   Whether to render icons as sprites from the IconSpriteAtlas instead of as regular textures.
     * @param displayLabels     Whether to display text labels for the options in the dropdown.
     * @param displayIcons      Whether to display icons for the options in the dropdown.
     * @param displayArrows     Whether to display arrows indicating the dropdown can be expanded.
     * @param expandDirection   The direction in which the dropdown should expand when activated (e.g., downwards, upwards, etc.).
     * @param maxVisibleOptions The maximum number of options to display when the dropdown is expanded, with excess options being scrollable.
     */
    public MapDropdownWidget(
            int x,
            int y,
            int width,
            int height,
            Text title,
            Text nullValueText,
            Identifier placeholderIcon,
            List<T> options,
            Function<T, E> optionDisplay,
            @Nullable T selected,
            Consumer<T> onSelect,
            boolean allowNull,
            boolean expanded,
            boolean displayAsSprite,
            boolean displayLabels,
            boolean displayIcons,
            boolean displayArrows,
            ExpandDirection expandDirection,
            int maxVisibleOptions
    ) {

        super(x, y, width, height, title, nullValueText, placeholderIcon, options, optionDisplay, selected, onSelect,
                allowNull, expanded, displayAsSprite, displayLabels, displayIcons, displayArrows, expandDirection,
                maxVisibleOptions);

        if (!displayLabels) iconSize = (Math.max(originalWidth, originalHeight));

        isSquareIconButton = originalWidth == originalHeight && displayIcons && !displayLabels;

        if (isSquareIconButton) {

            iconSize -= 2;
            buttonPadding = 0;
        }
    }

    /**
     * Renders the dropdown button, displaying either a square icon button or a base button with text and icon based on the configuration.
     *
     * @param context The DrawContext used for rendering the button.
     * @param mouseX  The current x-coordinate of the mouse cursor, used for hover detection.
     * @param mouseY  The current y-coordinate of the mouse cursor, used for hover detection.
     * @param delta   The time delta since the last render call, which can be used for animations or other time-based effects.
     */
    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {

        int x = getX();
        int y = getY();

        boolean isHovered =
                mouseX >= x && mouseX <= x + originalWidth &&
                        mouseY >= y && mouseY <= y + originalHeight;

        if (isSquareIconButton)
            drawSquareImageButton(context, x, y, isHovered);
        else
            drawBaseButton(context, x, y, isHovered);
    }

    /**
     * Renders a square image button for the dropdown, which is used when the dropdown is configured to display options as square icon buttons without labels.
     *
     * @param context The DrawContext used for rendering the square image button.
     * @param x       The x-coordinate where the square image button should be rendered.
     * @param y       The y-coordinate where the square image button should be rendered.
     * @param hovered Whether the square image button is being hovered by the mouse cursor, which can be used to apply hover effects to the button's appearance.
     */
    private void drawSquareImageButton(DrawContext context, int x, int y, boolean hovered) {

        E itemPair = optionDisplay.apply(selected);
        var icon = (selected == null) ? placeholderIcon : itemPair.image();
        var buttonSize = iconSize + 2;

        if (icon == null) return;

        if (displayAsSprite)
            context.drawSprite(x, y, 0, iconSize, iconSize, IconSpriteAtlas.retrieveSprite(icon));
        else
            context.drawTexture(icon, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);

        var u = 288f;

        if (hovered) u += 96;

        context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                x - 1, y,
                buttonSize, buttonSize,
                u, 224f,
                96, 96,
                512, 512);
    }

    /**
     * Renders the base button for the dropdown, which includes both the icon and the text label when configured to display labels. This method is used when the dropdown is not configured as a square icon button.
     * It applies a nine-sliced texture for the button background and then renders the icon and label based on the current selection and configuration.
     *
     * @param context The DrawContext used for rendering the base button.
     * @param x       The x-coordinate where the base button should be rendered.
     * @param y       The y-coordinate where the base button should be rendered.
     * @param hovered Whether the base button is being hovered by the mouse cursor, which can be used to apply hover effects to the button's appearance.
     */
    private void drawBaseButton(DrawContext context, int x, int y, boolean hovered) {

        var u = 16;
        var v = hovered ? 16 * 3 : 16;
        var textRenderer = Client.mc().textRenderer;

        E itemPair = optionDisplay.apply(selected);
        Text label = (selected == null) ? placeholderText : itemPair.text();

        context.drawNineSlicedTexture(ModConstants.MAP_GUI_ELEMENTS,
                x, y,
                width, originalHeight,
                12, // Left slice width
                9,              // Top slice height
                12,             // Right slice width
                9,              // Bottom slice height
                128,            // Centre slice width
                32,             // Center slice height
                u, v);

        if (displayIcons) {

            var icon = (selected == null) ? placeholderIcon : itemPair.image();

            var halfIconSize = (int) (iconSize / 2f);
            var iconX = x;
            var iconY = y + originalHeight / 2 - halfIconSize;

            if (icon != null) {

                if (displayLabels && label != null)
                    iconX = x + (width - textRenderer.getWidth(label) - (iconSize / 2) - LABEL_MARGIN) / 2;

                if (displayAsSprite)
                    context.drawSprite(iconX, iconY, 0, iconSize, iconSize, IconSpriteAtlas.retrieveSprite(icon));
                else
                    context.drawTexture(icon, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
            }
        }

        if (displayLabels) {

            int textX = x + (getWidth() - textRenderer.getWidth(label)) / 2;

            if (displayIcons)
                textX = (x + (width - textRenderer.getWidth(label) - iconSize - LABEL_MARGIN) / 2) + iconSize + LABEL_MARGIN;

            int textY = y + (originalHeight / 2 - Client.mc().textRenderer.fontHeight / 2);

            context.drawText(Client.mc().textRenderer, label, textX, textY, ModConstants.COLOR_DARK_BROWN, false);
        }
    }

    /**
     * Renders the expanded dropdown list, adjusting the rendering based on whether the dropdown is configured as a square icon button or a standard button.
     * For square icon buttons, it renders each option as a separate square button. For standard buttons, it renders a nine-sliced background and then the options on top.
     *
     * @param context The DrawContext used for rendering the expanded dropdown.
     * @param items   The list of items to be rendered in the expanded dropdown, each of type T.
     * @param mouseX  The current x-coordinate of the mouse cursor, used for hover detection on the dropdown options.
     * @param mouseY  The current y-coordinate of the mouse cursor, used for hover detection on the dropdown options.
     */
    @Override
    protected void renderExpandedDropdown(DrawContext context, List<T> items, int mouseX, int mouseY) {

        var numOptions = Math.min(options.size(), maxVisibleOptions);

        if (!allowNull) numOptions -= 1;

        var computedHeight = Math.min(height, (numOptions * originalHeight));

        boolean expandUp =
                expandDirection.equals(ExpandDirection.UP_RIGHT) ||
                        expandDirection.equals(ExpandDirection.UP_LEFT);

        if (isSquareIconButton) {

            super.renderExpandedDropdown(context, items, mouseX, mouseY);

            var y = getY();
            var buttonSize = iconSize + 2;
            var visibleCount = Math.min(items.size(), maxVisibleOptions) - 1;

            for (int idx = 0; idx < visibleCount; idx++) {

                if (expandUp) {
                    y -= originalHeight;
                } else {
                    y += originalHeight;
                }

                var u = 288f;

                if (isMouseOverItem(mouseX, mouseY, y)) u += 96;

                context.drawTexture(ModConstants.MAP_GUI_ELEMENTS,
                        getX() - 1, y,
                        buttonSize, buttonSize,
                        u, 224f,
                        96, 96,
                        512, 512);
            }

        } else {

            var y = getY() + originalHeight;

            if (expandUp) y = getY() - computedHeight;

            context.drawNineSlicedTexture(ModConstants.MAP_GUI_ELEMENTS,
                    getX(), y,
                    width, computedHeight,
                    16,
                    16,
                    16,
                    16,
                    64,
                    64,
                    16, 176);

            super.renderExpandedDropdown(context, items, mouseX, mouseY);
        }
    }

    /**
     * Renders a slice of the dropdown list for each option, applying special styling for the top and bottom slices when the dropdown is expanded.
     * This method is overridden to provide custom rendering logic for the dropdown options based on whether the dropdown is configured as a square icon button or a standard button.
     * For square icon buttons, this method does not render any additional styling since each option is rendered as a separate button. For standard buttons, it applies nine-sliced textures to create a cohesive dropdown appearance.
     *
     * @param context    The DrawContext used for rendering the list slice.
     * @param x          The x-coordinate where the list slice should be rendered.
     * @param y          The y-coordinate where the list slice should be rendered.
     * @param isHovered  Whether the current list slice is being hovered by the mouse cursor, which can be used to apply hover effects.
     * @param isSelected Whether the current list slice represents the selected option, which can be used to apply selection effects.
     */
    @Override
    protected void drawListSlice(DrawContext context, int x, int y, boolean isHovered, boolean isSelected) {

        if (isSquareIconButton) return;

        if (isHovered) {

            var topSliceHeight = 1;
            var bottomSliceHeight = 1;
            var numOptions = Math.min(options.size(), maxVisibleOptions);
            var computedHeight = Math.min(height, ((numOptions - 1) * originalHeight));

            boolean expandUp =
                    expandDirection.equals(ExpandDirection.UP_RIGHT) ||
                            expandDirection.equals(ExpandDirection.UP_LEFT);

            if (expandUp) {

                if (y == getY() - computedHeight) topSliceHeight = 16;
                if (y == getY() - originalHeight) bottomSliceHeight = 16;
            } else {

                if (y == getY() + originalHeight) topSliceHeight = 16;
                if (y == getY() + (maxVisibleOptions) * originalHeight) bottomSliceHeight = 16;
            }

            context.drawNineSlicedTexture(ModConstants.MAP_GUI_ELEMENTS,
                    x, y,
                    width, originalHeight,
                    16,
                    topSliceHeight,
                    16,
                    bottomSliceHeight,
                    64,
                    64,
                    80, 176);
        }
    }

    /**
     * Returns the colour to be used for rendering the text labels in the dropdown, which is a dark brown colour defined in the ModConstants.
     *
     * @return The integer colour value representing the dark brown colour for text labels.
     */
    @Override
    protected int getLabelColor() {
        return ModConstants.COLOR_DARK_BROWN;
    }
}
