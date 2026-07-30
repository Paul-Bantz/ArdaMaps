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

package com.duom.ardamaps.core.data.map.providers;

import com.duom.ardamaps.core.data.ImageFileType;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for HTTP image color packing helpers.
 */
class HttpImageProviderTest {

    /**
     * Verifies that Scrimage ARGB pixels are converted to the ABGR packing expected by NativeImage.
     */
    @Test
    void argbToAbgr_swapsRedAndBlueChannels() {

        assertEquals(0xFF332211, HttpImageProvider.argbToAbgr(0xFF112233));
    }

    /**
     * Verifies PNG signature detection does not depend on the URL extension.
     */
    @Test
    void detectImageFileType_pngMagicBytes_winOverExtension() {

        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};

        assertEquals(ImageFileType.PNG, HttpImageProvider.detectImageFileType(bytes, URI.create("https://example.test/map.jpg")));
    }

    /**
     * Verifies JPEG signature detection for extension-less or query-string URLs.
     */
    @Test
    void detectImageFileType_jpegMagicBytes_doNotDefaultToPng() {

        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0};

        assertEquals(ImageFileType.JPEG, HttpImageProvider.detectImageFileType(bytes, URI.create("https://example.test/icon?id=1")));
    }

    /**
     * Verifies WebP RIFF container detection.
     */
    @Test
    void detectImageFileType_webpMagicBytes() {

        byte[] bytes = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertEquals(ImageFileType.WEBP, HttpImageProvider.detectImageFileType(bytes, URI.create("https://example.test/layer.png")));
    }

    /**
     * Verifies inconclusive bytes still use the existing extension fallback.
     */
    @Test
    void detectImageFileType_inconclusiveBytes_useExtensionFallback() {

        byte[] bytes = new byte[]{0, 1, 2};

        assertEquals(ImageFileType.JPEG, HttpImageProvider.detectImageFileType(bytes, URI.create("https://example.test/layer.jpeg")));
    }
}
