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

package com.duom.ardamaps.gui.widgets.builders;

import com.duom.ardamaps.gui.widgets.BookLabelAction;
import com.duom.ardamaps.gui.widgets.BookLabelBehaviour;
import com.duom.ardamaps.gui.widgets.BookLabelButtonType;
import com.duom.ardamaps.gui.widgets.BookLabelButtonWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Builder for book-label button widgets.
 */
public class BookLabelButtonBuilder {

    /** Height of the button widget. */
    private int height;

    /** Text displayed on the button widget. */
    private Component text = Component.empty();

    /** Optional icon displayed in the button cap. */
    @Nullable
    private String icon;

    /** Explicit icon draw width in pixels, or null to fill the mask area. */
    @Nullable
    private Integer iconWidth;

    /** Explicit icon draw height in pixels, or null to fill the mask area. */
    @Nullable
    private Integer iconHeight;

    /** Visual type of the button. */
    private BookLabelButtonType type = BookLabelButtonType.BLUE;

    /** Behaviour run when the button is activated. */
    private BookLabelBehaviour behaviour = new BookLabelAction(() -> {
    });

    /** Whether the button scroll-selects directly through dropdown entries. */
    private boolean directSelection;

    /** Private constructor to enforce the static factory. */
    private BookLabelButtonBuilder() {
    }

    /**
     * Creates a new book-label button builder.
     *
     * @return A new builder instance.
     */
    public static BookLabelButtonBuilder create() {

        return new BookLabelButtonBuilder();
    }

    /**
     * Sets the button height.
     *
     * @param height The button height.
     * @return This builder.
     */
    public BookLabelButtonBuilder setHeight(int height) {

        this.height = height;
        return this;
    }

    /**
     * Sets the visual type.
     *
     * @param type The visual type.
     * @return This builder.
     */
    public BookLabelButtonBuilder setType(BookLabelButtonType type) {

        this.type = type;
        return this;
    }

    /**
     * Sets the label text.
     *
     * @param text The label text.
     * @return This builder.
     */
    public BookLabelButtonBuilder setText(Component text) {

        this.text = text;
        return this;
    }

    /**
     * Sets the icon displayed in the button cap.
     *
     * @param icon The atlas sprite path or absolute image URL.
     * @return This builder.
     */
    public BookLabelButtonBuilder setIcon(@Nullable String icon) {

        this.icon = icon;
        return this;
    }

    /**
     * Sets the icon displayed in the button cap.
     *
     * @param icon The atlas sprite identifier.
     * @return This builder.
     */
    public BookLabelButtonBuilder setIcon(@Nullable Identifier icon) {

        this.icon = icon == null ? null : icon.toString();
        return this;
    }

    /**
     * Sets the icon draw size inside the button cap.
     *
     * @param width  The icon width in screen pixels.
     * @param height The icon height in screen pixels.
     * @return This builder.
     */
    public BookLabelButtonBuilder setIconSize(int width, int height) {

        iconWidth = width;
        iconHeight = height;
        return this;
    }

    /**
     * Sets the square icon draw size inside the button cap.
     *
     * @param size The icon width and height in screen pixels.
     * @return This builder.
     */
    public BookLabelButtonBuilder setIconSize(int size) {

        return setIconSize(size, size);
    }

    /**
     * Sets a simple click action.
     *
     * @param onClick The action to run when clicked.
     * @return This builder.
     */
    public BookLabelButtonBuilder setOnClick(Runnable onClick) {

        this.behaviour = new BookLabelAction(onClick);
        return this;
    }

    /**
     * Sets the full label behaviour.
     *
     * @param behaviour The label behaviour.
     * @return This builder.
     */
    public BookLabelButtonBuilder setBehaviour(BookLabelBehaviour behaviour) {

        this.behaviour = behaviour;
        return this;
    }

    /**
     * Sets whether the button scroll-selects directly through dropdown entries.
     *
     * @param directSelection True to enable direct selection.
     * @return This builder.
     */
    public BookLabelButtonBuilder setDirectSelection(boolean directSelection) {

        this.directSelection = directSelection;
        return this;
    }

    /**
     * Builds the configured widget at the origin.
     *
     * @return A new book-label button widget.
     */
    public BookLabelButtonWidget build() {

        return new BookLabelButtonWidget(0, 0, height, type, behaviour, text, icon, iconWidth, iconHeight,
                directSelection);
    }
}
