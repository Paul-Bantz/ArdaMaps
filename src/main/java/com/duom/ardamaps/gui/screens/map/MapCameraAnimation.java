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

import com.duom.ardamaps.core.data.Vec2d;
import com.duom.ardamaps.core.data.map.cameras.MapCamera;
import lombok.Getter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.function.LongSupplier;

/**
 * Smooth pan and zoom animation for the map camera.
 */
@Environment(EnvType.CLIENT)
public class MapCameraAnimation {

    /** Total duration of the pan animation in milliseconds. */
    static final long ANIMATION_DURATION_MS = 1000L;

    /** Clock used for deterministic tests. */
    private final LongSupplier clock;

    /**
     * Flag indicating whether the map is currently panning / animating.
     */
    @Getter
    private boolean running = false;

    /** Animation start time in milliseconds. */
    private long animationStartMs;

    /** Starting X coordinate of the map camera when beginning an animation. */
    private double animStartX;

    /** Starting Z coordinate of the map camera when beginning an animation. */
    private double animStartZ;

    /** Target X coordinate of the map camera. */
    private double animTargetX;

    /** Target Z coordinate of the map camera. */
    private double animTargetZ;

    /** Initial zoom level when panning and zooming. */
    private double animStartZoom;

    /** Target zoom level when panning and zooming. */
    private double animTargetZoom;

    /**
     * Creates an animation using the system clock.
     */
    public MapCameraAnimation() {

        this(System::currentTimeMillis);
    }

    /**
     * Creates an animation using the given clock.
     *
     * @param clock Current time supplier in milliseconds
     */
    MapCameraAnimation(LongSupplier clock) {

        this.clock = clock;
    }

    /**
     * Starts an animation to the target world position and zoom.
     */
    public void start(Vec2d target, MapCamera camera, double targetZoom) {

        animTargetX = target.x();
        animTargetZ = target.y();
        animStartX = camera.getWorldX();
        animStartZ = camera.getWorldZ();
        animStartZoom = camera.getZoom();
        animTargetZoom = targetZoom;

        animationStartMs = clock.getAsLong();
        running = true;
    }

    /**
     * Cancels any active animation.
     */
    public void cancel() {

        running = false;
    }

    /**
     * Applies the current animation step to the camera.
     *
     * @return True if the animation remains active after this step.
     */
    public boolean apply(MapCamera camera, int contentLeftX, int contentTopY) {

        if (!running) return false;

        camera.resetZoomAnchor();

        long elapsed = clock.getAsLong() - animationStartMs;
        float time = Math.min(1f, (float) elapsed / ANIMATION_DURATION_MS);

        float ease = (float) (1 - Math.pow(1 - time, 5));

        double currentX = animStartX + (animTargetX - animStartX) * ease;
        double currentZ = animStartZ + (animTargetZ - animStartZ) * ease;
        double currentZoom = animStartZoom + (animTargetZoom - animStartZoom) * ease;

        camera.updateZoom(currentZoom);
        camera.setWorldX(currentX, contentLeftX);
        camera.setWorldZ(currentZ, contentTopY);

        if (time >= 1f) running = false;
        return running;
    }
}
