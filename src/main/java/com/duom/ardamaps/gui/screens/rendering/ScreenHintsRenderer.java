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

package com.duom.ardamaps.gui.screens.rendering;

import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.icons.IconSpriteAtlas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Renders screen-level input hints anchored to the window edge.
 */
public final class ScreenHintsRenderer {

    /** Distance in pixels from the screen edges. */
    private static final int SCREEN_EDGE_MARGIN = 8;

    /** Opacity applied to the whole hints display. */
    private static final float HINT_OPACITY = .35f;

    /** Tint applied to hint row contents, derived from {@link #HINT_OPACITY}. */
    private static final int HINT_TINT = (Math.round(HINT_OPACITY * 255) << 24) | 0x00FFFFFF;

    /** Rendered mouse icon size in pixels. */
    private static final int MOUSE_ICON_SIZE = 9;

    /** Gap in pixels before a hint label. */
    private static final int LABEL_GAP = 4;

    /** Horizontal padding in pixels inside outlined key-caps. */
    private static final int KEYCAP_PADDING_X = 3;

    /** Scale factor applied to the whole hint row. */
    private static final float HINT_SCALE = .75f;

    /**
     * Private constructor for utility class.
     */
    private ScreenHintsRenderer() {

    }

    /**
     * Renders the provided hint row.
     *
     * @param context      The draw context.
     * @param font         The font renderer.
     * @param screenWidth  The screen width.
     * @param screenHeight The screen height.
     * @param hints        The hints to render.
     */
    public static void render(GuiGraphicsExtractor context, Font font, int screenWidth, int screenHeight, List<Hint> hints) {

        if (hints.isEmpty()) return;

        int rowWidth = 0;
        for (int i = 0; i < hints.size(); i++) {
            if (i > 0) rowWidth += Button.DEFAULT_SPACING;
            rowWidth += hints.get(i).width(font);
        }

        int rowHeight = font.lineHeight + 3;
        int anchorX = screenWidth - SCREEN_EDGE_MARGIN - Math.round(rowWidth * HINT_SCALE);
        int anchorY = screenHeight - SCREEN_EDGE_MARGIN - Math.round(rowHeight * HINT_SCALE);

        context.pose().pushMatrix();
        context.pose().translate(anchorX, anchorY);
        context.pose().scale(HINT_SCALE, HINT_SCALE);

        int x = 0;
        for (Hint hint : hints) {
            x = hint.render(context, font, x, 0, rowHeight);
            x += Button.DEFAULT_SPACING;
        }

        context.pose().popMatrix();
    }

    /**
     * A complete hint made from renderable segments.
     */
    public static final class Hint {

        /** The hint segments. */
        private final List<HintSegment> segments;

        /**
         * Creates a hint from renderable segments.
         *
         * @param segments The hint segments.
         */
        private Hint(List<HintSegment> segments) {

            this.segments = segments;
        }

        /**
         * Builds a search hint.
         *
         * @return A search hint.
         */
        public static Hint search() {

            return new Hint(List.of(
                    new KeycapSegment("CTRL"),
                    new PlusSegment(),
                    new KeycapSegment("F"),
                    new LabelSegment(Component.translatable("ardamaps.client.generic.hint.search"))));
        }

        /**
         * Builds a teleport hint.
         *
         * @return A teleport hint.
         */
        public static Hint teleport() {

            return new Hint(List.of(
                    new KeycapSegment("CTRL"),
                    new PlusSegment(),
                    new SpriteSegment(ModConstants.ICON_MOUSE_LEFT_CLICK),
                    new LabelSegment(Component.translatable("ardamaps.client.generic.teleport"))));
        }

        /**
         * Builds an add-marker hint.
         *
         * @return An add-marker hint.
         */
        public static Hint addMarker() {

            return new Hint(List.of(
                    new KeycapSegment("SHIFT"),
                    new PlusSegment(),
                    new SpriteSegment(ModConstants.ICON_MOUSE_LEFT_CLICK),
                    new LabelSegment(Component.translatable("ardamaps.client.generic.hint.add_marker"))));
        }

        /**
         * Calculates the hint width.
         *
         * @param font The font renderer.
         * @return The hint width.
         */
        private int width(Font font) {

            int width = 0;
            for (HintSegment segment : segments) width += segment.width(font);
            return width;
        }

        /**
         * Renders this hint.
         *
         * @param context   The draw context.
         * @param font      The font renderer.
         * @param x         The starting x coordinate.
         * @param y         The row y coordinate.
         * @param rowHeight The row height.
         * @return The x coordinate after this hint.
         */
        @SuppressWarnings("SameParameterValue")
        private int render(GuiGraphicsExtractor context, Font font, int x, int y, int rowHeight) {

            int currentX = x;
            for (HintSegment segment : segments) {
                currentX = segment.render(context, font, currentX, y + (rowHeight - segment.height(font)) / 2);
            }
            return currentX;
        }
    }

    /**
     * A single measured hint segment.
     */
    private interface HintSegment {

        /**
         * Calculates this segment's width.
         *
         * @param font The font renderer.
         * @return The segment width.
         */
        int width(Font font);

