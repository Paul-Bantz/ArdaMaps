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

import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import com.duom.ardamaps.gui.RenderingUtils;
import com.duom.ardamaps.gui.screens.map.MarkerTypeFilter;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.widgets.CompactCheckboxWidget;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Marker type filter popup content.
 */
public class MarkersPopupContent implements BookLabelPopupContent {

    /** Minimum context menu row height in pixels. */
    private static final int MIN_ITEM_HEIGHT = 18;

    /** Marker type icon size in pixels. */
    private static final int ICON_SIZE = 12;

    /** Gap between a row checkbox and its icon. */
    private static final int CHECKBOX_GAP = 5;

    /** Gap between a row icon and its label. */
    private static final int ICON_TEXT_GAP = 6;

    /** Gap between the two marker type columns. */
    private static final int COLUMN_GAP = 16;

    /** Gap between popup content sections. */
    private static final int SECTION_GAP = Button.DEFAULT_SPACING;

    /** Popup separator height. */
    private static final int SEPARATOR_HEIGHT = 9;

    /** Session marker filter state. */
    private final MarkerTypeFilter markerFilter;

    /** Callback invoked when filter state changes. */
    private final Runnable onFilterChanged;

    /** All marker types button. */
    private final Button allButton;

    /** No marker types button. */
    private final Button noneButton;

    /** Font line height used for stable construction-time geometry. */
    private final int lineHeight;

    /** Popup title width in pixels. */
    private final int titleWidth;

    /** Popup title height in pixels. */
    private final int titleHeight;

    /** Marker type checkbox size in pixels. */
    private final int checkboxSize;

    /** Context menu row height in pixels. */
    private final int itemHeight;

    /** The x-coordinate of the popup on the screen. */
    private final int x;

    /** The y-coordinate of the popup on the screen. */
    private final int y;

    /** The popup width in pixels. */
    private final int width;

    /** The popup height in pixels. */
    private final int height;

    /** Number of rows displayed in the left column. */
    private final int leftRows;

    /** Width of one marker type column. */
    private final int columnWidth;

    /** Total width of the marker type column block, used to centre it in the popup. */
    private final int columnsWidth;

    /** Vertical row-block padding that places rows at the popup corner inset. */
    private final int verticalPadding;

    /** Lazily built checkbox widgets for marker type rows. */
    private final List<CompactCheckboxWidget> checkboxes = new ArrayList<>();

    /** Callback invoked when the popup close button is clicked. */
    private @Nullable Runnable onClose;

    /**
     * Creates marker popup content centred inside the supplied content area.
     *
     * @param contentAreaSupplier Supplies the current padded map content area.
     * @param markerFilter        The session marker filter state.
     * @param onFilterChanged     Callback invoked after a filter change.
     */
    public MarkersPopupContent(Supplier<BackgroundRenderer.GuiLayout> contentAreaSupplier,
                               MarkerTypeFilter markerFilter,
                               Runnable onFilterChanged) {

        this(contentAreaSupplier, markerFilter, onFilterChanged,
                index -> Client.mc().font.width(markerFilter.available().get(index).displayName()),
                Client.mc().font.width(Component.translatable("ardamaps.client.map.screen.markers.title")),
                Client.mc().font.lineHeight,
                CompactCheckboxWidget.BOX_SIZE);
    }

    /**
     * Creates marker popup content with injected text metrics.
     *
     * @param contentAreaSupplier Supplies the current padded map content area.
     * @param markerFilter        The session marker filter state.
     * @param onFilterChanged     Callback invoked after a filter change.
     * @param textWidth           Text width calculator by entry index.
     * @param titleWidth          Popup title width.
     * @param lineHeight          Font line height.
     * @param checkboxSize        Checkbox box size.
     */
    MarkersPopupContent(Supplier<BackgroundRenderer.GuiLayout> contentAreaSupplier,
                        MarkerTypeFilter markerFilter,
                        Runnable onFilterChanged,
                        IntFunction<Integer> textWidth,
                        int titleWidth,
                        int lineHeight,
                        int checkboxSize) {

        this.markerFilter = markerFilter;
        this.onFilterChanged = onFilterChanged;
        this.lineHeight = lineHeight;
        this.titleWidth = (int) (titleWidth * ModConstants.H1_TEXT_SCALE);
        this.titleHeight = (int) (lineHeight * ModConstants.H1_TEXT_SCALE);
        this.checkboxSize = checkboxSize;
        this.itemHeight = Math.max(MIN_ITEM_HEIGHT, checkboxSize + 2);
        this.verticalPadding = Math.max(0, ModConstants.POPUP_CORNER - (itemHeight - lineHeight) / 2);
        this.leftRows = (markerFilter.available().size() + 1) / 2;
        this.columnWidth = widestEntryWidth(textWidth);

        int columns = markerFilter.available().size() > leftRows ? 2 : 1;
        this.columnsWidth = columnWidth * columns
                + (columns > 1 ? COLUMN_GAP : 0);
        int titleAreaWidth = this.titleWidth + PopupCloseButton.SIZE + ModConstants.POPUP_CORNER;
        int desiredWidth = ModConstants.POPUP_CORNER * 2
                + Math.max(columnsWidth, titleAreaWidth);
        int rowsHeight = Math.max(1, leftRows) * itemHeight;
        int desiredHeight = verticalPadding * 2
                + this.titleHeight
                + SECTION_GAP
                + SEPARATOR_HEIGHT
                + SECTION_GAP
                + rowsHeight
                + SECTION_GAP
                + Button.DEFAULT_HEIGHT;

        BackgroundRenderer.GuiLayout contentArea = contentAreaSupplier.get();
        width = Math.min(contentArea.guiWidth(), desiredWidth);
        height = Math.min(contentArea.guiHeight(), desiredHeight);
        x = contentArea.topLeftX() + (contentArea.guiWidth() - width) / 2;
        y = contentArea.topLeftY() + (contentArea.guiHeight() - height) / 2;

        allButton = buildButton("ardamaps.client.map.screen.markers.all", markerFilter::enableAll);
        noneButton = buildButton("ardamaps.client.map.screen.markers.none", markerFilter::disableAll);
        positionButtons();
        syncButtonStates();
    }

