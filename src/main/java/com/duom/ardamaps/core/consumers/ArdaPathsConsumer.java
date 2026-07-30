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

package com.duom.ardamaps.core.consumers;

import com.duom.ardamaps.core.integration.PathfinderProvider;
import com.duom.ardamaps.core.integration.Pathfinders;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import space.ajcool.ardapaths.api.ArdaPathsApi;
import space.ajcool.ardapaths.api.ArdaPathsApiEntrypoint;

/**
 * Consumer for the Arda Paths API.
 */
public class ArdaPathsConsumer implements ArdaPathsApiEntrypoint, PathfinderProvider {

    /** ArdaPaths API instance, set when the API is ready. */
    private ArdaPathsApi api;

    /**
     * Initializes this consumer when the ArdaPaths API becomes ready on the client.
     *
     * @param ardaPathsApi the ArdaPaths API instance
     */
    @Override
    public void onApiReady(ArdaPathsApi ardaPathsApi) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) return;

        api = ardaPathsApi;
        Pathfinders.register(this);
    }

    /**
     * Selects a pathfinder path and chapter through the ArdaPaths API if available.
     *
     * @param pathId    the path identifier
     * @param chapterId the chapter identifier
     * @param teleport  whether to teleport to the chapter start
     */
    @Override
    public void selectPathAndChapter(String pathId, String chapterId, boolean teleport) {
        if (api == null) return;

        api.selectPathAndChapter(pathId, chapterId, true, teleport);
    }
}
