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
import lombok.Getter;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry and helper for all ArdaMaps keybindings.
 *
 * <p>Call {@link #register()} once during client initialization, then call
 * {@link #tick()} every client tick so that state is kept up to date.</p>
 *
 * <ul>
 *   <li><b>M</b> – opens the map screen; consume with {@link #consumeMapPress()}.</li>
 *   <li><b>V</b> – toggles the toposcope; query with {@link #isToposcopeEnabled()}.</li>
 * </ul>
 */
public final class KeyBinds {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBinds.class);
    
    /** Translation-key prefix for the key category shown in Controls. */
    private static final String CATEGORY = "key.categories." + ArdaMaps.MOD_ID;

    /** Opens the Arda Maps map screen. */
    public static final KeyBinding OPEN_MAP = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "key." + ArdaMaps.MOD_ID + ".open_map",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_M,
                    CATEGORY
            )
    );

    /** Toggles the toposcope HUD overlay. */
    public static final KeyBinding TOGGLE_TOPOSCOPE = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "key." + ArdaMaps.MOD_ID + ".toggle_toposcope",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_F,
                    CATEGORY
            )
    );

    /** Whether the toposcope is currently enabled. Off by default. */
    @Getter
    private static boolean toposcopeEnabled = false;

    /** Utility class – not instantiable. */
    private KeyBinds() {
    }

    /**
     * Triggers static initialization of this class so that both keybindings are
     * registered with {@link KeyBindingHelper}.  Must be called once from
     * {@code onInitializeClient()}.
     */
    public static void register() {
        // Referencing the class is enough; the static fields handle registration.
        LOGGER.info("ArdaMaps keybindings registered (M = open map, V = toggle toposcope).");
    }

    /**
     * Must be called every client tick (e.g. from
     * {@code ClientTickEvents.END_CLIENT_TICK}).  Polls all keybindings and
     * updates internal toggle state.
     */
    public static void tick() {

        // Consume every queued press of V and flip the toggle once per logical press.
        while (TOGGLE_TOPOSCOPE.wasPressed())
            toposcopeEnabled = !toposcopeEnabled;
    }

    /**
     * Returns {@code true} and consumes one press of the map key if it was
     * pressed since the last call.  Safe to call multiple times per tick – each
     * physical key-press is reported exactly once.
     *
     * @return {@code true} if the map key was pressed.
     */
    public static boolean consumeMapPress() {
        return OPEN_MAP.wasPressed();
    }

}

