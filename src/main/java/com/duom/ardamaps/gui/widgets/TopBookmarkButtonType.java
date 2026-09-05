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

import com.duom.ardamaps.gui.ModConstants;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Visual style and action metadata for top bookmark buttons.
 */
public enum TopBookmarkButtonType {

    /** Close button bookmark type. */
    BOOKMARK_CLOSE(ModConstants.TOP_BOOKMARK_BUTTON_RED_TEXTURE,
            ModConstants.TOP_BOOKMARK_BUTTON_RED_HIGHLIGHT_TEXTURE,
            ModConstants.CLOSE_ICON,
            "ardamaps.client.map.screen.generic.close"),

    /** Configuration button bookmark type. */
    BOOKMARK_CONFIGURATION(ModConstants.TOP_BOOKMARK_BUTTON_BLUE_TEXTURE,
            ModConstants.TOP_BOOKMARK_BUTTON_BLUE_HIGHLIGHT_TEXTURE,
            ModConstants.CONFIGURATION_ICON,
            "ardamaps.client.map.screen.configuration"),

    /** Guide button bookmark type. */
    BOOKMARK_GUIDE(ModConstants.TOP_BOOKMARK_BUTTON_VIOLET_TEXTURE,
            ModConstants.TOP_BOOKMARK_BUTTON_VIOLET_HIGHLIGHT_TEXTURE,
            ModConstants.GUIDE_ICON,
            "ardamaps.client.map.screen.guide"),

    /** Map button bookmark type. */
    BOOKMARK_MAP(ModConstants.TOP_BOOKMARK_BUTTON_GREEN_TEXTURE,
            ModConstants.TOP_BOOKMARK_BUTTON_GREEN_HIGHLIGHT_TEXTURE,
            ModConstants.MAP_ICON,
            "ardamaps.client.map.screen.map");

    /** Texture used when the button is idle. */
    @Getter
    private final Identifier texture;

    /** Texture used when the button is hovered or focused. */
    @Getter
    private final Identifier highlightTexture;

    /** Icon sprite drawn in the top cap. */
    @Getter
    private final Identifier icon;

    /** Translation key for this bookmark button type. */
    private final String translationKey;

    /**
     * Creates a new top bookmark button type.
     *
     * @param texture          Texture used when idle.
     * @param highlightTexture Texture used when hovered or focused.
     * @param icon             Icon sprite drawn in the cap.
     * @param translationKey   Translation key for narration.
     */
    TopBookmarkButtonType(Identifier texture, Identifier highlightTexture, Identifier icon, String translationKey) {

        this.texture = texture;
        this.highlightTexture = highlightTexture;
        this.icon = icon;
        this.translationKey = translationKey;
    }

    /**
     * Gets the translatable component for this bookmark button type.
     *
     * @return The translated component for this button type.
     */
    public Component getTranslation() {

        return Component.translatable(translationKey);
    }
}