    /**
     * Measures the widest marker type row.
     *
     * @param textWidth Text width calculator by entry index.
     * @return The widest row width.
     */
    private int widestEntryWidth(IntFunction<Integer> textWidth) {

        int widest = 0;
        for (int index = 0; index < markerFilter.available().size(); index++) {

            widest = Math.max(widest,
                    checkboxSize + CHECKBOX_GAP + ICON_SIZE + ICON_TEXT_GAP + textWidth.apply(index));
        }

        return widest;
    }

    /**
     * Builds an unregistered popup action button.
     *
     * @param translationKey The button label translation key.
     * @param onClick        The click action.
     * @return The constructed button.
     */
    private Button buildButton(String translationKey, Runnable onClick) {

        return Button.builder(Component.translatable(translationKey), _ -> onClick.run())
                .size(Button.SMALL_WIDTH, Button.DEFAULT_HEIGHT)
                .build();
    }

    /**
     * Renders the marker filter popup.
     *
     * @param context The GUI draw context.
     * @param anchorX The x coordinate of the owning label button.
     * @param anchorY The y coordinate of the owning label button.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     */
    @Override
    public void render(GuiGraphicsExtractor context, int anchorX, int anchorY, int mouseX, int mouseY) {

        renderContents(context, mouseX, mouseY);
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
     * Renders the full unclipped popup contents.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     */
    private void renderContents(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        PopupSurface.drawBackground(context, x, y, width, height);
        renderTitle(context);
        PopupCloseButton.render(context, x, y, width, mouseX, mouseY);
        renderSeparator(context);
        renderEntries(context, mouseX, mouseY);
        renderButtons(context, mouseX, mouseY);
    }

    /**
     * Renders the popup title.
     *
     * @param context The GUI draw context.
     */
    private void renderTitle(GuiGraphicsExtractor context) {

        Font font = Client.mc().font;
        String title = Component.translatable("ardamaps.client.map.screen.markers.title").getString();
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
                width - ModConstants.POPUP_CORNER * 2,
                x + ModConstants.POPUP_CORNER,
                separatorY());
    }

    /**
     * Renders marker type rows.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     */
    private void renderEntries(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        ensureCheckboxes();

        for (int index = 0; index < markerFilter.available().size(); index++) {

            int rowX = rowX(index);
            int rowY = rowY(index);

            MarkerTypeFilter.Entry entry = markerFilter.available().get(index);
            int checkboxY = rowY + (itemHeight - checkboxSize) / 2;
            int entryX = rowX + checkboxSize + CHECKBOX_GAP;

            if (isMouseOverEntry(mouseX, mouseY, index)) context.requestCursor(CursorTypes.POINTING_HAND);

            CompactCheckboxWidget checkbox = checkboxes.get(index);
            checkbox.setX(rowX);
            checkbox.setY(checkboxY);
            checkbox.extractRenderState(context, mouseX, mouseY, 0f);

            context.blitSprite(RenderPipelines.GUI_TEXTURED,
                    IconSpriteAtlas.retrieveSprite(entry.icon()),
                    entryX,
                    rowY + (itemHeight - ICON_SIZE) / 2,
                    ICON_SIZE,
                    ICON_SIZE,
                    ModConstants.COLOR_WHITE);

            context.text(Client.mc().font,
                    Component.literal(entry.displayName()),
                    entryX + ICON_SIZE + ICON_TEXT_GAP,
                    rowY + (itemHeight - lineHeight) / 2,
                    ModConstants.COLOR_DARK_BROWN,
                    false);
        }
    }

    /**
     * Builds checkboxes when needed.
     *
     */
    private void ensureCheckboxes() {

        if (!checkboxes.isEmpty()) return;

        for (MarkerTypeFilter.Entry entry : markerFilter.available()) {

            checkboxes.add(new CompactCheckboxWidget(0, 0, markerFilter.isEnabled(entry.key()),
                    (_, checked) -> {

                        if (markerFilter.isEnabled(entry.key()) != checked) markerFilter.toggle(entry.key());
                    }));
        }
    }

