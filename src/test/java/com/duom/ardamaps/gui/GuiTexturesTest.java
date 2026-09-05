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
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for GUI texture helper arithmetic.
 */
class GuiTexturesTest {

    /** Texture identifier used by GUI texture draw tests. */
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
     * Verifies that blitRepeatingScaled repeats full tiles and left-crops the trailing tile.
     */
    @Test
    void blitRepeatingScaled_drawsFullTilesAndLeftCroppedTrailingTile() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitRepeatingScaled(context, TEXTURE,
                5, 7, 26, 12,
                40, 50, 10, 30,
                8,
                256, 256, ModConstants.COLOR_WHITE);

        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.eq(40.0f), org.mockito.ArgumentMatchers.eq(50.0f),
                org.mockito.ArgumentMatchers.eq(8), org.mockito.ArgumentMatchers.eq(12),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(30),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(13), org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.eq(40.0f), org.mockito.ArgumentMatchers.eq(50.0f),
                org.mockito.ArgumentMatchers.eq(8), org.mockito.ArgumentMatchers.eq(12),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(30),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(21), org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.eq(40.0f), org.mockito.ArgumentMatchers.eq(50.0f),
                org.mockito.ArgumentMatchers.eq(8), org.mockito.ArgumentMatchers.eq(12),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(30),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(29), org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.eq(40.0f), org.mockito.ArgumentMatchers.eq(50.0f),
                org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(12),
                org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(30),
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
     * Verifies that the scroll background draws contiguous full-height slices at label height.
     */
    @Test
    void blitScrollBackground_drawsThreeContiguousFullHeightSlices() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitScrollBackground(context, 10, 20, 120, 33);

