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

package com.duom.ardamaps.gui;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.gui.screens.rendering.TextContentBlockRenderer;
import net.minecraft.util.Identifier;

/**
 * A utility class that holds constants for the map GUI, such as button dimensions and texture identifiers.
 * This class centralizes all GUI-related constants to ensure consistency across the map interface and to make it easier to manage and update these values in one place.
 */
public class ModConstants {

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** Standard button dimensions used throughout the map GUI for consistent sizing of interactive elements. */
    public static final int BUTTON_HEIGHT = 32;
    public static final int BUTTON_WIDTH = 90;
    public static final int SQUARED_BUTTON_SIZE = BUTTON_HEIGHT;
    public static final int SMALL_SQUARED_BUTTON_SIZE = 20;
    public static final int ROW_SPACING = 12;

    /** Texture identifiers for various GUI elements, such as the fog of war overlay, paper background, and map GUI elements. */
    public static final Identifier FOG_OF_WAR_TEXTURE = new Identifier(ArdaMaps.MOD_ID, "textures/gui/fog_of_war_texture.png");
    public static final Identifier PAPER_TEXTURE = new Identifier(ArdaMaps.MOD_ID, "textures/gui/paper_tex_9slices_512px.png");
    public static final Identifier MAP_GUI_ELEMENTS = new Identifier(ArdaMaps.MOD_ID, "textures/gui/gui_tex_map_elements_9slices_512px.png");
    public static final Identifier GUI_TEXTURE = new Identifier(ArdaMaps.MOD_ID, "textures/gui/gui_tex_book_9slices_512px.png");
    public static final Identifier TEXTURE = new Identifier("textures/gui/checkbox.png");

    public static final Identifier ARDACRAFT_LOGO = new Identifier(ArdaMaps.MOD_ID, "textures/icons/icon_ardacraft_gradient_128px.png");

    /** Texture identifiers for various icons used in the map GUI, such as the close button, map icon, configuration icon, and others. */
    public static final Identifier CLOSE_ICON = new Identifier(ArdaMaps.MOD_ID, "icons/icon_close");
    public static final Identifier MAP_ICON = new Identifier(ArdaMaps.MOD_ID, "icons/icon_map");
    public static final Identifier CONFIGURATION_ICON = new Identifier(ArdaMaps.MOD_ID, "icons/icon_configuration");
    public static final Identifier GUIDE_ICON = new Identifier(ArdaMaps.MOD_ID, "icons/icon_guide");
    public static final Identifier LANDMARK_ICON = new Identifier(ArdaMaps.MOD_ID, "icons/icon_landmark");
    public static final Identifier UNKNOWN_ICON = new Identifier(ArdaMaps.MOD_ID, "icons/icon_unknown");
    public static final Identifier MAP_MARKER_ICON = new Identifier(ArdaMaps.MOD_ID, "icons/map_marker");
    public static final Identifier MAP_MARKER_VISITED_ICON = new Identifier(ArdaMaps.MOD_ID, "icons/map_marker_visited");
    public static final Identifier ICON_WAYPOINT = new Identifier(ArdaMaps.MOD_ID, "icons/icon_waypoint");

    public static final Identifier ICON_MOUSE_LEFT_CLICK = new Identifier(ArdaMaps.MOD_ID, "icons/icon_mouse_left_click");
    public static final Identifier ICON_MOUSE_RIGHT_CLICK = new Identifier(ArdaMaps.MOD_ID, "icons/icon_mouse_right_click");
    public static final Identifier ICON_CARDINAL_NORTH = new Identifier(ArdaMaps.MOD_ID, "icons/icon_compass_north");
    public static final Identifier ICON_CARDINAL_SOUTH = new Identifier(ArdaMaps.MOD_ID, "icons/icon_compass_south");
    public static final Identifier ICON_CARDINAL_EAST = new Identifier(ArdaMaps.MOD_ID, "icons/icon_compass_east");
    public static final Identifier ICON_CARDINAL_WEST = new Identifier(ArdaMaps.MOD_ID, "icons/icon_compass_west");
    public static final Identifier COMPASS_BACKGROUND = new Identifier(ArdaMaps.MOD_ID, "textures/gui/compass_texture.png");
    public static final Identifier ICON_ALL = new Identifier(ArdaMaps.MOD_ID, "icons/icon_all");
    public static final Identifier ICON_BOOK = new Identifier(ArdaMaps.MOD_ID, "icons/icon_book");
    public static final Identifier ICON_KEYBIND = new Identifier(ArdaMaps.MOD_ID, "textures/icons/icon_keybind.png");

    /** Text color constants */
    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_RED = 0xFFFF0000;
    public static final int COLOR_BROWN = 0xFFC0AA85;
    public static final int COLOR_DARKER_BLUE = 0xFF1E2429;
    public static final int COLOR_BLUE = 0xFF494E60;
    public static final int COLOR_BLUE_HIGHLIGHT = 0xFF727684;
    public static final int COLOR_BLUE_EMPHASIZED = 0xFF6089DB;
    public static final int COLOR_DARK_BROWN = 0xFF654429;
    public static final int COLOR_LIGHT_BROWN = 0XFFE6D6BB;

    /** <command> inline tag colours */
    public static final int TEXT_COLOR_COMMAND = 0xFF603E05;
    public static final int COMMAND_BG_COLOR    = 0xFFC0AA85;

    public static final float H1_TEXT_SCALE = 1.5f;
    public static final float H2_TEXT_SCALE = 1.25f;
    public static final float H3_TEXT_SCALE = 1.25f;

    /**
     * Sentinel font {@link Identifier} written into the {@link net.minecraft.text.Style} of every
     * {@code <chatcommand>} glyph by {@code HtmlConverter}.
     * {@link TextContentBlockRenderer} reads {@code Style.getFont()} and compares it against this
     * value to identify chatcommand runs – no colour-sniffing required.
     */
    public static final Identifier RUN_FONT_CHATCOMMAND = new Identifier(ArdaMaps.MOD_ID, "run/chatcommand");

    /**
     * Sentinel font {@link Identifier} written into the {@link net.minecraft.text.Style} of every
     * {@code <keybind>} glyph by {@code HtmlConverter}.
     * {@link TextContentBlockRenderer} reads {@code Style.getFont()} and compares it against this
     * value to identify keybind runs – no colour-sniffing required.
     */
    public static final Identifier RUN_FONT_KEYBIND = new Identifier(ArdaMaps.MOD_ID, "run/keybind");

    /**
     * Padding in pixels applied around the background rectangle of both
     * {@code <chatcommand>} and {@code <keybind>} inline elements.
     */
    public static final int COMMAND_PADDING = 1;

    /** Colour used to draw the key label text centered inside the key-cap face. */
    public static final int KEYBIND_LABEL_COLOR  = 0xFF454545;

    /**
     * Minimum placeholder width in pixels for a keybind run.
     * Ensures single-character keys still produce a cap wide enough to look like a key.
     */
    public static final int MIN_KEYBIND_SLOT_PX = 10;

    /** Width in pixels of the vertical accent bar drawn to the left of a {@code <blockquote>}. */
    public static final int BLOCKQUOTE_ACCENT_WIDTH = 2;

    /**
     * Horizontal indent in pixels applied to blockquote text (measured from the left edge of
     * the content area). Includes the accent bar width plus a 4 px gap.
     */
    public static final int BLOCKQUOTE_INDENT = 6;
}
