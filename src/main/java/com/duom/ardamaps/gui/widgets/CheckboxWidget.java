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
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * A checkbox widget that can be toggled on or off.
 * Displays a text label next to the checkbox.
 */
public class CheckboxWidget extends PressableWidget {

    /** Text label displayed next to the checkbox */
    private final Text text;

    /** Size of the checkbox square (the actual box that is checked/unchecked) */
    private final int size;

    /** Whether the checkbox is currently checked or not */
    private boolean checked;

    /** Whether the checkbox is enabled or not. If false, the checkbox will be rendered in a disabled state and cannot be interacted with. */
    private final boolean enabled;

    /** Callback function that is called when the checkbox state changes. The new state of the checkbox (true for checked, false for unchecked) is passed as an argument to the callback. */
    private final Consumer<Boolean> onChange;

    /**
     * Creates a new CheckboxWidget with the specified parameters.
     *
     * @param x        The x coordinate of the top-left corner of the widget
     * @param y        The y coordinate of the top-left corner of the widget
     * @param width    The total width of the widget (including text and checkbox)
     * @param height   The total height of the widget
     * @param text     The text label to display next to the checkbox
     * @param checked  Whether the checkbox should start in a checked state or not
     * @param enabled  Whether the checkbox should be enabled or disabled
     * @param onChange Callback function to call when the checkbox state changes
     */
    public CheckboxWidget(int x, int y, int width, int height, Text text, boolean checked, boolean enabled, Consumer<Boolean> onChange) {
        super(x, y, width, height, null);
        this.text = text;
        this.checked = checked;
        this.onChange = onChange;
        this.enabled = enabled;
        this.size = Math.min(width, height);
    }

    /**
     * Renders the checkbox widget on the screen. The appearance of the checkbox changes based on its state (checked/unchecked, enabled/disabled) and whether it is hovered by the mouse cursor.
     *
     * @param context The DrawContext to render with
     * @param mouseX  Current x position of the mouse cursor
     * @param mouseY  Current y position of the mouse cursor
     * @param delta   Time delta since last render call
     */
    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int boxX = this.getX() + (width - size);
        int y = this.getY();
        TextRenderer textRenderer = Client.mc().textRenderer;

        if (!enabled) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(0, 0, 2);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.7f);
            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF48494A);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            matrices.pop();

            int textY = y + (height - textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(textRenderer, text, x, textY, 0xFF48494A);

            return;
        }

        if (this.isHovered()) {
            if (checked) {
                context.drawTexture(ModConstants.TEXTURE, boxX, y, size, size, 20, 20, 20, 20, 64, 64);
            } else {
                context.drawTexture(ModConstants.TEXTURE, boxX, y, size, size, 20, 0, 20, 20, 64, 64);
            }
        } else {
            if (checked) {
                context.drawTexture(ModConstants.TEXTURE, boxX, y, size, size, 0, 20, 20, 20, 64, 64);
            } else {
                context.drawTexture(ModConstants.TEXTURE, boxX, y, size, size, 0, 0, 20, 20, 64, 64);
            }
        }

        int textY = y + (height - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(textRenderer, text, x, textY, ModConstants.COLOR_WHITE);
    }

    /**
     * Toggles the checked state of the checkbox when it is pressed and calls the onChange callback with the new state.
     */
    @Override
    public void onPress() {
        checked = !checked;
        if (onChange != null) {
            onChange.accept(checked);
        }
    }

    /**
     * Appends narration messages for screen readers. This method is called when the widget is focused and should provide information about the widget's state (e.g., whether it is checked or not).
     *
     * @param builder The NarrationMessageBuilder to append messages to
     */
    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    /**
     * Sets the checked state of the checkbox and calls the onChange callback with the new state.
     *
     * @param checked the new checked state of the checkbox
     */
    public void setChecked(boolean checked) {
        this.checked = checked;
        if (onChange != null) {
            onChange.accept(checked);
        }
    }
}