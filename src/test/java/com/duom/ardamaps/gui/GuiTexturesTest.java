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

package com.duom.ardamaps.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for GUI texture helper arithmetic.
 */
class GuiTexturesTest {

    private static final Identifier TEXTURE = ModConstants.modId("textures/gui/test.png");

    /**
     * Verifies that blitRepeating splits texture regions into nearly equal centered chunks.
     */
    @Test
    void blitRepeating_splitsIntoNearlyEqualCenteredChunks() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitRepeating(context, TEXTURE,
                0, 0, 13, 5,
                0, 0, 10, 5,
                256, 256);

        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(1.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(2.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(6), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(6), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

    /**
     * Verifies that blitNineSliced clamps slices to half the destination size.
     */
    @Test
    void blitNineSliced_clampsSlicesToHalfDestinationSize() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitNineSliced(context, TEXTURE,
                0, 0, 20, 20,
                16, 16,
                64, 64,
                0, 0,
                256, 256);

        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

    /**
     * Verifies that blitMirroredH uses descending U coordinates for horizontal mirroring.
     */
    @Test
    void blitMirroredH_usesDescendingUCoordinates() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitMirroredH(context, TEXTURE,
                10, 20, 30, 40,
                64, 128, 16, 32,
                256, 256);

        verify(context).blit(org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(40), org.mockito.ArgumentMatchers.eq(60),
                org.mockito.ArgumentMatchers.eq(0.3125f), org.mockito.ArgumentMatchers.eq(0.25f),
                org.mockito.ArgumentMatchers.eq(0.5f), org.mockito.ArgumentMatchers.eq(0.625f));
    }
}
