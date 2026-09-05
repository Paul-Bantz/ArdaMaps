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

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;

import java.util.function.BiConsumer;

/**
 * A small checkbox that renders vanilla checkbox sprites at a compact, even scale.
 */
public class CompactCheckboxWidget extends AbstractButton {

    /** Pixel size of the rendered checkbox box. */
    public static final int BOX_SIZE = 10;

    /** Callback invoked after the selected state changes. */
    private final BiConsumer<CompactCheckboxWidget, Boolean> onValueChange;

    /** Current selected state. */
    private boolean selected;

    /**
     * Creates a compact checkbox.
     *
     * @param x             The x position.
     * @param y             The y position.
     * @param selected      The initial selected state.
     * @param onValueChange Callback invoked after toggling.
     */
    public CompactCheckboxWidget(int x, int y, boolean selected,
                                 BiConsumer<CompactCheckboxWidget, Boolean> onValueChange) {

        super(x, y, BOX_SIZE, BOX_SIZE, Component.empty());

        this.selected = selected;
        this.onValueChange = onValueChange;
    }

    /**
     * Returns whether this checkbox is selected.
     *
     * @return True when selected.
     */
    public boolean selected() {

        return selected;
    }

    /** {@inheritDoc} */
    @Override
    public void onPress(@NonNull InputWithModifiers input) {

        selected = !selected;
        onValueChange.accept(this, selected);
    }

    /** {@inheritDoc} */
    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

        Identifier sprite;
        if (selected) {
            sprite = Identifier.withDefaultNamespace(isHoveredOrFocused()
                    ? "widget/checkbox_selected_highlighted"
                    : "widget/checkbox_selected");
        } else {
            sprite = Identifier.withDefaultNamespace(isHoveredOrFocused()
                    ? "widget/checkbox_highlighted"
                    : "widget/checkbox");
        }

        context.blitSprite(RenderPipelines.GUI_TEXTURED,
                sprite,
                getX(),
                getY(),
                BOX_SIZE,
                BOX_SIZE,
                ARGB.white(alpha));
    }

    /** {@inheritDoc} */
    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {

        defaultButtonNarrationText(output);
    }
}
