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

import com.duom.ardamaps.gui.ModConstants;
import net.fabricmc.fabric.api.client.rendering.v1.AtlasRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

/**
 * Facade for retrieving sprites from the map icon atlas.
 */
public class IconSpriteAtlas {

    /** Runtime GPU texture storage location. Never read from disk. */
    public static final Identifier ATLAS_ID = ModConstants.modId("textures/atlas/map_icons.png");

    private static final Identifier ATLAS_DEFINITION = ModConstants.modId("map_icons");

    private IconSpriteAtlas() {
    }

    public static void register() {
        AtlasRegistry.register(new AtlasManager.AtlasConfig(ATLAS_ID, ATLAS_DEFINITION, false));
    }

    public static TextureAtlasSprite retrieveSprite(Identifier id) {
        return Minecraft.getInstance().getAtlasManager().get(new SpriteId(ATLAS_ID, id));
    }
}
