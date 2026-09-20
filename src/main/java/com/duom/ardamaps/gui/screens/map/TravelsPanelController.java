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
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.widgets.TravelsSheetButton;
import com.duom.ardamaps.gui.widgets.popup.TravelsPopupContent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Controls the map screen's movement-tracking sheet button and travels popup.
 */
@Environment(EnvType.CLIENT)
public class TravelsPanelController {

    /** Supplies the current outer book texture area. */
    private final Supplier<BackgroundRenderer.GuiLayout> bookAreaSupplier;

    /** Sheet button used to open the travels popup. */
    private final TravelsSheetButton sheetButton;

    /** Currently open travels popup, or null when closed. */
    private @Nullable TravelsPopupContent popup;

    /**
     * Creates a new controller.
     *
     * @param bookAreaSupplier Supplies the current outer book texture area.
     */
    public TravelsPanelController(Supplier<BackgroundRenderer.GuiLayout> bookAreaSupplier) {

        this.bookAreaSupplier = bookAreaSupplier;
        this.sheetButton = new TravelsSheetButton(bookAreaSupplier);
    }

    /**
     * Returns {@code true} when the travels popup is currently open.
     *
     * @return Whether the popup is visible.
     */
    public boolean isOpen() {

        return popup != null && movementTrackingEnabled();
    }

    /**
     * Recalculates sheet geometry.
     */
    public void layout() {

        if (!movementTrackingEnabled()) {
            close();
            return;
        }

        sheetButton.layout();
    }

    /**
     * Renders the sheet and popup when movement tracking is enabled.
     *
     * @param context The GUI draw context.
     * @param mouseX  Mouse x position.
     * @param mouseY  Mouse y position.
     * @param delta   The frame delta.
     */
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        if (!movementTrackingEnabled()) {
            close();
            return;
        }

        sheetButton.render(context, mouseX, mouseY, delta);
        if (popup != null) popup.render(context, 0, 0, mouseX, mouseY);
    }

    /**
     * Handles mouse clicks against the sheet and open popup.
     *
     * @param mouseX Mouse x position.
     * @param mouseY Mouse y position.
     * @param button The clicked button index.
     * @return Whether the event was consumed.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (!movementTrackingEnabled()) {
            close();
            return false;
        }

        if (popup != null && popup.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && sheetButton.isMouseOver(mouseX, mouseY)) {
            toggle();
            return true;
        }

        return false;
    }

    /**
     * Returns whether the mouse is over the sheet or popup.
     *
     * @param mouseX Mouse x position.
     * @param mouseY Mouse y position.
     * @return Whether the pointer is over this controller's UI.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {

        if (!movementTrackingEnabled()) {
            close();
            return false;
        }

        return sheetButton.isMouseOver(mouseX, mouseY)
                || (popup != null && popup.isMouseOver(mouseX, mouseY));
    }

    /**
     * Closes the popup.
     */
    public void close() {

        popup = null;
    }

    /**
     * Toggles popup visibility.
     */
    private void toggle() {

        if (popup != null) {
            close();
            return;
        }

        popup = new TravelsPopupContent(bookAreaSupplier);
        popup.setOnClose(this::close);
    }

    /**
     * Checks whether movement tracking UI should be available.
     *
     * @return True when movement tracking is enabled.
     */
    private static boolean movementTrackingEnabled() {

        return ArdaMapsClient.CONFIG != null && ArdaMapsClient.CONFIG.isTrackMovement();
    }
}
