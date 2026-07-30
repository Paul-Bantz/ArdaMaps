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
import com.duom.ardamaps.core.data.config.MapLayerRange;
import com.duom.ardamaps.gui.ModConstants;
import lombok.Getter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * A compact horizontally scrolling selector for vertically ranged map layers.
 */
public class RangeSelectionWidget extends AbstractWidget {

    /** Background colour used behind the full strip. */
    private static final int BACKGROUND_COLOR = 0x70000000;

    /** Fully transparent colour used at the start of the label-area fade. */
    private static final int BACKGROUND_TRANSPARENT = 0x00000000;

    /** Horizontal padding used when computing automatic item width. */
    private static final int ITEM_HORIZONTAL_MARGIN = 4;

    /** Horizontal padding around the fixed strip label. */
    private static final int LABEL_MARGIN = 4;

    /** Text drawn at clipped edges when more content is available. */
    private static final String ELLIPSIS = "...";

    /** Default cap geometry used by headless hit-testing without touching the Minecraft client. */
    private static final int ELLIPSIS_CAP_WIDTH = 14;

    /** Minimum horizontal pointer travel before a press counts as a drag rather than a click. */
    private static final int DRAG_THRESHOLD = 3;

    /** Vertical gap between the widget top edge and the tooltip anchor. */
    private static final int TOOLTIP_GAP = 4;

    /** Screen margin preserved when clamping tooltip positions. */
    private static final int TOOLTIP_SCREEN_MARGIN = 4;

    /** Callback invoked when the user selects a range by clicking the strip. */
    private final Consumer<MapLayerRange> onSelect;

    /** True when item width should be recomputed whenever ranges are replaced. */
    private final boolean autoItemWidth;

    /** Ranges displayed by the strip in their configured order. */
    @Getter
    private List<MapLayerRange> ranges;

    /** Pixel width used by every range cell. */
    @Getter
    private int itemWidth;

    /** Fixed label drawn to the left of the selectable range cells. */
    @Getter
    private Component label = Component.empty();

    /** Cached pixel width occupied by the label and its horizontal margins. */
    private int labelWidth = 0;

    /** Current selected list position, or -1 when nothing is selected. */
    @Getter
    private int selectedIndex = -1;

    /** Horizontal content offset in pixels, where 0 means the first cell is flush left. */
    @Getter
    private double scrollOffset = 0;

    /** True while a pointer press is being tracked for click or drag behaviour. */
    @Getter
    private boolean dragging = false;

    /** Mouse X coordinate where the active press began. */
    @Getter
    private double dragStartMouseX = 0;

    /** Scroll offset where the active press began. */
    @Getter
    private double dragStartOffset = 0;

    /** True once the active press has moved far enough to be treated as a drag. */
    @Getter
    private boolean dragMoved = false;

    /**
     * Creates a range selector with an explicit fixed item width.
     *
     * @param x         The x-coordinate of the widget.
     * @param y         The y-coordinate of the widget.
     * @param width     The width of the widget.
     * @param height    The height of the widget.
     * @param label     The fixed label to draw before the range cells.
     * @param ranges    The ranges to display.
     * @param itemWidth The fixed width of each item cell.
     * @param onSelect  Callback invoked when a range is selected by click.
     */
    public RangeSelectionWidget(int x, int y, int width, int height,
                                @Nullable Component label, List<MapLayerRange> ranges, int itemWidth,
                                Consumer<MapLayerRange> onSelect) {

        this(x, y, width, height, label, ranges, itemWidth, onSelect, false);
    }

    /**
     * Creates a range selector.
     *
     * @param x             The x-coordinate of the widget.
     * @param y             The y-coordinate of the widget.
     * @param width         The width of the widget.
     * @param height        The height of the widget.
     * @param label         The fixed label to draw before the range cells.
     * @param ranges        The ranges to display.
     * @param itemWidth     The width of each item cell.
     * @param onSelect      Callback invoked when a range is selected by click.
     * @param autoItemWidth Whether the item width should be recomputed on range changes.
     */
    private RangeSelectionWidget(int x, int y, int width, int height,
                                 @Nullable Component label, List<MapLayerRange> ranges, int itemWidth,
                                 Consumer<MapLayerRange> onSelect, boolean autoItemWidth) {

        super(x, y, width, height, Component.empty());
        this.ranges = List.copyOf(ranges);
        this.itemWidth = Math.max(1, itemWidth);
        this.onSelect = onSelect;
        this.autoItemWidth = autoItemWidth;
        setLabel(label);
    }

