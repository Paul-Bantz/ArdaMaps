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

package com.duom.ardamaps.core.integration;

import com.duom.ardamaps.core.executors.WarpExecutor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Facade for optional server-side warp integrations.
 */
@Slf4j(topic = "ardamaps")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Warps {

    /** Whether the optional warp service has already been resolved. */
    private static volatile boolean resolved = false;

    /** The active warp service, or null when HuskHomes is absent or unusable. */
    private static volatile @Nullable WarpService service;

    /**
     * @return true when a usable optional warp service is available
     */
    public static boolean isAvailable() {
        return resolve() != null;
    }

    /**
     * Warps the player through the optional service, falling back when unavailable or invalid.
     *
     * @param server    the server that owns the destination world
     * @param player    the player to move
     * @param warpName  the configured warp name
     * @param onFailure fallback action for missing or invalid warp targets
     */
    public static void warpTo(MinecraftServer server, ServerPlayer player, String warpName, Runnable onFailure) {
        WarpService warpService = resolve();
        if (warpService != null) warpService.warpTo(server, player, warpName, onFailure);
        else onFailure.run();
    }

    private static @Nullable WarpService resolve() {
        if (!resolved) {
            synchronized (Warps.class) {
                if (!resolved) {
                    service = createService();
                    resolved = true;
                }
            }
        }

        return service;
    }

    private static @Nullable WarpService createService() {
        if (!FabricLoader.getInstance().isModLoaded("huskhomes")) return null;

        try {
            return new WarpExecutor();
        } catch (Throwable throwable) {
            log.warn("HuskHomes warp integration is unavailable; falling back to coordinates.", throwable);
            return null;
        }
    }
}
