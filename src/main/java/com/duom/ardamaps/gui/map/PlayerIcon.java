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

package com.duom.ardamaps.gui.map;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.Client;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for retrieving and processing the player's skin icon.
 */
public class PlayerIcon {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerIcon.class);
    
    /** The size of the player icon in pixels */
    public static final int ICON_SIZE = 48;

    /** The player's head icon as a BufferedImage */
    private static Identifier playerIcon;

    /**
     * Get the player's head icon as a BufferedImage.
     *
     * @return The player's head icon
     */
    public static Identifier getPlayerIcon() {

        if (playerIcon == null) {
            initialize();
        }

        return playerIcon;
    }

    /**
     * Initialize the player icon by retrieving the player's skin texture
     */
    private static void initialize() {

        AbstractClientPlayerEntity player = Client.player();

        if (player == null) return;

        Identifier skinId = player.getSkinTexture();

        TextureManager textureManager = Client.mc().getTextureManager();
        AbstractTexture texture = textureManager.getTexture(skinId);
        NativeImage skinImage = null;

        if (texture instanceof NativeImageBackedTexture nativeTex) {

            skinImage = nativeTex.getImage();

        } else if (texture instanceof ResourceTexture) {

            LOGGER.info("Loading player skin texture from resource for {}, @'{}'",
                    player.getName().getString(),
                    skinId.getPath()
            );
            skinImage = loadResourceTexture(skinId);

        }

        if (skinImage == null) {

            var defaultTex = DefaultSkinHelper.getTexture();
            LOGGER.info("Loading default skin texture failed for {}, @'{}'",
                    player.getName().getString(),
                    defaultTex.getPath()
            );
            // Should never happen, but be safe
            skinImage = loadResourceTexture(defaultTex);
        }

        playerIcon = getIcon(skinImage);
    }

    /**
     * Load a texture from the resource manager as a NativeImage.
     *
     * @param id The identifier of the texture to load
     * @return The loaded NativeImage, or null if loading failed
     */
    private static @Nullable NativeImage loadResourceTexture(Identifier id) {

        if (Client.mc().getResourceManager().getResource(id).isPresent()) {

            try (InputStream in = Client.mc().getResourceManager()
                    .getResource(id)
                    .get()
                    .getInputStream()) {

                return NativeImage.read(in);

            } catch (IOException e) {
                LOGGER.warn("Unable to load texture resource: {}", id);
            }
        } else {

            LOGGER.warn("Texture resource not found: {}", id);
        }

        return null;
    }

    /**
     * Create a player head icon from the given skin image.
     *
     * @param skin The player's skin image
     * @return The player head icon identifier
     */
    private static Identifier getIcon(NativeImage skin) {

        // Final 48x48 head image
        NativeImage head = new NativeImage(ICON_SIZE, ICON_SIZE, true);

        // Base head (8x8 - 40x40 at (4,4))
        blit(skin, head, 8, 8, 8, 8, 4, 4, 40, 40);

        // Hat layer (8x8 - 48x48 at (0,0))
        blit(skin, head, 40, 8, 8, 8, 0, 0, 48, 48);

        NativeImageBackedTexture texture = new NativeImageBackedTexture(head);
        Identifier id = new Identifier(ArdaMaps.MOD_ID, "player_head_map_marker");
        Client.mc().getTextureManager().registerTexture(id, texture);

        return id;
    }

    /**
     * Blit a region from the source image to the destination image, scaling as necessary.
     *
     * @param src Source image
     * @param dst Destination image
     * @param sx  Source x
     * @param sy  Source y
     * @param sw  Source width
     * @param sh  Source height
     * @param dx  Destination x
     * @param dy  Destination y
     * @param dw  Destination width
     * @param dh  Destination height
     */
    @SuppressWarnings("SameParameterValue")
    private static void blit(
            NativeImage src,
            NativeImage dst,
            int sx, int sy, int sw, int sh,
            int dx, int dy, int dw, int dh
    ) {
        for (int x = 0; x < dw; x++) {
            for (int y = 0; y < dh; y++) {
                int srcX = sx + x * sw / dw;
                int srcY = sy + y * sh / dh;
                int color = src.getColor(srcX, srcY);

                // Respect transparency (important for hat layer)
                if ((color >>> 24) != 0) {
                    dst.setColor(dx + x, dy + y, color);
                }
            }
        }
    }
}