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
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * A utility class that holds constants for the map GUI, such as button dimensions and texture identifiers.
 * This class centralizes all GUI-related constants to ensure consistency across the map interface and to make it easier to manage and update these values in one place.
 */
public class ModConstants {

    /** Date format string for displaying timestamps. */
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** Height of standard buttons in pixels. */
    public static final int BUTTON_HEIGHT = 32;

    /** Width of standard buttons in pixels. */
    public static final int BUTTON_WIDTH = 90;

    /** Size of square buttons in pixels. */
    public static final int SQUARED_BUTTON_SIZE = BUTTON_HEIGHT;

    /** Size of small square buttons in pixels. */
    public static final int SMALL_SQUARED_BUTTON_SIZE = 20;

    /** Vertical spacing between rows in pixels. */
    public static final int ROW_SPACING = 12;

    /** Texture identifier for the fog of war overlay. */
    public static final Identifier FOG_OF_WAR_TEXTURE = modId("textures/gui/fog_of_war_texture.png");

    /** Texture identifier for the paper background using 9-slice scaling. */
    public static final Identifier PAPER_TEXTURE = modId("textures/gui/paper_tex_9slices_512px.png");

    /** Texture identifier for map GUI elements using 9-slice scaling. */
    public static final Identifier MAP_GUI_ELEMENTS = modId("textures/gui/gui_tex_map_elements_9slices_512px.png");

    /** Texture identifier for the book GUI using 9-slice scaling. */
    public static final Identifier GUI_TEXTURE = modId("textures/gui/gui_tex_book_9slices_512px.png");

    /** Texture identifier for the ArdaCraft logo. */
    public static final Identifier ARDACRAFT_LOGO = modId("textures/icons/icon_ardacraft_gradient_128px.png");

    /**
     * Legacy GUI nine-slice calls address 512px sheets through vanilla's old 256-unit texture space.
     * Direct full-sheet blits still use the real texture dimensions.
     */
    public static final int LEGACY_TEXTURE_SPACE = 256;

    /** Icon identifier for the close button. */
    public static final Identifier CLOSE_ICON = modId("icons/icon_close");

    /** Icon identifier for the map icon. */
    public static final Identifier MAP_ICON = modId("icons/icon_map");

    /** Icon identifier for the configuration icon. */
    public static final Identifier CONFIGURATION_ICON = modId("icons/icon_configuration");

    /** Icon identifier for the guide icon. */
    public static final Identifier GUIDE_ICON = modId("icons/icon_guide");

    /** Icon identifier for the landmark icon. */
    public static final Identifier LANDMARK_ICON = modId("icons/icon_landmark");

    /** Icon identifier for the unknown icon (fallback). */
    public static final Identifier UNKNOWN_ICON = modId("icons/icon_unknown");

    /** Texture identifier for checkbox element. */
    public static final Identifier TEXTURE = id("textures/gui/checkbox.png");

    /** Icon identifier for the map marker. */
    public static final Identifier MAP_MARKER_ICON = modId("icons/map_marker");

    /** Icon identifier for the visited map marker. */
    public static final Identifier MAP_MARKER_VISITED_ICON = modId("icons/map_marker_visited");

    /** Icon identifier for waypoint markers. */
    public static final Identifier ICON_WAYPOINT = modId("icons/icon_waypoint");

    /** Icon identifier for the left mouse click icon. */
    public static final Identifier ICON_MOUSE_LEFT_CLICK = modId("icons/icon_mouse_left_click");

    /** Icon identifier for the right mouse click icon. */
    public static final Identifier ICON_MOUSE_RIGHT_CLICK = modId("icons/icon_mouse_right_click");

    /** Icon identifier for the compass north cardinal direction. */
    public static final Identifier ICON_CARDINAL_NORTH = modId("icons/icon_compass_north");

    /** Icon identifier for the compass south cardinal direction. */
    public static final Identifier ICON_CARDINAL_SOUTH = modId("icons/icon_compass_south");

    /** Icon identifier for the compass east cardinal direction. */
    public static final Identifier ICON_CARDINAL_EAST = modId("icons/icon_compass_east");

    /** Icon identifier for the compass west cardinal direction. */
    public static final Identifier ICON_CARDINAL_WEST = modId("icons/icon_compass_west");

    /** Texture identifier for the compass background. */
    public static final Identifier COMPASS_BACKGROUND = modId("textures/gui/compass_texture.png");

    /** Icon identifier for "all" or generic selection. */
    public static final Identifier ICON_ALL = modId("icons/icon_all");

