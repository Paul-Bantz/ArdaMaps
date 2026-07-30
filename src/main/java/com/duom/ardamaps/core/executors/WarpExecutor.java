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

package com.duom.ardamaps.core.executors;

import com.duom.ardamaps.core.integration.WarpService;
import com.duom.ardamaps.gui.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.william278.huskhomes.api.FabricHuskHomesAPI;

/**
 * HuskHomes-backed warp executor. This class is only loaded after HuskHomes is known to be present.
 */
public class WarpExecutor implements WarpService {

    @Override
    public void warpTo(MinecraftServer server, ServerPlayer player, String warpName, Runnable onFailure) {
        FabricHuskHomesAPI.getInstance().getWarp(warpName).thenAccept(warpOpt -> {
            if (warpOpt.isEmpty()) {
                onFailure.run();
                return;
            }

            var warp = warpOpt.get();
            Identifier dimensionId = Identifier.tryParse(warp.getWorld().getName());
            if (dimensionId == null) {
                onFailure.run();
                return;
            }

            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);

            ServerLevel serverWorld = server.getLevel(key);
            if (serverWorld == null) {
                onFailure.run();
                return;
            }

            player.teleportTo(serverWorld, warp.getX(), warp.getY(), warp.getZ(), player.getYRot(), player.getXRot());
        });
    }
}
