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

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.Client;
import com.duom.ardamaps.core.data.map.providers.PMTilesHttpTileProvider;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/**
 * Facade for the mod's on-disk cache storage.
 */
public final class CacheStorage {

    /** Number of bytes in one mebibyte. */
    private static final double MEBIBYTE = 1024.0 * 1024.0;

    /** Number of bytes in one gibibyte. */
    private static final long GIBIBYTE = 1024L * 1024L * 1024L;

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheStorage.class);

    private CacheStorage() {

    }

    /**
     * Calculates the current cache size on disk.
     *
     * @return The total size of regular files under the cache directory.
     */
    public static long sizeOnDisk() {

        return sizeOnDisk(Client.cacheDirectory());
    }

    /**
     * Clears every known mod disk cache.
     */
    public static void clear() {

        ArdaMapsClient.getHttpImageProvider().clearDiskCache();
        PMTilesHttpTileProvider.clearDiskCaches();
        deleteChildren(Client.cacheDirectory());
    }

    /**
     * Formats a byte count for display in the configuration screen.
     *
     * @param bytes The byte count to format.
     * @return A translated size component.
     */
    public static Component formatSize(long bytes) {

        double value = bytes >= GIBIBYTE ? bytes / (double) GIBIBYTE : bytes / MEBIBYTE;
        String translationKey = bytes >= GIBIBYTE
                ? "ardamaps.client.map.screen.configuration.cache.size.gigabytes"
                : "ardamaps.client.map.screen.configuration.cache.size.megabytes";

        return Component.translatable(translationKey, String.format(Locale.ROOT, "%.1f", value));
    }

    /**
     * Calculates the size of a directory tree.
     *
     * @param directory The directory to walk.
     * @return The total size of regular files under the directory.
     */
    static long sizeOnDisk(Path directory) {

        if (!Files.exists(directory)) return 0;

        try (var paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .mapToLong(CacheStorage::fileSize)
                    .sum();
        } catch (IOException e) {
            LOGGER.warn("[ArdaMaps] Failed to calculate cache size", e);
            return 0;
        }
    }

    /**
     * Gets a regular file size on a best-effort basis.
     *
     * @param path The file path.
     * @return The file size, or zero when unavailable.
     */
    private static long fileSize(Path path) {

        try {
            return Files.size(path);
        } catch (IOException e) {
            LOGGER.debug("[ArdaMaps] Failed to read cache file size {}", path, e);
            return 0;
        }
    }

    /**
     * Deletes direct children of a directory recursively.
     *
     * @param directory The parent directory to clear.
     */
    private static void deleteChildren(Path directory) {

        if (!Files.exists(directory)) return;

        try (var children = Files.list(directory)) {
            children.forEach(CacheStorage::deleteRecursively);
        } catch (IOException e) {
            LOGGER.warn("[ArdaMaps] Failed to list cache directory for clearing", e);
        }
    }

    /**
     * Deletes one cache path recursively on a best-effort basis.
     *
     * @param path The path to delete.
     */
    private static void deleteRecursively(Path path) {

        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(CacheStorage::deletePath);
        } catch (IOException e) {
            LOGGER.warn("[ArdaMaps] Failed to clear cache path {}", path, e);
        }
    }

    /**
     * Deletes one path on a best-effort basis.
     *
     * @param path The path to delete.
     */
    private static void deletePath(Path path) {

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.debug("[ArdaMaps] Failed to delete cache path {}", path, e);
        }
    }
}