    /**
     * Clears checkbox widgets so they rebuild from filter state.
     */
    private void rebuildCheckboxes() {

        checkboxes.clear();
    }

    /**
     * Syncs action button availability with the current filter state.
     */
    private void syncButtonStates() {

        boolean hasEntries = !markerFilter.available().isEmpty();
        allButton.active = !(hasEntries && markerFilter.isAllEnabled());
        noneButton.active = !(hasEntries && markerFilter.isNoneEnabled());
    }

    /**
     * Positions action buttons inside the popup.
     */
    private void positionButtons() {

        int innerWidth = width - ModConstants.POPUP_CORNER * 2;
        int buttonWidth = (innerWidth - SECTION_GAP) / 2;
        int buttonX = x + ModConstants.POPUP_CORNER;
        int buttonY = y + height - verticalPadding - Button.DEFAULT_HEIGHT;

        allButton.setX(buttonX);
        allButton.setY(buttonY);
        allButton.setWidth(buttonWidth);

        noneButton.setX(buttonX + buttonWidth + SECTION_GAP);
        noneButton.setY(buttonY);
        noneButton.setWidth(buttonWidth);
    }

    /**
     * Renders popup action buttons.
     *
     * @param context The GUI draw context.
     * @param mouseX  The mouse x position.
     * @param mouseY  The mouse y position.
     */
    private void renderButtons(GuiGraphicsExtractor context, int mouseX, int mouseY) {

        syncButtonStates();
        positionButtons();
        allButton.extractRenderState(context, mouseX, mouseY, 0f);

        noneButton.extractRenderState(context, mouseX, mouseY, 0f);
    }

    /**
     * Gets the x coordinate for a row.
     *
     * @param index The entry index.
     * @return The row x coordinate.
     */
    private int rowX(int index) {

        int column = index < leftRows ? 0 : 1;
        return x + (width - columnsWidth) / 2 + column * (columnWidth + COLUMN_GAP);
    }

    /**
     * Gets the y coordinate for a row.
     *
     * @param index The entry index.
     * @return The row y coordinate.
     */
    private int rowY(int index) {

        int row = index < leftRows ? index : index - leftRows;
        return separatorY() + SEPARATOR_HEIGHT + SECTION_GAP + row * itemHeight;
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
     * Checks whether the mouse is over a marker type entry.
     *
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param index  The entry index.
     * @return True when the mouse is over the entry row.
     */
    private boolean isMouseOverEntry(double mouseX, double mouseY, int index) {

        return mouseX >= rowX(index)
                && mouseX < rowX(index) + columnWidth
                && mouseY >= rowY(index)
                && mouseY < rowY(index) + itemHeight;
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

        positionButtons();
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));
        if (button == 0 && PopupCloseButton.isMouseOver(x, y, width, mouseX, mouseY)) {

            if (onClose != null) onClose.run();
            return true;
        }

        if (button == 0 && allButton.active && allButton.isMouseOver(mouseX, mouseY)) {

            markerFilter.enableAll();
            rebuildCheckboxes();
            syncButtonStates();
            onFilterChanged.run();
            return true;
        }

        if (button == 0 && noneButton.active && noneButton.isMouseOver(mouseX, mouseY)) {

            markerFilter.disableAll();
            rebuildCheckboxes();
            syncButtonStates();
            onFilterChanged.run();
            return true;
        }

        for (CompactCheckboxWidget checkbox : checkboxes) {

            if (!checkbox.mouseClicked(event, false)) continue;

            syncButtonStates();
            onFilterChanged.run();
            return true;
        }

        for (int index = 0; index < markerFilter.available().size(); index++) {

            if (!isMouseOverEntry(mouseX, mouseY, index)) continue;

            markerFilter.toggle(markerFilter.available().get(index).key());
            rebuildCheckboxes();
            syncButtonStates();
            onFilterChanged.run();
            return true;
        }

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

        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Gets the popup x coordinate.
     *
     * @return The x coordinate.
     */
    int getX() {

        return x;
    }

    /**
     * Gets the popup y coordinate.
     *
     * @return The y coordinate.
     */
    int getY() {

        return y;
    }

    /**
     * Checks whether all available marker types are enabled.
     *
     * @return True when all available marker types are enabled.
     */
    boolean isAllToggled() {

        return !markerFilter.available().isEmpty() && markerFilter.isAllEnabled();
    }

    /**
     * Checks whether no available marker types are enabled.
     *
     * @return True when no available marker types are enabled.
     */
    boolean isNoneToggled() {

        return !markerFilter.available().isEmpty() && markerFilter.isNoneEnabled();
    }

    /**
     * Gets the popup width in pixels.
     *
     * @return The popup width.
     */
    @Override
    public int getWidth() {

        return width;
    }

    /**
     * Gets the popup height in pixels.
     *
     * @return The popup height.
     */
    @Override
    public int getHeight() {

        return height;
    }
}