    /**
     * Creates a range selector with an item width based on the current text renderer.
     *
     * @param x        The x-coordinate of the widget.
     * @param y        The y-coordinate of the widget.
     * @param width    The width of the widget.
     * @param height   The height of the widget.
     * @param label    The fixed label to draw before the range cells.
     * @param ranges   The ranges to display.
     * @param onSelect Callback invoked when a range is selected by click.
     */
    public RangeSelectionWidget(int x, int y, int width, int height,
                                @Nullable Component label, List<MapLayerRange> ranges,
                                Consumer<MapLayerRange> onSelect) {

        this(x, y, width, height, label, ranges, computeDefaultItemWidth(ranges), onSelect, true);
    }

    /**
     * Computes the total width occupied by label and range cells.
     *
     * @return The occupied content width in pixels.
     */
    private int usedWidth() {

        return labelWidth + viewportWidth() + (showCaps() ? 2 * ELLIPSIS_CAP_WIDTH : 0);
    }

    /**
     * Computes the screen X coordinate of the occupied content's left edge.
     *
     * @return The content left edge in pixels.
     */
    private int contentX() {

        return getX() + width - usedWidth();
    }

    /**
     * Computes the screen X coordinate of the range-cell viewport's left edge.
     *
     * @return The viewport left edge in pixels.
     */
    int stripX() {

        return contentX() + labelWidth + (showCaps() ? ELLIPSIS_CAP_WIDTH : 0);
    }

    /**
     * Computes the screen X coordinate of the left edge of an item cell.
     *
     * @param index The list position to locate.
     * @return The screen X coordinate of the cell.
     */
    double itemXAt(int index) {

        return stripX() + scrollOffset + index * itemWidth;
    }

    /**
     * Finds the item index under a mouse position.
     *
     * @param mouseX The mouse X coordinate.
     * @param mouseY The mouse Y coordinate.
     * @return The hovered list position, or -1 when outside the widget or range content.
     */
    int indexAt(double mouseX, double mouseY) {

        if (!isMouseOver(mouseX, mouseY)) return -1;
        if (mouseX < stripX()) return -1;
        if (mouseX >= stripX() + viewportWidth()) return -1;

        int index = (int) Math.floor((mouseX - stripX() - scrollOffset) / itemWidth);
        if (index < 0 || index >= ranges.size()) return -1;

        return index;
    }

    /**
     * Computes the first range index that can be visible in the strip.
     *
     * @return The first visible list position, or -1 when there are no ranges.
     */
    int firstVisibleIndex() {

        if (ranges.isEmpty()) return -1;

        return Math.max(0, (int) Math.floor(-scrollOffset / itemWidth));
    }

    /**
     * Computes the last range index that can be visible in the strip.
     *
     * @return The last visible list position, or -1 when there are no ranges.
     */
    int lastVisibleIndex() {

        if (ranges.isEmpty()) return -1;

        int last = (int) Math.ceil((viewportWidth() - scrollOffset) / itemWidth) - 1;
        return Math.min(ranges.size() - 1, Math.max(0, last));
    }

    /**
     * Scrolls the strip so the requested index is centered when clamping permits it.
     *
     * @param index The list position to centre.
     */
    void centerOn(int index) {

        scrollOffset = clampScrollOffset(viewportWidth() / 2.0 - (index * itemWidth + itemWidth / 2.0));
    }