        /**
         * Calculates this segment's height.
         *
         * @param font The font renderer.
         * @return The segment height.
         */
        int height(Font font);

        /**
         * Renders this segment.
         *
         * @param context The draw context.
         * @param font    The font renderer.
         * @param x       The x coordinate.
         * @param y       The y coordinate.
         * @return The x coordinate after this segment.
         */
        int render(GuiGraphicsExtractor context, Font font, int x, int y);
    }

    /**
     * A key-cap hint segment.
     *
     * @param label The key label.
     */
    private record KeycapSegment(String label) implements HintSegment {

        /**
         * Calculates this segment's width.
         *
         * @param font The font renderer.
         * @return The segment width.
         */
        @Override
        public int width(Font font) {

            return Math.max(font.width(label), ModConstants.MIN_KEYBIND_SLOT_PX) + KEYCAP_PADDING_X * 2;
        }

        /**
         * Calculates this segment's height.
         *
         * @param font The font renderer.
         * @return The segment height.
         */
        @Override
        public int height(Font font) {

            return font.lineHeight + 3;
        }

        /**
         * Renders this segment.
         *
         * @param context The draw context.
         * @param font    The font renderer.
         * @param x       The x coordinate.
         * @param y       The y coordinate.
         * @return The x coordinate after this segment.
         */
        @Override
        public int render(GuiGraphicsExtractor context, Font font, int x, int y) {

            int width = width(font);
            int height = height(font);
            context.fill(x, y, x + width, y + 1, HINT_TINT);
            context.fill(x, y + height - 1, x + width, y + height, HINT_TINT);
            context.fill(x, y, x + 1, y + height, HINT_TINT);
            context.fill(x + width - 1, y, x + width, y + height, HINT_TINT);

            int labelX = x + (width - font.width(label)) / 2;
            int labelY = y + (height - font.lineHeight) / 2;
            context.text(font, label, labelX, labelY, HINT_TINT, false);
            return x + width(font);
        }
    }

    /**
     * A plus-sign hint segment.
     */
    private static final class PlusSegment implements HintSegment {

        /** Text drawn for a plus segment. */
        private static final String LABEL = "+";

        /**
         * Calculates this segment's width.
         *
         * @param font The font renderer.
         * @return The segment width.
         */
        @Override
        public int width(Font font) {

            return font.width(LABEL);
        }

        /**
         * Calculates this segment's height.
         *
         * @param font The font renderer.
         * @return The segment height.
         */
        @Override
        public int height(Font font) {

            return font.lineHeight;
        }

        /**
         * Renders this segment.
         *
         * @param context The draw context.
         * @param font    The font renderer.
         * @param x       The x coordinate.
         * @param y       The y coordinate.
         * @return The x coordinate after this segment.
         */
        @Override
        public int render(GuiGraphicsExtractor context, Font font, int x, int y) {

            context.text(font, LABEL, x, y, HINT_TINT, false);
            return x + width(font);
        }
    }

    /**
     * A sprite hint segment.
     *
     * @param icon The sprite icon.
     */
    private record SpriteSegment(Identifier icon) implements HintSegment {

        /**
         * Calculates this segment's width.
         *
         * @param font The font renderer.
         * @return The segment width.
         */
        @Override
        public int width(Font font) {

            return MOUSE_ICON_SIZE;
        }

        /**
         * Calculates this segment's height.
         *
         * @param font The font renderer.
         * @return The segment height.
         */
        @Override
        public int height(Font font) {

            return MOUSE_ICON_SIZE;
        }

        /**
         * Renders this segment.
         *
         * @param context The draw context.
         * @param font    The font renderer.
         * @param x       The x coordinate.
         * @param y       The y coordinate.
         * @return The x coordinate after this segment.
         */
        @Override
        public int render(GuiGraphicsExtractor context, Font font, int x, int y) {

            context.blitSprite(RenderPipelines.GUI_TEXTURED, IconSpriteAtlas.retrieveSprite(icon),
                    x, y, MOUSE_ICON_SIZE, MOUSE_ICON_SIZE, HINT_TINT);
            return x + width(font);
        }
    }

    /**
     * A text label hint segment.
     *
     * @param text The text to draw.
     */
    private record LabelSegment(Component text) implements HintSegment {

        /**
         * Calculates this segment's width.
         *
         * @param font The font renderer.
         * @return The segment width.
         */
        @Override
        public int width(Font font) {

            return LABEL_GAP + font.width(text);
        }

        /**
         * Calculates this segment's height.
         *
         * @param font The font renderer.
         * @return The segment height.
         */
        @Override
        public int height(Font font) {

            return font.lineHeight;
        }

        /**
         * Renders this segment.
         *
         * @param context The draw context.
         * @param font    The font renderer.
         * @param x       The x coordinate.
         * @param y       The y coordinate.
         * @return The x coordinate after this segment.
         */
        @Override
        public int render(GuiGraphicsExtractor context, Font font, int x, int y) {

            context.text(font, text, x + LABEL_GAP, y, HINT_TINT, false);
            return x + width(font);
        }
    }
}
