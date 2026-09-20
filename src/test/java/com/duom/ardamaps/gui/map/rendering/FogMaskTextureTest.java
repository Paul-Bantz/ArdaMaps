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

package com.duom.ardamaps.gui.map.rendering;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link FogMaskTexture} native-buffer lifecycle and upload bounds.
 */
class FogMaskTextureTest {

    /** Mocked RenderSystem static accessors used by texture construction. */
    private MockedStatic<RenderSystem> mockedRenderSystem;

    /** Mocked MemoryUtil static methods used to verify native-buffer ownership. */
    private MockedStatic<MemoryUtil> mockedMemoryUtil;

    /** Mock GPU device used by the texture under test. */
    @SuppressWarnings("FieldCanBeLocal")
    private GpuDevice mockDevice;

    /** Mock command encoder used for texture uploads. */
    private CommandEncoder mockCommandEncoder;

    /** Mock GPU texture created by the device. */
    private GpuTexture mockTexture;

    /** Mock GPU texture view created by the device. */
    @SuppressWarnings("FieldCanBeLocal")
    private GpuTextureView mockTextureView;

    /**
     * Installs render and native-memory mocks needed to construct {@link FogMaskTexture}.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    void setUp() {

        mockDevice = Mockito.mock(GpuDevice.class);
        mockCommandEncoder = Mockito.mock(CommandEncoder.class);
        mockTexture = Mockito.mock(GpuTexture.class);
        mockTextureView = Mockito.mock(GpuTextureView.class);

        GpuSampler mockSampler = Mockito.mock(GpuSampler.class);
        SamplerCache mockSamplerCache = Mockito.mock(SamplerCache.class);
        Mockito.when(mockSamplerCache.getSampler(
                        Mockito.any(AddressMode.class),
                        Mockito.any(AddressMode.class),
                        Mockito.any(FilterMode.class),
                        Mockito.any(FilterMode.class),
                        Mockito.anyBoolean()))
                .thenReturn(mockSampler);
        Mockito.when(mockSamplerCache.getRepeat(Mockito.any(FilterMode.class)))
                .thenReturn(mockSampler);

        Mockito.when(mockDevice.createTexture(
                        Mockito.<Supplier<String>>any(),
                        Mockito.anyInt(),
                        Mockito.any(TextureFormat.class),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt()))
                .thenReturn(mockTexture);
        Mockito.when(mockDevice.createTextureView(mockTexture)).thenReturn(mockTextureView);
        Mockito.when(mockDevice.createCommandEncoder()).thenReturn(mockCommandEncoder);

        mockedRenderSystem = Mockito.mockStatic(RenderSystem.class);
        mockedRenderSystem.when(RenderSystem::getDevice).thenReturn(mockDevice);
        mockedRenderSystem.when(RenderSystem::getSamplerCache).thenReturn(mockSamplerCache);

        mockedMemoryUtil = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS);
        mockedMemoryUtil.when(() -> MemoryUtil.memFree(Mockito.any(ByteBuffer.class)))
                .thenAnswer(_ -> null);
    }

    /**
     * Releases static mocks after each test.
     */
    @AfterEach
    void tearDown() {

        mockedMemoryUtil.close();
        mockedRenderSystem.close();
    }

    /**
     * Construction must upload the zeroed CPU buffer so GPU storage never contains undefined data.
     */
    @Test
    void construction_uploadsFullTextureOnce() {

        FogMaskTexture texture = new FogMaskTexture(() -> "fog-test", 4, 3);

        ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        Mockito.verify(mockCommandEncoder).writeToTexture(
                Mockito.eq(mockTexture),
                bufferCaptor.capture(),
                Mockito.eq(NativeImage.Format.LUMINANCE),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.eq(4),
                Mockito.eq(3));

        ByteBuffer upload = bufferCaptor.getValue();
        assertEquals(12, upload.remaining());
        while (upload.hasRemaining()) {
            assertEquals(0, upload.get());
        }

        texture.close();
    }

    /**
     * Closing twice must not free the same native buffer twice.
     */
    @Test
    void close_calledTwice_doesNotDoubleFree() {

        FogMaskTexture texture = new FogMaskTexture(() -> "fog-test", 4, 3);

        texture.close();
        texture.close();

        mockedMemoryUtil.verify(() -> MemoryUtil.memFree(Mockito.any(ByteBuffer.class)), Mockito.times(1));
    }

    /**
     * Pixel writes after close must not touch freed CPU-side storage or enqueue uploads.
     */
    @Test
    void setPixel_afterClose_isNoOp() {

        FogMaskTexture texture = new FogMaskTexture(() -> "fog-test", 4, 3);
        Mockito.clearInvocations(mockCommandEncoder);

        texture.close();

        texture.setPixel(1, 1, (byte) 0x7f);
        texture.upload();

        Mockito.verifyNoInteractions(mockCommandEncoder);
    }

    /**
     * Upload after close must not write to a closed GPU texture.
     */
    @Test
    void upload_afterClose_isNoOp() {

        FogMaskTexture texture = new FogMaskTexture(() -> "fog-test", 4, 3);
        texture.setPixel(1, 1, (byte) 0x7f);
        Mockito.clearInvocations(mockCommandEncoder);

        texture.close();
        texture.upload();

        Mockito.verifyNoInteractions(mockCommandEncoder);
    }

    /**
     * Uploads after construction must be constrained to the inclusive dirty row span.
     */
    @Test
    void upload_afterConstruction_onlyUploadsDirtyRows() {

        FogMaskTexture texture = new FogMaskTexture(() -> "fog-test", 4, 5);
        Mockito.clearInvocations(mockCommandEncoder);

        texture.setPixel(0, 1, (byte) 0x7f);
        texture.setPixel(3, 3, (byte) 0x7f);

        texture.upload();

        Mockito.verify(mockCommandEncoder).writeToTexture(
                Mockito.eq(mockTexture),
                Mockito.any(ByteBuffer.class),
                Mockito.eq(NativeImage.Format.LUMINANCE),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.eq(1),
                Mockito.eq(4),
                Mockito.eq(3));

        texture.close();
    }

    /**
     * Filling the mask must update every CPU texel and mark the whole texture dirty.
     */
    @Test
    void fill_marksEveryRowDirtyAndSetsEveryTexel() {

        FogMaskTexture texture = new FogMaskTexture(() -> "fog-test", 4, 3);
        Mockito.clearInvocations(mockCommandEncoder);

        texture.fill((byte) 0x7f);
        texture.upload();

        ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        Mockito.verify(mockCommandEncoder).writeToTexture(
                Mockito.eq(mockTexture),
                bufferCaptor.capture(),
                Mockito.eq(NativeImage.Format.LUMINANCE),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.eq(4),
                Mockito.eq(3));

        ByteBuffer upload = bufferCaptor.getValue();
        assertEquals(12, upload.remaining());
        while (upload.hasRemaining()) {
            assertEquals((byte) 0x7f, upload.get());
        }

        texture.close();
    }
}