    /**
     * Renders the range selector strip.
     *
     * @param context The drawing context.
     * @param mouseX  The current mouse X coordinate.
     * @param mouseY  The current mouse Y coordinate.
     * @param delta   The frame delta.
     */
    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {

        if (!visible) return;

        int hoveredIndex = dragging ? -1 : indexAt(mouseX, mouseY);
        Font textRenderer = Client.mc().font;
        int contentX = contentX();
        int stripX = stripX();

        renderBackground(context);

        if (labelWidth > 0) {
            int labelTextY = getY() + height / 2 - textRenderer.lineHeight / 2;
            context.drawString(textRenderer, label, contentX + LABEL_MARGIN, labelTextY, ModConstants.COLOR_WHITE);
        }

        int scissorRight = stripX + viewportWidth();
        if (stripX < scissorRight) {
            context.enableScissor(stripX, getY(), scissorRight, getY() + height);

            for (int index = firstVisibleIndex(); index >= 0 && index <= lastVisibleIndex(); index++) {
                int itemX = (int) itemXAt(index);
                if (index == hoveredIndex) {
                    context.fill(itemX, getY(), itemX + itemWidth, getY() + height, ModConstants.COLOR_BLUE);
                }

                String itemLabel = label(ranges.get(index));
                int textX = itemX + itemWidth / 2 - textRenderer.width(itemLabel) / 2;
                int textY = getY() + height / 2 - textRenderer.lineHeight / 2;
                int color = index == selectedIndex ? ModConstants.COLOR_BLUE_EMPHASIZED : ModConstants.COLOR_WHITE;
                context.drawString(textRenderer, itemLabel, textX, textY, color);
            }

            context.disableScissor();
        }

        renderEllipses(context, textRenderer);

        if (hoveredIndex >= 0) renderTooltip(context, textRenderer, hoveredIndex);
    }

    /**
     * Renders the strip background using a horizontal fade beneath the fixed label area.
     *
     * @param context The drawing context.
     */
    private void renderBackground(GuiGraphics context) {

        int contentX = contentX();
        int widgetTop = getY();
        int widgetBottom = getY() + height;
        int labelAreaEnd = contentX + labelWidth;

        if (labelWidth > 0) {
            for (int columnOffset = 0; columnOffset < labelWidth; columnOffset++) {
                int columnX = contentX + columnOffset;
                context.fill(columnX, widgetTop, columnX + 1, widgetBottom, gradientColorAt(columnOffset, labelWidth));
            }
        }

        context.fill(labelAreaEnd, widgetTop, getX() + width, widgetBottom, BACKGROUND_COLOR);
    }

    /**
     * Renders edge ellipses when there is hidden content in either direction.
     *
     * @param context      The drawing context.
     * @param textRenderer The text renderer used for ellipsis measurement.
     */
    private void renderEllipses(GuiGraphics context, Font textRenderer) {

        if (!showCaps()) return;

        int textY = getY() + height / 2 - textRenderer.lineHeight / 2;
        int textWidth = textRenderer.width(ELLIPSIS);

        if (scrollOffset < 0) {
            int capX = contentX() + labelWidth;
            int textX = capX + ELLIPSIS_CAP_WIDTH / 2 - textWidth / 2;
            context.drawString(textRenderer, ELLIPSIS, textX, textY, ModConstants.COLOR_WHITE);
        }

        if (scrollOffset > minScrollOffset()) {
            int capX = stripX() + viewportWidth();
            int textX = capX + ELLIPSIS_CAP_WIDTH / 2 - textWidth / 2;
            context.drawString(textRenderer, ELLIPSIS, textX, textY, ModConstants.COLOR_WHITE);
        }
    }

    /**
     * Renders the hovered range tooltip centred above the active cell.
     *
     * @param context      The drawing context.
     * @param textRenderer The text renderer used for tooltip layout.
     * @param hoveredIndex The hovered range index.
     */
    private void renderTooltip(GuiGraphics context, Font textRenderer, int hoveredIndex) {

        MapLayerRange range = ranges.get(hoveredIndex);
        int anchorX = clampTooltipAnchorX((int) itemXAt(hoveredIndex) + itemWidth / 2, stripX(), stripX() + viewportWidth());
        int anchorY = getY() - TOOLTIP_GAP;
        List<FormattedCharSequence> tooltip = List.of(Component.literal(tooltipLabel(range)).getVisualOrderText());
        context.renderTooltip(textRenderer, tooltip, AboveAnchorTooltipPositioner.INSTANCE, anchorX, anchorY);
    }

    /**
     * Starts tracking a press that may become either a click or a drag.
     *
     * @param mouseX The mouse X coordinate where the press began.
     * @param mouseY The mouse Y coordinate where the press began.
     */
    @Override
    public void onClick(double mouseX, double mouseY) {

        dragging = true;
        dragStartMouseX = mouseX;
        dragStartOffset = scrollOffset;
        dragMoved = false;
    }

