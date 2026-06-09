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

package com.duom.ardamaps.core.data.conversion;

import com.duom.ardamaps.gui.ModConstants;
import lombok.Getter;
import net.minecraft.text.MutableText;

import java.util.List;

/**
 * Represents a single unit of rendered content inside the GuideScreen's content sub-column.
 * Content is modelled as a sealed type so the renderer can switch exhaustively between
 * a block of styled text, an image loaded from the mod's resource pack, or a bullet list.
 */
public sealed interface ContentBlock permits ContentBlock.BlockquoteBlock,
        ContentBlock.ImageBlock,
        ContentBlock.LineBreakBlock,
        ContentBlock.ListBlock,
        ContentBlock.SeparatorBlock,
        ContentBlock.TextBlock,
        ContentBlock.TitleBlock {

    /**
     * A block of Minecraft styled text.
     *
     * @param text the pre-styled, wrappable text to render
     */
    record TextBlock(MutableText text) implements ContentBlock {}

    /**
     * An image loaded from the mod's resource pack that occupies its own horizontal row
     * inside the content sub-column.
     *
     * <p>{@code src} is the path relative to {@code assets/ardamaps/}, e.g.
     * {@code guide/resources/my_image.png}. Images must exist in the resource pack;
     * missing images are skipped with a logged warning.</p>
     *
     * <p>If {@code caption} is non-blank, a small italic light-brown line is rendered
     * centred beneath the image.</p>
     *
     * @param src     resource-pack path relative to {@code assets/ardamaps/}
     * @param width   declared pixel width  (must be &gt; 0)
     * @param height  declared pixel height (must be &gt; 0)
     * @param align   horizontal alignment within the content sub-column
     * @param caption optional caption text displayed below the image (empty = none)
     */
    record ImageBlock(String src, int width, int height, Align align, String caption) implements ContentBlock {

        /**
         * Horizontal alignment of an {@link ImageBlock} within the content sub-column.
         */
        public enum Align {
            LEFT, CENTER, RIGHT;

            /**
             * Parses an HTML {@code align} attribute value.
             * Unrecognised values silently default to {@link #LEFT}.
             *
             * @param value the raw attribute string (may be null or empty)
             * @return the resolved alignment
             */
            public static Align fromString(String value) {
                if (value == null) return LEFT;
                return switch (value.toLowerCase().trim()) {
                    case "right"  -> RIGHT;
                    case "center" -> CENTER;
                    default       -> LEFT;
                };
            }
        }
    }

    /**
     * Block declaration for a title (h1, h2, etc)
     * @param title the title to display
     * @param type the type of title
     */
    record TitleBlock(String title, Type type) implements ContentBlock {

        /**
         * Title scaling - scaling is relative to the font height in use
         */
        @Getter
        public enum Type {

            H1(ModConstants.H1_TEXT_SCALE, ModConstants.COLOR_BLUE),
            H2(ModConstants.H2_TEXT_SCALE, ModConstants.COLOR_DARK_BROWN),
            H3(ModConstants.H3_TEXT_SCALE, ModConstants.COLOR_DARK_BROWN);

            /** The scale at which to display this title */
            private final float scale;

            /** The title's colour */
            private final int color;

            /**
             * Construct a new Type given a scale
             * @param scale the title scale
             */
            Type(float scale, int color) {

                this.scale = scale;
                this.color = color;
            }

            /**
             * Converts a string to its equivalent title type. Unrecognised values default to H3.
             * @param value the value to convert
             * @return the converted value
             */
            public static Type fromString(String value) {

                if (value == null) return H1;
                return switch (value.toLowerCase().trim()) {
                    case "h1" -> H1;
                    case "h2" -> H2;
                    default   -> H3;
                };
            }
        }
    }

    /**
     * A block that renders as a bullet list.
     *
     * <p>Each element of {@code items} corresponds to one {@code <li>} entry.
     * The renderer is responsible for drawing the bullet glyph (a small filled
     * square via {@code context.fill}) and indenting the item text.</p>
     *
     * @param items the pre-styled text for each list item
     */
    record ListBlock(List<MutableText> items) implements ContentBlock {}

    /**
     * A block that renders as an indented blockquote with a left accent bar and
     * {@code ⌈fontHeight/2⌉} px of vertical margin above and below.
     *
     * @param text the pre-styled text content of the quotation
     */
    record BlockquoteBlock(MutableText text) implements ContentBlock {}

    /**
     * A pure vertical spacer block that represents an explicit {@code <br>} line break
     * that occurred when the text buffer was empty (e.g. immediately after an image or
     * list block). The renderer advances {@code totalHeight} by one line for each
     * instance without drawing anything.
     */
    record LineBreakBlock() implements ContentBlock {}

    /**
     * A vertical separator block representing a vertical separation between two content blocks
     */
    record SeparatorBlock() implements ContentBlock {}
}