        verify(context, times(3)).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SCROLL_BUTTON_TEXTURE),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(96), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SCROLL_BUTTON_TEXTURE),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(0.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(19), org.mockito.ArgumentMatchers.eq(33),
                org.mockito.ArgumentMatchers.eq(27), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(96), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SCROLL_BUTTON_TEXTURE),
                org.mockito.ArgumentMatchers.eq(111), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(69.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(19), org.mockito.ArgumentMatchers.eq(33),
                org.mockito.ArgumentMatchers.eq(27), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(96), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SCROLL_BUTTON_TEXTURE),
                org.mockito.ArgumentMatchers.eq(29), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(27.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(82), org.mockito.ArgumentMatchers.eq(33),
                org.mockito.ArgumentMatchers.eq(42), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(96), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

    /**
     * Verifies that narrow scroll backgrounds collapse to two side slices without a center slice.
     */
    @Test
    void blitScrollBackground_omitsCenterWhenSidesFillWidth() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitScrollBackground(context, 10, 20, 20, 33);

        verify(context, times(2)).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SCROLL_BUTTON_TEXTURE),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(96), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SCROLL_BUTTON_TEXTURE),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(0.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(33),
                org.mockito.ArgumentMatchers.eq(27), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(96), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SCROLL_BUTTON_TEXTURE),
                org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(69.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(33),
                org.mockito.ArgumentMatchers.eq(27), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(96), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

    /**
     * Verifies that wide separators draw every mirrored ornament slice including flourishes.
     */
    @Test
    void blitSeparator_drawsFlourishesAboveThreshold() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);
        ArgumentCaptor<Float> uCaptor = ArgumentCaptor.forClass(Float.class);

        GuiTextures.blitSeparator(context, 10, 20, 300, 20, ModConstants.COLOR_WHITE);

        verify(context, times(11)).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SEPARATOR_TEXTURE),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                uCaptor.capture(), org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(512), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        assertEquals(List.of(0.0f, 42.0f, 64.0f, 92.0f, 114.0f, 148.0f,
                364.0f, 398.0f, 420.0f, 448.0f, 470.0f), uCaptor.getAllValues());
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SEPARATOR_TEXTURE),
                org.mockito.ArgumentMatchers.eq(42), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(64.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(36), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(28), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(512), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SEPARATOR_TEXTURE),
                org.mockito.ArgumentMatchers.eq(242), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(420.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(36), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(28), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(512), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

    /**
     * Verifies that narrow separators omit optional flourish slices while keeping mirrored grow bands.
     */
    @Test
    void blitSeparator_omitsFlourishesBelowThreshold() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);
        ArgumentCaptor<Float> uCaptor = ArgumentCaptor.forClass(Float.class);

        GuiTextures.blitSeparator(context, 10, 20, 220, 20, ModConstants.COLOR_WHITE);

        verify(context, times(9)).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SEPARATOR_TEXTURE),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                uCaptor.capture(), org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(512), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        assertEquals(List.of(0.0f, 42.0f, 64.0f, 92.0f, 148.0f,
                398.0f, 420.0f, 448.0f, 470.0f), uCaptor.getAllValues());
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SEPARATOR_TEXTURE),
                org.mockito.ArgumentMatchers.eq(42), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(64.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(13), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(28), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(512), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class),
                org.mockito.ArgumentMatchers.eq(ModConstants.SEPARATOR_TEXTURE),
                org.mockito.ArgumentMatchers.eq(185), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(420.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(13), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(28), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(512), org.mockito.ArgumentMatchers.eq(40),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

    /**
     * Verifies that clamped nine-slice source spans still draw a middle row for short destinations.
     */
    @Test
    void blitNineSlicedStretched_drawsMiddleRowWhenHeightIsLessThanVerticalSlices() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitNineSlicedStretched(context, TEXTURE,
                0, 0, 60, 33,
                28, 28, 28, 28,
                96, 48,
                0, 0,
                96, 48,
                ModConstants.COLOR_WHITE);

        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(28), org.mockito.ArgumentMatchers.eq(16),
                org.mockito.ArgumentMatchers.eq(28.0f), org.mockito.ArgumentMatchers.eq(16.0f),
                org.mockito.ArgumentMatchers.eq(4), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(40), org.mockito.ArgumentMatchers.eq(16),
                org.mockito.ArgumentMatchers.eq(96), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

    /**
     * Verifies that blitNineSlicedScaled draws nine stretched regions with scaled destination corners.
     */
    @Test
    void blitNineSlicedScaled_drawsNineScaledRegions() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitNineSlicedScaled(context, TEXTURE,
                10, 20, 100, 80,
                64, 16,
                256, 256,
                0, 0,
                256, 256);

        verify(context, times(9)).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(0.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(16), org.mockito.ArgumentMatchers.eq(16),
                org.mockito.ArgumentMatchers.eq(64), org.mockito.ArgumentMatchers.eq(64),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(26), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(64.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(68), org.mockito.ArgumentMatchers.eq(16),
                org.mockito.ArgumentMatchers.eq(128), org.mockito.ArgumentMatchers.eq(64),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(26), org.mockito.ArgumentMatchers.eq(36),
                org.mockito.ArgumentMatchers.eq(64.0f), org.mockito.ArgumentMatchers.eq(64.0f),
                org.mockito.ArgumentMatchers.eq(68), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(128), org.mockito.ArgumentMatchers.eq(128),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

    /**
     * Verifies that blitNineSlicedScaled clamps destination corners but keeps source corner regions unchanged.
     */
    @Test
    void blitNineSlicedScaled_clampsDestinationCornerOnly() {

        GuiGraphicsExtractor context = mock(GuiGraphicsExtractor.class);

        GuiTextures.blitNineSlicedScaled(context, TEXTURE,
                0, 0, 20, 20,
                64, 16,
                256, 256,
                0, 0,
                256, 256);

        verify(context).blit(any(RenderPipeline.class), org.mockito.ArgumentMatchers.eq(TEXTURE),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0.0f), org.mockito.ArgumentMatchers.eq(0.0f),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(64), org.mockito.ArgumentMatchers.eq(64),
                org.mockito.ArgumentMatchers.eq(256), org.mockito.ArgumentMatchers.eq(256),
                org.mockito.ArgumentMatchers.eq(ModConstants.COLOR_WHITE));
    }

}
