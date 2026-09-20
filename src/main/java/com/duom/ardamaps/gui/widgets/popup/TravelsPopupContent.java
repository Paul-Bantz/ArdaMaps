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

package com.duom.ardamaps.gui.widgets.popup;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.conversion.DistanceUnitConverter;
import com.duom.ardamaps.core.data.trail.MovementStats;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.RenderingUtils;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Popup content for player movement totals and reset controls.
 */
public class TravelsPopupContent implements BookLabelPopupContent {

    /** Left margin from the outer book texture. */
    private static final int POPUP_MARGIN_X = 8;

    /** Gap between popup content sections. */
    private static final int SECTION_GAP = Button.DEFAULT_SPACING;

    /** Popup separator height. */
    private static final int SEPARATOR_HEIGHT = 9;

    /** Number of movement-stat rows. */
    private static final int ROW_COUNT = 3;

    /** Reset button for clearing movement tracking progress. */
    private final Button resetButton;

    /** Font line height used for stable construction-time geometry. */
    private final int lineHeight;

    /** Popup title width in pixels. */
    private final int titleWidth;

    /** Popup title height in pixels. */
    private final int titleHeight;

    /** Movement row height in pixels. */
    private final int rowHeight;

    /** Vertical row-block padding that places rows at the popup corner inset. */
    private final int verticalPadding;

    /** The x-coordinate of the popup on the screen. */
    private final int x;

    /** The y-coordinate of the popup on the screen. */
    private final int y;

    /** The popup width in pixels. */
    private final int width;

    /** The popup height in pixels. */
    private final int height;

    /** Callback invoked when the popup close button is clicked. */
    private @Nullable Runnable onClose;

    /**
     * Creates travels popup content anchored to the supplied book area.
     *
     * @param bookAreaSupplier Supplies the current outer book texture area.
     */
    public TravelsPopupContent(Supplier<BackgroundRenderer.GuiLayout> bookAreaSupplier) {

        this(bookAreaSupplier,
                widestCurrentRowWidth(),
                Client.mc().font.width(Component.translatable("ardamaps.client.map.screen.travels.title")),
                Client.mc().font.lineHeight);
    }

