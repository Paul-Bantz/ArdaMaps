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

package com.duom.ardamaps.gui.icons;

import com.duom.ardamaps.ArdaMaps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasHolder;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

/**
 * Holds the sprite atlas for map icons.
 * Atlas ID: ardamaps:map_icons
 * Sprite IDs follow the pattern: ardamaps:icons/<name>
 */
public class IconSpriteAtlas extends SpriteAtlasHolder implements IdentifiableResourceReloadListener {

    /** Runtime GPU texture storage location. Never read from disk. */
    public static final Identifier ATLAS_ID = new Identifier(ArdaMaps.MOD_ID, "textures/atlas/map_icons.png");

    /** Singleton instance of the atlas. Initialized at runtime. */
    private static IconSpriteAtlas INSTANCE;

    /** Private constructor to enforce singleton pattern. */
    public IconSpriteAtlas(TextureManager textureManager) {
        super(textureManager, ATLAS_ID, new Identifier(ArdaMaps.MOD_ID, "map_icons"));
    }

    /**
     * Factory method to create or retrieve the singleton instance of the IconSpriteAtlas.
     *
     * @param textureManager the TextureManager to use for loading the atlas.
     * @return the singleton instance of IconSpriteAtlas.
     */
    public static IconSpriteAtlas create(TextureManager textureManager) {
        if (INSTANCE == null) {
            INSTANCE = new IconSpriteAtlas(textureManager);
        }
        return INSTANCE;
    }

    /**
     * Get a sprite by its full identifier (e.g. {@code ardamaps:icons/icon_landmark}).
     *
     * @param id the identifier of the sprite to retrieve.
     * @return the sprite from the atlas.
     */
    public static Sprite retrieveSprite(Identifier id) {
        if (INSTANCE == null) throw new IllegalStateException("IconSpriteAtlas not initialized");
        return INSTANCE.getSprite(id);
    }

    /**
     * @return the unique identifier for this resource reload listener, used for logging and debugging.
     */
    @Override
    public Identifier getFabricId() {
        return new Identifier(ArdaMaps.MOD_ID, "icon_sprite_atlas");
    }
}

