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

package com.duom.ardamaps.core;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.config.Dimension;
import com.duom.ardamaps.gui.screens.ArdaMapsScreen;
import com.duom.ardamaps.gui.widgets.SearchWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Utility class for accessing client-side Minecraft functionality and mod-specific features.
 * This class provides static methods to interact with the Minecraft client, player, world,
 * and other client-specific data. It also includes methods for managing mod-related directories
 * and resources.
 */
@Environment(EnvType.CLIENT)
public class Client {

    /** Cached current dimension to avoid repeated lookups - can be null if not available */
    private static Dimension cachedCurrentDimension = null;

    /**
     * @return The client's world, or null if not available
     */
    public static @Nullable ClientWorld world() {
        return mc().world;
    }

    /**
     * This method will only return null when the player is not connected to a world or the world is not loaded yet.
     * @return The registry key of the current dimension, or null if world is not available
     */
    public static @Nullable String currentDimensionId() {

        var world = world();

        if (world == null) return null;

        return world.getRegistryKey().getValue().toString();
    }

   /**
    * This method will only return null when the player is not connected to a world or the world is not loaded yet.
     * @return The registry key of the current dimension, or null if world is not available
     */
    public static @Nullable Dimension currentDimension() {

        var world = world();

        if (world == null) return null;

        var dimensionId = world.getRegistryKey().getValue().toString();

        if (cachedCurrentDimension == null || !cachedCurrentDimension.getId().equals(dimensionId))
            cachedCurrentDimension = ArdaMapsClient.CONFIG.getDimension(dimensionId);

        return cachedCurrentDimension;
    }

    /**
     * Invalidates the cached current dimension.
     */
    public static void invalidateCachedDimension() {
        cachedCurrentDimension = null;
    }

    /**
     * Gets the Minecraft client instance. We annotate this with
     * {@link NotNull} because utility methods should only be
     * invoked after the client has been initialized.
     *
     * @return The Minecraft client instance
     */
    public static @NotNull MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    /**
     * @return The player's position as a 2D vector (X, Z), or (0,0) if player is not available
     */
    public static @NotNull Vec2d playerPosition2d() {

        ClientPlayerEntity player = player();
        if (player != null) {
            return new Vec2d(player.getX(), player.getZ());
        }
        return new Vec2d(0, 0);
    }

    /**
     * @return The client's player, or null if not available
     */
    public static @Nullable ClientPlayerEntity player() {
        return mc().player;
    }

    /**
     * @return True if the Arda Maps screen is currently being shown, otherwise false
     */
    public static boolean isShowingMapScreen() {
        return mc().currentScreen != null
                && mc().currentScreen instanceof ArdaMapsScreen
                || mc().currentScreen instanceof SearchWidget;
    }

    /**
     * @return The centre X coordinate of the game window
     */
    public static int getWindowCenterX() {
        return getScaledWindowWidth() / 2;
    }

    /**
     * @return The scaled width of the game window
     */
    public static int getScaledWindowWidth() {
        return mc().getWindow().getScaledWidth();
    }

    /**
     * @return The centre Y coordinate of the game window
     */
    public static int getWindowCenterY() {
        return getScaledWindowHeight() / 2;
    }

    /**
     * @return The scaled height of the game window
     */
    public static int getScaledWindowHeight() {
        return mc().getWindow().getScaledHeight();
    }

    /**
     * @return The cache directory for the mod
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static Path cacheDirectory() {

        var cachePath = runtimeDirectory()
                .resolve("cache");

        if (!cachePath.toFile().exists()) {
            cachePath.toFile().mkdirs();
        }

        return cachePath;
    }

    /**
     * @return The runtime directory for the mod
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static Path runtimeDirectory() {

        var modPath = mc().runDirectory.toPath()
                .resolve(ArdaMaps.MOD_ID);

        if (!modPath.toFile().exists()) {
            modPath.toFile().mkdirs();
        }

        return mc().runDirectory.toPath()
                .resolve(ArdaMaps.MOD_ID);
    }
}