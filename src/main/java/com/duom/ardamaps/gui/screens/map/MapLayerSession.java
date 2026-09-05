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

package com.duom.ardamaps.gui.screens.map;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.core.data.PlayerExploration;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import com.duom.ardamaps.gui.map.rendering.MapRenderable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Manages the async lifecycle of the currently loaded map layer: triggering loads, discarding
 * stale results via a monotonic generation token, and closing the old renderer when a new one
 * arrives. Owns no GUI widgets — it is a pure state machine for the map renderable.
 */
@Environment(EnvType.CLIENT)
public class MapLayerSession {

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(MapLayerSession.class);

    /** The map-layer loader used for constructing new renderables. */
    private final MapLayerLoader mapLayerLoader = new MapLayerLoader();

    /** Monotonic token used to discard stale asynchronous layer-load results. */
    private int layerLoadGeneration;

    /** Whether this session has been closed and must reject all future completions. */
    private boolean removed;

    /** The currently active map renderable, or {@code null} when no layer has been loaded yet. */
    @Nullable
    private MapRenderable mapRenderable;

    /**
     * Returns the current generation token.
     * The owning screen may snapshot this at {@code init()} time to detect whether a
     * configure-button callback already triggered a new load while widgets were being rebuilt.
     *
     * @return the current generation counter.
     */
    public int generation() {

        return layerLoadGeneration;
    }

    /**
     * Marks the session as active (not removed). Call at the start of each {@code init()}.
     */
    public void activate() {

        removed = false;
    }

    /**
     * Returns the camera of the active renderable, or {@code null} when no map is loaded.
     *
     * @return the active {@link MapCamera}, or {@code null}.
     */
    @Nullable
    public MapCamera getCamera() {

        return mapRenderable != null ? mapRenderable.getCamera() : null;
    }

    /**
     * Returns the active renderable, or {@code null} when no map is loaded.
     *
     * @return the active {@link MapRenderable}, or {@code null}.
     */
    @Nullable
    public MapRenderable getRenderable() {

        return mapRenderable;
    }

    /**
     * Returns the exploration state of the active renderable, or {@code null} when no map is loaded.
     *
     * @return the active {@link PlayerExploration}, or {@code null}.
     */
    @Nullable
    public PlayerExploration getExploration() {

        return mapRenderable != null ? mapRenderable.getExploration() : null;
    }

    /**
     * Returns the visual pixels-per-block of the active camera so it can be preserved across layer switches,
     * or {@link Double#NaN} when no map is loaded yet.
     *
     * @return the pixels-per-block value, or {@code NaN}.
     */
    public double capturedRenderScale() {

        return mapRenderable != null ? mapRenderable.getCamera().getVisualPixelsPerBlock() : Double.NaN;
    }

    /**
     * Triggers an asynchronous layer load. The result is applied on the Minecraft main thread only
     * when the load generation still matches (i.e. no newer load was started in the meantime).
     *
     * @param input    The immutable inputs for the layer loader.
     * @param onLoaded Runnable invoked on the main thread after a successful swap (use to update button visibility, etc.).
     */
    public void reload(MapLayerLoader.Input input, Runnable onLoaded) {

        final int generation = ++layerLoadGeneration;

        CompletableFuture.supplyAsync(() -> mapLayerLoader.load(input), ArdaMaps.IO_EXECUTOR)
                .whenComplete((result, ex) -> {

                    if (ex != null) {

                        LOGGER.error("Failed to load map layer", ex);
                        return;
                    }

                    Minecraft.getInstance().execute(() -> applyResult(generation, result, onLoaded));
                });
    }

    /**
     * Applies a completed load result on the main thread, discarding stale results.
     *
     * @param generation The generation token this result was launched with.
     * @param result     The loaded renderable, or {@code null} when loading failed.
     * @param onLoaded   Callback to invoke after a successful swap.
     */
    private void applyResult(int generation, @Nullable MapRenderable result, Runnable onLoaded) {

        LOGGER.info("Map layer loaded: {}", result != null ? "success" : "failed");

        if (removed || generation != layerLoadGeneration) {

            if (result != null) result.close();
            return;
        }

        if (result != null) {

            result.getExploration().initializeTexture();
            closeRenderable();
            mapRenderable = result;
            onLoaded.run();
        }
    }

    /**
     * Closes the active renderable if present and sets it to {@code null}.
     */
    private void closeRenderable() {

        if (mapRenderable != null) {

            mapRenderable.close();
            mapRenderable = null;
        }
    }

    /**
     * Closes this session: marks it removed, bumps the generation to discard any in-flight load,
     * and closes the active renderable.
     */
    public void close() {

        removed = true;
        layerLoadGeneration++;
        closeRenderable();
    }
}