    /**
     * Creates travels popup content with injected text metrics.
     *
     * @param bookAreaSupplier Supplies the current outer book texture area.
     * @param widestRowWidth   Widest movement row width.
     * @param titleWidth       Popup title width.
     * @param lineHeight       Font line height.
     */
    TravelsPopupContent(Supplier<BackgroundRenderer.GuiLayout> bookAreaSupplier,
                        int widestRowWidth,
                        int titleWidth,
                        int lineHeight) {

        this.lineHeight = lineHeight;
        this.titleWidth = (int) (titleWidth * ModConstants.H1_TEXT_SCALE);
        this.titleHeight = (int) (lineHeight * ModConstants.H1_TEXT_SCALE);
        this.rowHeight = rowHeight(lineHeight);
        this.verticalPadding = verticalPadding(lineHeight);

        int titleAreaWidth = this.titleWidth + PopupCloseButton.SIZE + ModConstants.POPUP_CONTENT_INSET;
        int desiredWidth = ModConstants.POPUP_CONTENT_INSET * 2
                + Math.max(Math.max(widestRowWidth, titleAreaWidth), 90);

        BackgroundRenderer.GuiLayout bookArea = bookAreaSupplier.get();
        width = Math.min(bookArea.guiWidth(), desiredWidth);
        height = computeHeight(lineHeight, bookArea.guiHeight());
        x = bookArea.topLeftX() + POPUP_MARGIN_X - ModConstants.POPUP_SHADOW_INSET;
        y = bookArea.topLeftY() + (bookArea.guiHeight() - height) / 2;

        resetButton = Button.builder(Component.translatable("ardamaps.client.map.screen.travels.reset"),
                        _ -> ArdaMapsClient.resetMovementTracking())
                .size(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("ardamaps.client.map.screen.travels.reset.tooltip")))
                .build();
        positionButton();
    }

    /**
     * Computes the preferred popup height for the supplied book area.
     *
     * @param bookArea Current outer book texture area.
     * @return The preferred popup height.
     */
    public static int preferredHeight(BackgroundRenderer.GuiLayout bookArea) {

        return computeHeight(Client.mc().font.lineHeight, bookArea.guiHeight());
    }

    /**
     * Computes the movement row height from a font line height.
     *
     * @param lineHeight Font line height.
     * @return The movement row height.
     */
    static int rowHeight(int lineHeight) {

        return Math.max(18, lineHeight + 4);
    }

    /**
     * Computes vertical padding that aligns rows with the popup corner inset.
     *
     * @param lineHeight Font line height.
     * @return The popup vertical padding.
     */
    static int verticalPadding(int lineHeight) {

        return Math.max(0, ModConstants.POPUP_CONTENT_INSET - (rowHeight(lineHeight) - lineHeight) / 2);
    }

    /**
     * Computes popup height from font metrics and available book height.
     *
     * @param lineHeight Font line height.
     * @param guiHeight  Available book height.
     * @return The clamped popup height.
     */
    static int computeHeight(int lineHeight, int guiHeight) {

        int computedRowHeight = rowHeight(lineHeight);
        int computedVerticalPadding = verticalPadding(lineHeight);
        int titleHeight = (int) (lineHeight * ModConstants.H1_TEXT_SCALE);
        int desiredHeight = computedVerticalPadding * 2
                + titleHeight
                + SECTION_GAP
                + SEPARATOR_HEIGHT
                + SECTION_GAP
                + ROW_COUNT * computedRowHeight
                + SECTION_GAP
                + Button.DEFAULT_HEIGHT;

        return Math.min(guiHeight, desiredHeight);
    }

    /**
     * Measures the widest currently rendered movement-stat row.
     *
     * @return The widest row width.
     */
    private static int widestCurrentRowWidth() {

        Font font = Client.mc().font;
        MovementStats stats = ArdaMapsClient.CONFIG.getClientProgress().getMovementStats();
        int walkedWidth = font.width(walkedRow(stats));
        int flownWidth = font.width(flownRow(stats));
        int swamWidth = font.width(swamRow(stats));

        return Math.max(walkedWidth, Math.max(flownWidth, swamWidth));
    }

    /**
     * Renders the travels popup.
     *
     * @param context The GUI draw context.
     * @param anchorX The x coordinate of the owning label button.
     * @param anchorY The y coordinate of the owning label button.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     */
    @Override
    public void render(GuiGraphicsExtractor context, int anchorX, int anchorY, int mouseX, int mouseY) {

        PopupSurface.drawBackground(context, x, y, width, height);
        renderTitle(context);
        PopupCloseButton.render(context, x, y, width, PopupCloseButton.shadowedInset(), mouseX, mouseY);
        renderSeparator(context);
        renderRows(context);
        renderButton(context, mouseX, mouseY);
    }

    /**
     * Supplies a callback that closes the owning popup.
     *
     * @param onClose Callback invoked to close the popup.
     */
    @Override
    public void setOnClose(@Nullable Runnable onClose) {

        this.onClose = onClose;
    }

    /**
     * Renders the popup title.
     *
     * @param context The GUI draw context.
     */
    private void renderTitle(GuiGraphicsExtractor context) {

        Font font = Client.mc().font;
        String title = Component.translatable("ardamaps.client.map.screen.travels.title").getString();
        RenderingUtils.renderH1(context,
                font,
                title,
                x + (width - titleWidth) / 2,
                y + verticalPadding,
                ModConstants.COLOR_BLUE);
    }

    /**
     * Renders the separator below the popup title.
     *
     * @param context The GUI draw context.
     */
    private void renderSeparator(GuiGraphicsExtractor context) {

        RenderingUtils.renderSeparator(context,
                width - ModConstants.POPUP_CONTENT_INSET * 2,
                x + ModConstants.POPUP_CONTENT_INSET,
                separatorY());
    }

    /**
     * Renders live movement-stat rows.
     *
     * @param context The GUI draw context.
     */
    private void renderRows(GuiGraphicsExtractor context) {

        MovementStats stats = ArdaMapsClient.CONFIG.getClientProgress().getMovementStats();
        Font font = Client.mc().font;
        int rowX = x + ModConstants.POPUP_CONTENT_INSET;

        context.text(font, walkedRow(stats), rowX, rowY(0), ModConstants.COLOR_DARK_BROWN, false);
        context.text(font, flownRow(stats), rowX, rowY(1), ModConstants.COLOR_DARK_BROWN, false);
        context.text(font, swamRow(stats), rowX, rowY(2), ModConstants.COLOR_DARK_BROWN, false);
    }

    /**
     * Builds the walked-distance row.
     *
     * @param stats Movement stats to read.
     * @return The walked-distance row.
     */
    private static Component walkedRow(MovementStats stats) {

        return Component.translatable("ardamaps.client.map.screen.travels.walked",
                DistanceUnitConverter.asRealWorldDistance(stats.getWalkedMetres()));
    }

    /**
     * Builds the flown-distance row.
     *
     * @param stats Movement stats to read.
     * @return The flown-distance row.
     */
    private static Component flownRow(MovementStats stats) {

        return Component.translatable("ardamaps.client.map.screen.travels.flown",
                DistanceUnitConverter.asRealWorldDistance(stats.getFlownMetres()));
    }

    /**
     * Builds the swam-distance row.
     *
     * @param stats Movement stats to read.
     * @return The swam-distance row.
     */
    private static Component swamRow(MovementStats stats) {

        return Component.translatable("ardamaps.client.map.screen.travels.swam",
                DistanceUnitConverter.asRealWorldDistance(stats.getSwamMetres()));
    }

    /**
     * Positions the reset button inside the popup.
     */
    private void positionButton() {

        resetButton.setX(x + ModConstants.POPUP_CONTENT_INSET);
        resetButton.setY(y + height - verticalPadding - Button.DEFAULT_HEIGHT);
        resetButton.setWidth(width - ModConstants.POPUP_CONTENT_INSET * 2);
    }

    /**
     * Renders the reset button.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     */
    private void renderButton(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        positionButton();
        resetButton.extractRenderState(context, mouseX, mouseY, 0f);
    }

    /**
     * Gets the y coordinate for the popup separator.
     *
     * @return The separator y coordinate.
     */
    private int separatorY() {

        return y + verticalPadding + titleHeight + SECTION_GAP;
    }

    /**
     * Gets the y coordinate for a row.
     *
     * @param index The row index.
     * @return The row y coordinate.
     */
    private int rowY(int index) {

        return separatorY() + SEPARATOR_HEIGHT + SECTION_GAP + index * rowHeight
                + (rowHeight - lineHeight) / 2;
    }

    /**
     * Handles popup clicks.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param button The clicked mouse button.
     * @return True when the click was handled or swallowed inside the popup.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        positionButton();
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));
        if (button == 0 && PopupCloseButton.isMouseOver(x, y, width, PopupCloseButton.shadowedInset(), mouseX, mouseY)) {

            if (onClose != null) onClose.run();
            return true;
        }

        if (button == 0 && resetButton.mouseClicked(event, false)) return true;

        return isMouseOver(mouseX, mouseY);
    }

    /**
     * Checks whether the mouse is over the popup.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @return True if the mouse is over the popup.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {

        int inset = ModConstants.POPUP_SHADOW_INSET;
        return mouseX >= x + inset
                && mouseX < x + width - inset
                && mouseY >= y + inset
                && mouseY < y + height - inset;
    }

    /** {@inheritDoc} */
    @Override
    public int getWidth() {

        return width;
    }

    /** {@inheritDoc} */
    @Override
    public int getHeight() {

        return height;
    }
}