    /**
     * Updates horizontal scrolling while a drag is active.
     *
     * @param mouseX The current mouse X coordinate.
     * @param mouseY The current mouse Y coordinate.
     * @param deltaX The horizontal mouse movement since the previous event.
     * @param deltaY The vertical mouse movement since the previous event.
     */
    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {

        scrollOffset = clampScrollOffset(dragStartOffset + (mouseX - dragStartMouseX));
        if (Math.abs(mouseX - dragStartMouseX) > DRAG_THRESHOLD) dragMoved = true;
    }

    /**
     * Clamps a proposed scroll offset into the valid horizontal range.
     *
     * @param offset The proposed scroll offset.
     * @return The clamped scroll offset.
     */
    double clampScrollOffset(double offset) {

        return Math.max(minScrollOffset(), Math.min(0, offset));
    }

    /**
     * Computes the most negative scroll offset allowed by the current geometry.
     *
     * @return The minimum scroll offset in pixels.
     */
    double minScrollOffset() {

        return Math.min(0, viewportWidth() - contentWidth());
    }

    /**
     * Computes the visible width occupied by range cells.
     *
     * @return The viewport width in pixels.
     */
    int viewportWidth() {

        if (showCaps()) return stripAvailableWidth() - 2 * ELLIPSIS_CAP_WIDTH;

        return Math.min(stripAvailableWidth(), contentWidth());
    }

    /**
     * Computes the total content width in pixels.
     *
     * @return The full width of all range cells.
     */
    int contentWidth() {

        return ranges.size() * itemWidth;
    }

    /**
     * Returns whether overflowing content can reserve gutters for edge ellipses.
     *
     * @return True when the strip should reserve cap space on both sides.
     */
    boolean showCaps() {

        return contentWidth() > stripAvailableWidth()
                && stripAvailableWidth() > 2 * ELLIPSIS_CAP_WIDTH;
    }

    /**
     * Computes the width available to the horizontally scrolling item strip.
     *
     * @return The available strip width in pixels.
     */
    private int stripAvailableWidth() {

        return Math.max(0, width - labelWidth);
    }

    /**
     * Selects an item on click release or finishes an active drag.
     *
     * @param mouseX The mouse X coordinate where the press was released.
     * @param mouseY The mouse Y coordinate where the press was released.
     */
    @Override
    public void onRelease(double mouseX, double mouseY) {

        if (!dragMoved) selectIndex(indexAt(mouseX, mouseY));
        dragging = false;
    }

    /**
     * Handles wheel input over the strip, either selecting ranges with control held or panning horizontally.
     *
     * @param mouseX The mouse X coordinate.
     * @param mouseY The mouse Y coordinate.
     * @param amount The scroll amount.
     * @return True when the scroll event was consumed.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {

        if (!isMouseOver(mouseX, mouseY) || amount == 0) return false;

        if (isControlDown()) return selectRelative(amount > 0 ? 1 : -1);

        scrollOffset = clampScrollOffset(scrollOffset + amount * itemWidth);
        return true;
    }

    /**
     * Reports whether a control modifier is currently held.
     *
     * @return True when control is held.
     */
    boolean isControlDown() {

        return Screen.hasControlDown();
    }

    /**
     * Selects a range relative to the current selection or visible strip start.
     *
     * @param step Relative selection step.
     * @return True when the event was consumed.
     */
    private boolean selectRelative(int step) {

        if (ranges.isEmpty()) return false;

        int baseIndex = selectedIndex >= 0 ? selectedIndex : firstVisibleIndex();
        int targetIndex = Math.max(0, Math.min(ranges.size() - 1, baseIndex + step));
        if (targetIndex == selectedIndex) return true;

        selectIndex(targetIndex);
        return true;
    }

    /**
     * Selects a valid list position and notifies the click callback.
     *
     * @param index The list position to select.
     */
    private void selectIndex(int index) {

        if (index < 0 || index >= ranges.size()) return;

        selectedIndex = index;
        centerOn(index);
        onSelect.accept(ranges.get(index));
    }