    /** Icon identifier for the book/guide icon. */
    public static final Identifier ICON_BOOK = modId("icons/icon_book");

    /** Icon identifier for keybind display. */
    public static final Identifier ICON_KEYBIND = modId("textures/icons/icon_keybind.png");

    /** ARGB colour constant for white. */
    public static final int COLOR_WHITE = 0xFFFFFFFF;

    /** ARGB colour constant for red. */
    public static final int COLOR_RED = 0xFFFF0000;

    /** ARGB colour constant for brown. */
    public static final int COLOR_BROWN = 0xFFC0AA85;

    /** ARGB colour constant for dark blue. */
    public static final int COLOR_DARKER_BLUE = 0xFF1E2429;

    /** ARGB colour constant for blue. */
    public static final int COLOR_BLUE = 0xFF494E60;

    /** ARGB colour constant for light blue highlight. */
    public static final int COLOR_BLUE_HIGHLIGHT = 0xFF727684;

    /** ARGB colour constant for emphasized blue. */
    public static final int COLOR_BLUE_EMPHASIZED = 0xFF6089DB;

    /** ARGB colour constant for dark brown. */
    public static final int COLOR_DARK_BROWN = 0xFF654429;

    /** ARGB colour constant for light brown. */
    public static final int COLOR_LIGHT_BROWN = 0XFFE6D6BB;

    /** ARGB colour constant for text inside command inline tags. */
    public static final int TEXT_COLOR_COMMAND = 0xFF603E05;

    /** ARGB colour constant for background of command inline tags. */
    public static final int COMMAND_BG_COLOR = 0xFFC0AA85;

    /** Text scale for heading level 1. */
    public static final float H1_TEXT_SCALE = 1.5f;

    /** Text scale for heading level 2. */
    public static final float H2_TEXT_SCALE = 1.25f;

    /** Text scale for heading level 3. */
    public static final float H3_TEXT_SCALE = 1.25f;

    /**
     * Sentinel font identifier written into the Style of every chatcommand glyph by HtmlConverter.
     * TextContentBlockRenderer reads Style.getFont() and compares it against this value to identify
     * chatcommand runs without colour-sniffing.
     */
    public static final FontDescription RUN_FONT_CHATCOMMAND = new FontDescription.Resource(modId("run/chatcommand"));

    /**
     * Sentinel font identifier written into the Style of every keybind glyph by HtmlConverter.
     * TextContentBlockRenderer reads Style.getFont() and compares it against this value to identify
     * keybind runs without colour-sniffing.
     */
    public static final FontDescription RUN_FONT_KEYBIND = new FontDescription.Resource(modId("run/keybind"));

    /**
     * Padding in pixels applied around the background rectangle of both chatcommand and keybind
     * inline elements.
     */
    public static final int COMMAND_PADDING = 1;

    /** ARGB colour constant for key label text drawn inside the key-cap face. */
    public static final int KEYBIND_LABEL_COLOR = 0xFF454545;

    /**
     * Minimum placeholder width in pixels for a keybind run to ensure single-character keys
     * produce a cap wide enough to look like a key.
     */
    public static final int MIN_KEYBIND_SLOT_PX = 10;

    /** Width in pixels of the vertical accent bar drawn to the left of a blockquote element. */
    public static final int BLOCKQUOTE_ACCENT_WIDTH = 2;

    /**
     * Horizontal indent in pixels applied to blockquote text, measured from the left edge of the
     * content area. Includes the accent bar width plus a 4 pixel gap.
     */
    public static final int BLOCKQUOTE_INDENT = 6;

    /**
     * Parses an identifier string into an Identifier, returning UNKNOWN_ICON if the path is null.
     *
     * @param path The identifier path string (e.g., "namespace:path").
     * @return The parsed Identifier, or UNKNOWN_ICON if path is null.
     */
    public static Identifier id(String path) {
        if (path == null) return UNKNOWN_ICON;
        return Identifier.parse(path);
    }

    /**
     * Creates an Identifier from a path using the ArdaMaps mod namespace.
     *
     * @param path The resource path (e.g., "textures/gui/map").
     * @return The created Identifier with ArdaMaps as namespace.
     */
    public static Identifier modId(String path) {
        return id(ArdaMaps.MOD_ID, path);
    }

    /**
     * Creates an Identifier from a namespace and path.
     *
     * @param namespace The namespace (e.g., "minecraft").
     * @param path      The resource path (e.g., "textures/gui/map").
     * @return The created Identifier.
     */
    public static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
