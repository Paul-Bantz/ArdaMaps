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

package com.duom.ardamaps.gui.screens;

import com.duom.ardamaps.core.Client;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration screen variant that returns to the ModMenu parent screen when closed.
 */
@Environment(EnvType.CLIENT)
public class ModMenuConfigurationScreen extends ConfigurationScreen {

    /** Screen to return to when this screen closes, or null to close to the game. */
    private final @Nullable Screen parent;

    /**
     * Constructor for the ModMenu configuration screen.
     *
     * @param parent the parent screen to return to when closing
     */
    public ModMenuConfigurationScreen(@Nullable Screen parent) {

        super(parent);
        this.parent = parent;
    }

    /**
     * Persists configuration and returns to the ModMenu parent screen.
     */
    @Override
    public void onClose() {

        persistConfiguration();
        Client.mc().setScreen(parent);
    }

    /**
     * Hides the map, guide and configuration bookmarks, as they lead nowhere useful from the mods list.
     *
     * @return false, only the exit bookmark is shown
     */
    @Override
    protected boolean showsNavigationBookmarks() {

        return false;
    }

    /**
     * Routes the exit bookmark through onClose, returning to the ModMenu parent screen.
     */
    @Override
    protected void onExitButtonPressed() {

        this.onClose();
    }
}
