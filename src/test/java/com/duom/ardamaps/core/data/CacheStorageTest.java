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

package com.duom.ardamaps.core.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for cache storage helpers.
 */
class CacheStorageTest {

    /** Temporary directory for disk-size tests. */
    @TempDir
    private Path tempDir;

    /**
     * Verifies size walking sums regular files recursively.
     *
     * @throws IOException If test fixture files cannot be written.
     */
    @Test
    void sizeOnDisk_directoryTree_sumsRegularFiles() throws IOException {

        Files.write(tempDir.resolve("one.bin"), new byte[7]);
        Path child = Files.createDirectory(tempDir.resolve("child"));
        Files.write(child.resolve("two.bin"), new byte[11]);

        assertEquals(18, CacheStorage.sizeOnDisk(tempDir));
    }

    /**
     * Verifies absent directories report zero bytes.
     */
    @Test
    void sizeOnDisk_missingDirectory_returnsZero() {

        assertEquals(0, CacheStorage.sizeOnDisk(tempDir.resolve("missing")));
    }

    /**
     * Verifies zero bytes are formatted in megabytes.
     */
    @Test
    void formatSize_zero_usesMegabytes() {

        assertTranslation(CacheStorage.formatSize(0),
                "ardamaps.client.map.screen.configuration.cache.size.megabytes",
                "0.0");
    }

    /**
     * Verifies values below one GiB are formatted in megabytes.
     */
    @Test
    void formatSize_justUnderGibibyte_usesMegabytes() {

        assertTranslation(CacheStorage.formatSize((1024L * 1024L * 1024L) - 1),
                "ardamaps.client.map.screen.configuration.cache.size.megabytes",
                "1024.0");
    }

    /**
     * Verifies values at one GiB are formatted in gigabytes.
     */
    @Test
    void formatSize_oneGibibyte_usesGigabytes() {

        assertTranslation(CacheStorage.formatSize(1024L * 1024L * 1024L),
                "ardamaps.client.map.screen.configuration.cache.size.gigabytes",
                "1.0");
    }

    /**
     * Asserts a translatable component key and arguments.
     *
     * @param component The component to inspect.
     * @param key       The expected translation key.
     * @param args      The expected translation arguments.
     */
    private static void assertTranslation(Component component, String key, Object... args) {

        TranslatableContents contents = (TranslatableContents) component.getContents();
        assertEquals(key, contents.getKey());
        assertArrayEquals(args, contents.getArgs());
    }
}
