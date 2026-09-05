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

package com.duom.ardamaps.core.data.guide;

import com.duom.ardamaps.ArdaMaps;
import com.duom.ardamaps.gui.ModConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Synchronous, session-scoped cache for guide images loaded from the mod resource pack.
 * This cache is simple by design, guide images are expected to be small and few.
 * <p>Images live under {@code assets/ardamaps/guide/resources/} and are referenced
 * in HTML {@code <img>} tags via their path relative to {@code assets/ardamaps/},
 * e.g. {@code guide/resources/my_image.png}.</p>
 *
 * <p>Loading is intentionally synchronous: resource-pack assets are local files and
 * are normally small, so the latency is negligible.  The texture is registered with
 * Minecraft's {@link net.minecraft.client.renderer.texture.TextureManager} on the first call
 * and every subsequent call returns the cached {@link Identifier} instantly.</p>
 *
 * <p>Call {@link #clear()} when client resources reload so stale entries are evicted
 * and images are re-read from the updated pack.</p>
 */
public final class GuideImageCache {

    /** Class logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideImageCache.class);

    /**
     * {@code Optional.empty()} marks a path that was already attempted and failed,
     * preventing repeated filesystem lookups for known-missing resources.
     */
    private static final Map<String, Optional<Identifier>> CACHE = new HashMap<>();

    /** Utility class with no public instances. */
    private GuideImageCache() {
    }

    /**
     * Returns the registered {@link Identifier} for the texture at {@code src},
     * loading and registering it on the first access.
     *
     * <p>Must be called from the render thread because
     * {@link net.minecraft.client.renderer.texture.TextureManager#register} requires
     * an active GL context.</p>
     *
     * @param src path relative to {@code assets/ardamaps/}
     *            (e.g. {@code guide/resources/icon_ardacraft_gradient_128px.png})
     * @return the registered texture identifier, or {@code null} if the resource is
     * missing or could not be loaded (a warning is logged in that case)
     */
    public static Identifier getTexture(String src) {

        if (CACHE.containsKey(src)) {
            return CACHE.get(src).orElse(null);
        }

        Identifier resourceId = ModConstants.modId(src);
        Optional<Resource> resource = Minecraft.getInstance()
                .getResourceManager()
                .getResource(resourceId);

        if (resource.isEmpty()) {
            LOGGER.warn(
                    "[GuideImageCache] Image not found in resource pack: assets/{}/{} – skipping.",
                    ArdaMaps.MOD_ID, src);
            CACHE.put(src, Optional.empty());
            return null;
        }

        try (var is = resource.get().open()) {

            NativeImage img = NativeImage.read(is);
            // Sanitize path characters that are illegal in dynamic texture names
            String sanitised = src.replace('/', '_').replace('.', '_');
            String textureName = "ardamaps_guide_" + sanitised;
            DynamicTexture tex = new DynamicTexture(() -> textureName, img);
            Identifier registered = com.duom.ardamaps.gui.ModConstants.modId(textureName);
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(registered, tex);

            CACHE.put(src, Optional.of(registered));
            return registered;

        } catch (IOException e) {
            LOGGER.warn(
                    "[GuideImageCache] Failed to load image assets/{}/{}: {}",
                    ArdaMaps.MOD_ID, src, e.getMessage());
            CACHE.put(src, Optional.empty());
            return null;
        }
    }

    /**
     * Evicts all cached entries.
     * Should be called by a resource-reload listener so images are re-read from
     * the updated resource pack.
     */
    public static void clear() {
        CACHE.clear();
    }
}