    /**
     * Checks whether a mouse coordinate is inside this widget's current bounds.
     *
     * @param mouseX The mouse X coordinate.
     * @param mouseY The mouse Y coordinate.
     * @return True when the widget is interactive and the coordinate is inside its bounds.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {

        return visible
                && active
                && mouseX >= getX()
                && mouseX < getX() + width
                && mouseY >= getY()
                && mouseY < getY() + height;
    }

    /**
     * Appends the default narration entries for this clickable widget.
     *
     * @param builder The narration message builder.
     */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

        defaultButtonNarrationText(builder);
    }

    /**
     * Returns the currently selected range.
     *
     * @return The selected range, or null when no valid selection exists.
     */
    public @Nullable MapLayerRange getSelected() {

        if (selectedIndex < 0 || selectedIndex >= ranges.size()) return null;

        return ranges.get(selectedIndex);
    }

    /**
     * Sets the selected range without firing the selection callback.
     *
     * @param selected The range to select, or null to clear selection.
     */
    public void setSelected(@Nullable MapLayerRange selected) {

        selectedIndex = selected == null ? -1 : ranges.indexOf(selected);
        if (selectedIndex >= 0) centerOn(selectedIndex);
    }

    /**
     * Sets the fixed label drawn before the range cells.
     *
     * @param label The replacement label, or null for no label.
     */
    public void setLabel(@Nullable Component label) {

        this.label = label == null ? Component.empty() : label;
        if (this.label.getString().isEmpty()) {
            labelWidth = 0;
            if (selectedIndex >= 0) centerOn(selectedIndex);
            else scrollOffset = clampScrollOffset(scrollOffset);
            return;
        }

        labelWidth = Client.mc().font.width(this.label) + LABEL_MARGIN * 2;
        if (selectedIndex >= 0) centerOn(selectedIndex);
        else scrollOffset = clampScrollOffset(scrollOffset);
    }

    /**
     * Replaces the displayed ranges and recomputes automatic item width when applicable.
     *
     * @param ranges The replacement range list.
     */
    public void setRanges(List<MapLayerRange> ranges) {

        this.ranges = List.copyOf(ranges);
        if (autoItemWidth) this.itemWidth = computeDefaultItemWidth(ranges);
        this.selectedIndex = -1;
        this.scrollOffset = 0;
        this.dragging = false;
        this.dragMoved = false;
    }

    /**
     * Computes an item width wide enough for the largest visible range label.
     *
     * @param ranges The ranges to measure.
     * @return The default item width in pixels.
     */
    private static int computeDefaultItemWidth(List<MapLayerRange> ranges) {

        Font textRenderer = Client.mc().font;
        int maxWidth = 0;

        for (MapLayerRange range : ranges) {
            maxWidth = Math.max(maxWidth, textRenderer.width(label(range)));
        }

        return Math.max(1, maxWidth + ITEM_HORIZONTAL_MARGIN * 2);
    }

    /**
     * Builds the visible label for a range.
     *
     * @param range The range to label.
     * @return The authored range index as text.
     */
    private static String label(MapLayerRange range) {

        return String.valueOf(range.index());
    }

    /**
     * Builds the tooltip label for a range.
     *
     * @param range The range to label.
     * @return The numeric Y interval covered by the range.
     */
    static String tooltipLabel(MapLayerRange range) {

        return Math.min(range.rangeMinY(), range.rangeMaxY()) + ".." + Math.max(range.rangeMinY(), range.rangeMaxY());
    }

    /**
     * Interpolates two ARGB colours channel-by-channel.
     *
     * @param interpolationFraction The interpolation fraction in the inclusive range [0, 1].
     * @return The interpolated ARGB colour.
     */
    @SuppressWarnings("ConstantValue")
    static int lerpColor(float interpolationFraction) {

        float clamped = Math.max(0, Math.min(1, interpolationFraction));
        int fromA = RangeSelectionWidget.BACKGROUND_TRANSPARENT >>> 24;
        int fromR = (RangeSelectionWidget.BACKGROUND_TRANSPARENT >>> 16) & 0xFF;
        int fromG = (RangeSelectionWidget.BACKGROUND_TRANSPARENT >>> 8) & 0xFF;
        int fromB = RangeSelectionWidget.BACKGROUND_TRANSPARENT & 0xFF;
        int toA = RangeSelectionWidget.BACKGROUND_COLOR >>> 24;
        int toR = (RangeSelectionWidget.BACKGROUND_COLOR >>> 16) & 0xFF;
        int toG = (RangeSelectionWidget.BACKGROUND_COLOR >>> 8) & 0xFF;
        int toB = RangeSelectionWidget.BACKGROUND_COLOR & 0xFF;

        int alpha = Math.round(fromA + (toA - fromA) * clamped);
        int red = Math.round(fromR + (toR - fromR) * clamped);
        int green = Math.round(fromG + (toG - fromG) * clamped);
        int blue = Math.round(fromB + (toB - fromB) * clamped);

        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /**
     * Computes the background gradient colour for one label-area column.
     *
     * @param columnOffset The zero-based column offset inside the label area.
     * @param labelWidth   The total label-area width in pixels.
     * @return The ARGB colour to draw for that column.
     */
    static int gradientColorAt(int columnOffset, int labelWidth) {

        if (labelWidth <= 0) return BACKGROUND_COLOR;
        if (labelWidth == 1) return BACKGROUND_COLOR;

        float t = (float) columnOffset / (labelWidth - 1);
        return lerpColor(t);
    }

    /**
     * Clamps a tooltip anchor into the visible strip viewport.
     *
     * @param anchorX   The proposed tooltip anchor X coordinate.
     * @param minAnchor The minimum visible anchor X coordinate.
     * @param maxAnchor The maximum visible anchor X coordinate.
     * @return The clamped anchor X coordinate.
     */
    static int clampTooltipAnchorX(int anchorX, int minAnchor, int maxAnchor) {

        if (maxAnchor <= minAnchor) return minAnchor;
        return Math.max(minAnchor, Math.min(maxAnchor, anchorX));
    }

    /**
     * Computes the tooltip's top-left X coordinate after centring and screen-edge clamping.
     *
     * @param screenWidth The available screen width.
     * @param anchorX     The tooltip anchor X coordinate.
     * @param tooltipWidth The tooltip width.
     * @return The clamped tooltip X coordinate.
     */
    static int tooltipLeft(int screenWidth, int anchorX, int tooltipWidth) {

        return clampToScreen(anchorX - tooltipWidth / 2, TOOLTIP_SCREEN_MARGIN,
                screenWidth - tooltipWidth - TOOLTIP_SCREEN_MARGIN);
    }

    /**
     * Computes the tooltip's top-left Y coordinate above its anchor.
     *
     * @param anchorY       The tooltip anchor Y coordinate.
     * @param tooltipHeight The tooltip height.
     * @return The tooltip Y coordinate.
     */
    static int tooltipTop(int anchorY, int tooltipHeight) {

        return Math.max(TOOLTIP_SCREEN_MARGIN, anchorY - tooltipHeight);
    }

    /**
     * Clamps a value into the inclusive range used for tooltip layout.
     *
     * @param value The proposed coordinate.
     * @param min   The minimum allowed coordinate.
     * @param max   The maximum allowed coordinate.
     * @return The clamped coordinate.
     */
    static int clampToScreen(int value, int min, int max) {

        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Sets the widget width and restores the selected range's preferred scroll position.
     *
     * @param width The replacement width.
     */
    @Override
    public void setWidth(int width) {

        super.setWidth(width);
        if (selectedIndex >= 0) centerOn(selectedIndex);
        else scrollOffset = clampScrollOffset(scrollOffset);
    }

    /**
     * Tooltip positioner that centres the tooltip above the supplied anchor point.
     */
    private static final class AboveAnchorTooltipPositioner implements ClientTooltipPositioner {

        /** Shared instance reused for all hovered range tooltips. */
        private static final AboveAnchorTooltipPositioner INSTANCE = new AboveAnchorTooltipPositioner();

        /**
         * Computes the tooltip position centred above the supplied anchor.
         *
         * @param screenWidth  The width of the current screen.
         * @param screenHeight The height of the current screen.
         * @param x            The tooltip anchor X coordinate.
         * @param y            The tooltip anchor Y coordinate.
         * @param width        The tooltip width.
         * @param height       The tooltip height.
         * @return The tooltip's top-left screen coordinate.
         */
        @Override
        public @NonNull Vector2ic positionTooltip(int screenWidth, int screenHeight, int x, int y, int width, int height) {

            return new Vector2i(tooltipLeft(screenWidth, x, width), tooltipTop(y, height));
        }
    }
}
